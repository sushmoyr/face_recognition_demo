"""
Face liveness detection component.

Determines if detected faces are from live persons or spoofing attempts
using various liveness detection techniques.
"""

import cv2
import numpy as np
import asyncio
from typing import Optional, Tuple
import structlog
from dataclasses import dataclass

from ..config import EdgeConfig
from .face_detector import FaceDetection

logger = structlog.get_logger(__name__)


@dataclass
class LivenessResult:
    """Result of liveness detection."""
    is_live: bool
    confidence: float
    score: float
    method: str
    details: dict = None
    
    def to_dict(self) -> dict:
        return {
            "is_live": self.is_live,
            "confidence": self.confidence,
            "score": self.score,
            "method": self.method,
            "details": self.details or {}
        }


class LivenessDetector:
    """
    Face liveness detector with multiple detection methods.
    
    Methods supported:
    - Motion-based: Detects movement patterns
    - Texture-based: Analyzes image texture patterns
    - Eye blink detection: Detects eye blinking
    - 3D depth: Uses depth information (if available)
    - ML-based: Uses trained models for spoofing detection
    """
    
    def __init__(self, config: EdgeConfig):
        self.config = config
        self.detection_method = getattr(config, 'liveness_method', 'motion')
        self.confidence_threshold = getattr(config, 'liveness_threshold', 0.5)
        
        # Motion-based detection state
        self.previous_frame = None
        self.motion_history = []
        self.max_history_length = 10
        
        # Eye blink detection
        self.eye_cascade = None
        self.blink_counter = 0
        self.eye_states = []
        
        # Performance tracking
        self.detections_performed = 0
        self.live_detections = 0
        self.spoof_detections = 0
    
    async def initialize(self) -> bool:
        """Initialize the liveness detection system."""
        try:
            logger.info("Initializing liveness detector", method=self.detection_method)
            
            if self.detection_method == "motion":
                await self._init_motion_detector()
            elif self.detection_method == "blink":
                await self._init_blink_detector()
            elif self.detection_method == "texture":
                await self._init_texture_detector()
            elif self.detection_method == "ml":
                await self._init_ml_detector()
            else:
                logger.warning("Unknown liveness method, using motion", method=self.detection_method)
                self.detection_method = "motion"
                await self._init_motion_detector()
            
            logger.info("Liveness detector initialized successfully")
            return True
            
        except Exception as e:
            logger.error("Failed to initialize liveness detector", error=str(e))
            return False
    
    async def _init_motion_detector(self):
        """Initialize motion-based liveness detection."""
        # No special initialization needed for motion detection
        pass
    
    async def _init_blink_detector(self):
        """Initialize eye blink detection."""
        try:
            eye_cascade_path = cv2.data.haarcascades + 'haarcascade_eye.xml'
            self.eye_cascade = cv2.CascadeClassifier(eye_cascade_path)
            
            if self.eye_cascade.empty():
                logger.warning("Eye cascade not found, falling back to motion detection")
                self.detection_method = "motion"
                
        except Exception as e:
            logger.warning("Failed to load eye cascade, using motion detection", error=str(e))
            self.detection_method = "motion"
    
    async def _init_texture_detector(self):
        """Initialize texture-based detection."""
        # Stub - would initialize texture analysis models here
        logger.warning("Texture detection not implemented yet, using motion detection")
        self.detection_method = "motion"
    
    async def _init_ml_detector(self):
        """Initialize ML-based liveness detection."""
        # Stub - would load ML models here
        logger.warning("ML liveness detection not implemented yet, using motion detection")
        self.detection_method = "motion"
    
    async def detect_liveness(self, frame: np.ndarray, face_detection: FaceDetection) -> LivenessResult:
        """
        Detect if the face is from a live person.
        
        Args:
            frame: Full frame containing the face
            face_detection: Detected face with bounding box
            
        Returns:
            Liveness detection result
        """
        self.detections_performed += 1
        
        try:
            if self.detection_method == "motion":
                result = await self._detect_motion_liveness(frame, face_detection)
            elif self.detection_method == "blink":
                result = await self._detect_blink_liveness(frame, face_detection)
            elif self.detection_method == "texture":
                result = await self._detect_texture_liveness(frame, face_detection)
            elif self.detection_method == "ml":
                result = await self._detect_ml_liveness(frame, face_detection)
            else:
                # Fallback to motion detection
                result = await self._detect_motion_liveness(frame, face_detection)
            
            if result.is_live:
                self.live_detections += 1
            else:
                self.spoof_detections += 1
            
            logger.debug("Liveness detection completed", 
                        is_live=result.is_live, 
                        confidence=result.confidence,
                        method=result.method)
            
            return result
            
        except Exception as e:
            logger.error("Error in liveness detection", error=str(e))
            # Return conservative result (assume live to avoid false positives)
            return LivenessResult(
                is_live=True,
                confidence=0.5,
                score=0.5,
                method="error_fallback",
                details={"error": str(e)}
            )
    
    async def _detect_motion_liveness(self, frame: np.ndarray, face_detection: FaceDetection) -> LivenessResult:
        """Detect liveness based on motion patterns."""
        gray_frame = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        
        # Extract face region
        x, y, w, h = face_detection.bbox
        face_region = gray_frame[y:y+h, x:x+w]
        
        if self.previous_frame is None:
            self.previous_frame = face_region.copy()
            # First frame, assume live
            return LivenessResult(
                is_live=True,
                confidence=0.7,
                score=0.7,
                method="motion",
                details={"reason": "first_frame"}
            )
        
        # Calculate optical flow or simple frame difference
        motion_score = self._calculate_motion_score(face_region, self.previous_frame)
        self.motion_history.append(motion_score)
        
        # Keep history limited
        if len(self.motion_history) > self.max_history_length:
            self.motion_history.pop(0)
        
        # Analyze motion patterns
        if len(self.motion_history) >= 3:
            avg_motion = np.mean(self.motion_history)
            motion_variance = np.var(self.motion_history)
            
            # Live faces should have some motion but not too much
            # Static images will have low motion, videos might have artificial patterns
            is_live = (0.1 < avg_motion < 0.8) and (motion_variance > 0.01)
            confidence = min(avg_motion * 2, 1.0) if is_live else max(1.0 - avg_motion * 2, 0.0)
        else:
            # Not enough history, be conservative
            is_live = True
            confidence = 0.6
        
        self.previous_frame = face_region.copy()
        
        return LivenessResult(
            is_live=is_live,
            confidence=confidence,
            score=motion_score,
            method="motion",
            details={
                "motion_score": motion_score,
                "avg_motion": np.mean(self.motion_history) if self.motion_history else 0,
                "motion_variance": np.var(self.motion_history) if len(self.motion_history) > 1 else 0
            }
        )
    
    async def _detect_blink_liveness(self, frame: np.ndarray, face_detection: FaceDetection) -> LivenessResult:
        """Detect liveness based on eye blinking."""
        if self.eye_cascade is None:
            return await self._detect_motion_liveness(frame, face_detection)
        
        # Extract face region
        x, y, w, h = face_detection.bbox
        face_crop = frame[y:y+h, x:x+w]
        gray_face = cv2.cvtColor(face_crop, cv2.COLOR_BGR2GRAY)
        
        # Detect eyes
        eyes = self.eye_cascade.detectMultiScale(gray_face, 1.1, 5)
        
        # Simple blink detection logic
        current_eye_state = len(eyes) > 0
        self.eye_states.append(current_eye_state)
        
        # Keep only recent states
        if len(self.eye_states) > 20:
            self.eye_states.pop(0)
        
        # Count blinks (state changes)
        blinks = 0
        for i in range(1, len(self.eye_states)):
            if self.eye_states[i-1] != self.eye_states[i]:
                blinks += 1
        
        # Live faces should have some eye state changes
        if len(self.eye_states) >= 10:
            is_live = blinks > 0
            confidence = min(blinks / 10.0, 1.0)
        else:
            is_live = True  # Not enough data
            confidence = 0.5
        
        return LivenessResult(
            is_live=is_live,
            confidence=confidence,
            score=blinks,
            method="blink",
            details={
                "eyes_detected": len(eyes),
                "blink_count": blinks,
                "eye_state_history_length": len(self.eye_states)
            }
        )
    
    async def _detect_texture_liveness(self, frame: np.ndarray, face_detection: FaceDetection) -> LivenessResult:
        """Detect liveness based on texture analysis."""
        # Stub implementation - would analyze texture patterns here
        return await self._detect_motion_liveness(frame, face_detection)
    
    async def _detect_ml_liveness(self, frame: np.ndarray, face_detection: FaceDetection) -> LivenessResult:
        """Detect liveness using ML models."""
        # Stub implementation - would use trained models here
        return await self._detect_motion_liveness(frame, face_detection)
    
    def _calculate_motion_score(self, current_frame: np.ndarray, previous_frame: np.ndarray) -> float:
        """Calculate motion score between two frames."""
        try:
            # Resize frames to same size if needed
            if current_frame.shape != previous_frame.shape:
                min_h = min(current_frame.shape[0], previous_frame.shape[0])
                min_w = min(current_frame.shape[1], previous_frame.shape[1])
                current_frame = current_frame[:min_h, :min_w]
                previous_frame = previous_frame[:min_h, :min_w]
            
            # Calculate absolute difference
            diff = cv2.absdiff(current_frame, previous_frame)
            
            # Calculate motion score (normalized)
            motion_pixels = np.sum(diff > 10)  # Threshold for motion
            total_pixels = diff.size
            motion_score = motion_pixels / total_pixels
            
            return motion_score
            
        except Exception as e:
            logger.error("Error calculating motion score", error=str(e))
            return 0.0
    
    def get_stats(self) -> dict:
        """Get liveness detection performance statistics."""
        live_rate = (self.live_detections / self.detections_performed 
                    if self.detections_performed > 0 else 0)
        
        return {
            "detection_method": self.detection_method,
            "detections_performed": self.detections_performed,
            "live_detections": self.live_detections,
            "spoof_detections": self.spoof_detections,
            "live_rate": live_rate,
            "confidence_threshold": self.confidence_threshold
        }
