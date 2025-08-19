"""
Face tracking component for maintaining face identities across frames.

Tracks detected faces across video frames to maintain temporal consistency
and reduce duplicate recognitions.
"""

import cv2
import numpy as np
from typing import List, Optional, Dict, Tuple
import uuid
import time
import asyncio
import structlog
from dataclasses import dataclass, field

from .face_detector import FaceDetection

logger = structlog.get_logger(__name__)


@dataclass
class FaceTrack:
    """Represents a tracked face across multiple frames."""
    track_id: str = field(default_factory=lambda: str(uuid.uuid4()))
    detections: List[FaceDetection] = field(default_factory=list)
    last_seen: float = field(default_factory=time.time)
    first_seen: float = field(default_factory=time.time)
    confidence_history: List[float] = field(default_factory=list)
    position_history: List[Tuple[int, int]] = field(default_factory=list)  # (center_x, center_y)
    is_active: bool = True
    recognition_attempts: int = 0
    last_recognition_time: float = 0.0
    
    @property
    def duration(self) -> float:
        """Duration the face has been tracked (seconds)."""
        return self.last_seen - self.first_seen
    
    @property
    def center(self) -> Tuple[int, int]:
        """Current center position of the tracked face."""
        if not self.detections:
            return (0, 0)
        
        last_detection = self.detections[-1]
        x, y, w, h = last_detection.bbox
        return (x + w // 2, y + h // 2)
    
    @property
    def avg_confidence(self) -> float:
        """Average confidence across all detections."""
        if not self.confidence_history:
            return 0.0
        return sum(self.confidence_history) / len(self.confidence_history)
    
    @property
    def is_stable(self) -> bool:
        """Check if the track is stable (tracked for sufficient time)."""
        return self.duration >= 0.5 and len(self.detections) >= 3
    
    def should_recognize(self, min_interval: float = 5.0) -> bool:
        """Check if this track should be sent for recognition."""
        current_time = time.time()
        return (
            self.is_stable and 
            self.is_active and
            (current_time - self.last_recognition_time) >= min_interval
        )
    
    def add_detection(self, detection: FaceDetection):
        """Add a new detection to this track."""
        self.detections.append(detection)
        self.confidence_history.append(detection.confidence)
        self.position_history.append(self.center)
        self.last_seen = time.time()
        
        # Keep history limited
        max_history = 50
        if len(self.detections) > max_history:
            self.detections = self.detections[-max_history:]
            self.confidence_history = self.confidence_history[-max_history:]
            self.position_history = self.position_history[-max_history:]
    
    def mark_recognized(self):
        """Mark that this track was sent for recognition."""
        self.recognition_attempts += 1
        self.last_recognition_time = time.time()
    
    def get_best_detection(self) -> Optional[FaceDetection]:
        """Get the detection with highest confidence."""
        if not self.detections:
            return None
        return max(self.detections, key=lambda d: d.confidence)
    
    def to_dict(self) -> dict:
        """Convert track to dictionary representation."""
        return {
            "track_id": self.track_id,
            "duration": self.duration,
            "detection_count": len(self.detections),
            "avg_confidence": self.avg_confidence,
            "is_stable": self.is_stable,
            "is_active": self.is_active,
            "recognition_attempts": self.recognition_attempts,
            "last_seen": self.last_seen,
            "center": self.center
        }


class FaceTracker:
    """
    Face tracking system using centroid tracking and Kalman filtering.
    
    Tracks faces across frames to:
    - Maintain temporal consistency
    - Reduce duplicate recognitions
    - Filter out brief/unstable detections
    - Provide smooth tracking data
    """
    
    def __init__(self, max_disappeared: int = 30, max_distance: float = 100.0):
        """
        Initialize face tracker.
        
        Args:
            max_disappeared: Maximum frames a track can be missing before removal
            max_distance: Maximum distance for associating detections to tracks
        """
        self.max_disappeared = max_disappeared
        self.max_distance = max_distance
        
        # Active tracks
        self.tracks: Dict[str, FaceTrack] = {}
        self.disappeared_counts: Dict[str, int] = {}
        
        # Performance stats
        self.total_tracks_created = 0
        self.total_tracks_expired = 0
        self.frames_processed = 0
        
        logger.info("Face tracker initialized", 
                   max_disappeared=max_disappeared,
                   max_distance=max_distance)
    
    async def update(self, detections: List[FaceDetection]) -> List[FaceTrack]:
        """
        Update tracks with new detections.
        
        Args:
            detections: List of face detections from current frame
            
        Returns:
            List of active face tracks
        """
        self.frames_processed += 1
        current_time = time.time()
        
        # If no detections, just increment disappeared counts
        if len(detections) == 0:
            self._handle_no_detections()
            return list(self.tracks.values())
        
        # If no existing tracks, create new ones
        if len(self.tracks) == 0:
            for detection in detections:
                self._create_new_track(detection)
            return list(self.tracks.values())
        
        # Associate detections with existing tracks
        track_ids = list(self.tracks.keys())
        detection_centers = [self._get_detection_center(d) for d in detections]
        track_centers = [self.tracks[tid].center for tid in track_ids]
        
        # Compute distance matrix and find optimal assignment
        assignments = self._assign_detections_to_tracks(
            detection_centers, track_centers, track_ids
        )
        
        # Update assigned tracks
        used_detection_indices = set()
        used_track_ids = set()
        
        for track_id, detection_idx in assignments:
            if detection_idx is not None and track_id is not None:
                self.tracks[track_id].add_detection(detections[detection_idx])
                self.disappeared_counts[track_id] = 0
                used_detection_indices.add(detection_idx)
                used_track_ids.add(track_id)
        
        # Handle unassigned tracks (increment disappeared count)
        for track_id in track_ids:
            if track_id not in used_track_ids:
                self.disappeared_counts[track_id] = self.disappeared_counts.get(track_id, 0) + 1
        
        # Create new tracks for unassigned detections
        for i, detection in enumerate(detections):
            if i not in used_detection_indices:
                self._create_new_track(detection)
        
        # Remove expired tracks
        self._cleanup_expired_tracks()
        
        return list(self.tracks.values())
    
    def _handle_no_detections(self):
        """Handle frame with no detections."""
        for track_id in list(self.tracks.keys()):
            self.disappeared_counts[track_id] = self.disappeared_counts.get(track_id, 0) + 1
    
    def _create_new_track(self, detection: FaceDetection) -> str:
        """Create a new face track."""
        track = FaceTrack()
        track.add_detection(detection)
        
        self.tracks[track.track_id] = track
        self.disappeared_counts[track.track_id] = 0
        self.total_tracks_created += 1
        
        logger.debug("Created new face track", 
                    track_id=track.track_id,
                    confidence=detection.confidence)
        
        return track.track_id
    
    def _assign_detections_to_tracks(
        self, 
        detection_centers: List[Tuple[int, int]], 
        track_centers: List[Tuple[int, int]],
        track_ids: List[str]
    ) -> List[Tuple[Optional[str], Optional[int]]]:
        """
        Assign detections to tracks using Hungarian algorithm approximation.
        
        Returns:
            List of (track_id, detection_index) assignments
        """
        if not detection_centers or not track_centers:
            return []
        
        # Compute distance matrix
        distances = np.zeros((len(track_centers), len(detection_centers)))
        
        for i, track_center in enumerate(track_centers):
            for j, detection_center in enumerate(detection_centers):
                distances[i, j] = self._calculate_distance(track_center, detection_center)
        
        # Simple greedy assignment (could be improved with Hungarian algorithm)
        assignments = []
        used_tracks = set()
        used_detections = set()
        
        # Sort by distance and assign greedily
        candidates = []
        for i in range(len(track_centers)):
            for j in range(len(detection_centers)):
                if distances[i, j] <= self.max_distance:
                    candidates.append((distances[i, j], i, j))
        
        candidates.sort()  # Sort by distance
        
        for distance, track_idx, detection_idx in candidates:
            if track_idx not in used_tracks and detection_idx not in used_detections:
                assignments.append((track_ids[track_idx], detection_idx))
                used_tracks.add(track_idx)
                used_detections.add(detection_idx)
        
        # Add unassigned tracks
        for i, track_id in enumerate(track_ids):
            if i not in used_tracks:
                assignments.append((track_id, None))
        
        return assignments
    
    def _calculate_distance(self, center1: Tuple[int, int], center2: Tuple[int, int]) -> float:
        """Calculate Euclidean distance between two centers."""
        return np.sqrt((center1[0] - center2[0])**2 + (center1[1] - center2[1])**2)
    
    def _get_detection_center(self, detection: FaceDetection) -> Tuple[int, int]:
        """Get center point of a face detection."""
        x, y, w, h = detection.bbox
        return (x + w // 2, y + h // 2)
    
    def _cleanup_expired_tracks(self):
        """Remove tracks that have been missing for too long."""
        expired_tracks = []
        
        for track_id, disappeared_count in self.disappeared_counts.items():
            if disappeared_count > self.max_disappeared:
                expired_tracks.append(track_id)
        
        for track_id in expired_tracks:
            if track_id in self.tracks:
                del self.tracks[track_id]
            if track_id in self.disappeared_counts:
                del self.disappeared_counts[track_id]
            self.total_tracks_expired += 1
            
            logger.debug("Expired face track", track_id=track_id)
    
    def get_tracks_for_recognition(self, min_interval: float = 5.0) -> List[FaceTrack]:
        """Get tracks that are ready for recognition."""
        return [
            track for track in self.tracks.values()
            if track.should_recognize(min_interval)
        ]
    
    def get_active_tracks(self) -> List[FaceTrack]:
        """Get all active tracks."""
        return [track for track in self.tracks.values() if track.is_active]
    
    def get_stable_tracks(self) -> List[FaceTrack]:
        """Get all stable tracks."""
        return [track for track in self.tracks.values() if track.is_stable]
    
    def mark_track_recognized(self, track_id: str):
        """Mark a track as recognized."""
        if track_id in self.tracks:
            self.tracks[track_id].mark_recognized()
    
    def get_stats(self) -> dict:
        """Get tracking performance statistics."""
        active_tracks = len([t for t in self.tracks.values() if t.is_active])
        stable_tracks = len([t for t in self.tracks.values() if t.is_stable])
        
        return {
            "frames_processed": self.frames_processed,
            "active_tracks": active_tracks,
            "stable_tracks": stable_tracks,
            "total_tracks": len(self.tracks),
            "total_tracks_created": self.total_tracks_created,
            "total_tracks_expired": self.total_tracks_expired,
            "max_disappeared": self.max_disappeared,
            "max_distance": self.max_distance
        }
    
    def visualize_tracks(self, frame: np.ndarray) -> np.ndarray:
        """
        Draw tracking information on frame for debugging.
        
        Args:
            frame: Input frame
            
        Returns:
            Frame with tracking visualization
        """
        annotated_frame = frame.copy()
        
        for track in self.tracks.values():
            if not track.detections:
                continue
                
            # Get latest detection
            latest_detection = track.detections[-1]
            x, y, w, h = latest_detection.bbox
            
            # Choose color based on track state
            if track.is_stable:
                color = (0, 255, 0)  # Green for stable
            elif track.is_active:
                color = (255, 255, 0)  # Yellow for active
            else:
                color = (128, 128, 128)  # Gray for inactive
            
            # Draw bounding box
            cv2.rectangle(annotated_frame, (x, y), (x + w, y + h), color, 2)
            
            # Draw track ID and info
            text = f"ID:{track.track_id[:8]} C:{track.avg_confidence:.2f}"
            cv2.putText(annotated_frame, text, (x, y - 10), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.5, color, 1)
            
            # Draw track history (trajectory)
            if len(track.position_history) > 1:
                points = np.array(track.position_history[-10:], dtype=np.int32)
                cv2.polylines(annotated_frame, [points], False, color, 1)
        
        return annotated_frame
