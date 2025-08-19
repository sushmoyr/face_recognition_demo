"""
Main face recognition pipeline orchestrator.

Coordinates all pipeline components to process video streams,
detect and recognize faces, and publish recognition events.
"""

import asyncio
import time
import cv2
import numpy as np
from typing import Optional, Dict, Any, List
import structlog
from dataclasses import dataclass

from ..config import EdgeConfig
from .rtsp_reader import RTSPReader, FrameData
from .face_detector import FaceDetector, FaceDetection
from .embedding_extractor import EmbeddingExtractor
from .liveness_detector import LivenessDetector
from .face_tracker import FaceTracker, FaceTrack
from .index_sync import IndexSync
from .snapshot_uploader import SnapshotUploader
from .recognition_publisher import RecognitionPublisher

logger = structlog.get_logger(__name__)


@dataclass
class PipelineStats:
    """Pipeline performance statistics."""
    frames_processed: int = 0
    faces_detected: int = 0
    faces_recognized: int = 0
    recognitions_published: int = 0
    processing_fps: float = 0.0
    avg_processing_time: float = 0.0
    start_time: float = 0.0
    
    def to_dict(self) -> dict:
        return {
            "frames_processed": self.frames_processed,
            "faces_detected": self.faces_detected,
            "faces_recognized": self.faces_recognized,
            "recognitions_published": self.recognitions_published,
            "processing_fps": self.processing_fps,
            "avg_processing_time": self.avg_processing_time,
            "uptime_seconds": time.time() - self.start_time if self.start_time > 0 else 0
        }


class FaceRecognitionPipeline:
    """
    Main face recognition pipeline that orchestrates all components.
    
    Pipeline flow:
    1. Read frames from video source (RTSP/file/webcam)
    2. Detect faces in each frame
    3. Track faces across frames for stability
    4. Check liveness for detected faces
    5. Extract embeddings from live faces
    6. Match embeddings against known templates
    7. Upload snapshots to storage
    8. Publish recognition events to backend
    """
    
    def __init__(self, config: EdgeConfig):
        self.config = config
        self.device_code = getattr(config, 'device_code', 'edge-001')
        self.recognition_interval = getattr(config, 'recognition_interval', 5.0)  # seconds
        
        # Pipeline components
        self.rtsp_reader: Optional[RTSPReader] = None
        self.face_detector: Optional[FaceDetector] = None
        self.embedding_extractor: Optional[EmbeddingExtractor] = None
        self.liveness_detector: Optional[LivenessDetector] = None
        self.face_tracker: Optional[FaceTracker] = None
        self.index_sync: Optional[IndexSync] = None
        self.snapshot_uploader: Optional[SnapshotUploader] = None
        self.recognition_publisher: Optional[RecognitionPublisher] = None
        
        # Pipeline state
        self.running = False
        self.pipeline_task: Optional[asyncio.Task] = None
        self.stats = PipelineStats()
        
        # Performance tracking
        self.processing_times = []
        self.max_processing_times = 100
        
        logger.info("Face recognition pipeline created", device_code=self.device_code)
    
    async def initialize(self) -> bool:
        """Initialize all pipeline components."""
        try:
            logger.info("Initializing face recognition pipeline")
            
            # Initialize components in dependency order
            components = [
                ("RTSP Reader", self._init_rtsp_reader),
                ("Face Detector", self._init_face_detector),
                ("Embedding Extractor", self._init_embedding_extractor),
                ("Liveness Detector", self._init_liveness_detector),
                ("Face Tracker", self._init_face_tracker),
                ("Index Sync", self._init_index_sync),
                ("Snapshot Uploader", self._init_snapshot_uploader),
                ("Recognition Publisher", self._init_recognition_publisher),
            ]
            
            for component_name, init_func in components:
                logger.info("Initializing component", component=component_name)
                
                if not await init_func():
                    logger.error("Failed to initialize component", component=component_name)
                    return False
                    
                logger.info("Component initialized successfully", component=component_name)
            
            self.stats.start_time = time.time()
            logger.info("Face recognition pipeline initialized successfully")
            return True
            
        except Exception as e:
            logger.error("Failed to initialize pipeline", error=str(e))
            await self.cleanup()
            return False
    
    async def _init_rtsp_reader(self) -> bool:
        """Initialize RTSP reader component."""
        self.rtsp_reader = RTSPReader(self.config)
        return await self.rtsp_reader.initialize()
    
    async def _init_face_detector(self) -> bool:
        """Initialize face detector component."""
        self.face_detector = FaceDetector(self.config)
        return await self.face_detector.initialize()
    
    async def _init_embedding_extractor(self) -> bool:
        """Initialize embedding extractor component."""
        self.embedding_extractor = EmbeddingExtractor(self.config)
        return await self.embedding_extractor.initialize()
    
    async def _init_liveness_detector(self) -> bool:
        """Initialize liveness detector component."""
        self.liveness_detector = LivenessDetector(self.config)
        return await self.liveness_detector.initialize()
    
    async def _init_face_tracker(self) -> bool:
        """Initialize face tracker component."""
        self.face_tracker = FaceTracker(
            max_disappeared=30,
            max_distance=100.0
        )
        return True
    
    async def _init_index_sync(self) -> bool:
        """Initialize index sync component."""
        self.index_sync = IndexSync(self.config)
        return await self.index_sync.initialize()
    
    async def _init_snapshot_uploader(self) -> bool:
        """Initialize snapshot uploader component."""
        self.snapshot_uploader = SnapshotUploader(self.config)
        return await self.snapshot_uploader.initialize()
    
    async def _init_recognition_publisher(self) -> bool:
        """Initialize recognition publisher component."""
        self.recognition_publisher = RecognitionPublisher(self.config)
        return await self.recognition_publisher.initialize()
    
    async def start(self):
        """Start the face recognition pipeline."""
        if self.running:
            logger.warning("Pipeline is already running")
            return
        
        logger.info("Starting face recognition pipeline")
        self.running = True
        self.pipeline_task = asyncio.create_task(self._pipeline_loop())
    
    async def stop(self):
        """Stop the face recognition pipeline."""
        if not self.running:
            logger.warning("Pipeline is not running")
            return
        
        logger.info("Stopping face recognition pipeline")
        self.running = False
        
        if self.pipeline_task and not self.pipeline_task.done():
            self.pipeline_task.cancel()
            try:
                await self.pipeline_task
            except asyncio.CancelledError:
                pass
    
    async def cleanup(self):
        """Cleanup all pipeline components."""
        logger.info("Cleaning up face recognition pipeline")
        
        # Stop pipeline if running
        await self.stop()
        
        # Cleanup components in reverse order
        cleanup_tasks = []
        
        for component in [
            self.recognition_publisher,
            self.snapshot_uploader,
            self.index_sync,
            self.rtsp_reader
        ]:
            if component and hasattr(component, 'cleanup'):
                cleanup_tasks.append(component.cleanup())
        
        if cleanup_tasks:
            await asyncio.gather(*cleanup_tasks, return_exceptions=True)
        
        logger.info("Pipeline cleanup completed")
    
    async def _pipeline_loop(self):
        """Main pipeline processing loop."""
        logger.info("Pipeline processing loop started")
        
        try:
            # Start reading frames
            async for frame_data in self.rtsp_reader.read_frames():
                if not self.running:
                    break
                
                # Process frame
                await self._process_frame(frame_data)
                
                # Update stats
                self.stats.frames_processed += 1
                self._update_fps_stats()
                
        except asyncio.CancelledError:
            logger.info("Pipeline loop cancelled")
        except Exception as e:
            logger.error("Error in pipeline loop", error=str(e))
        finally:
            logger.info("Pipeline processing loop ended")
    
    async def _process_frame(self, frame_data: FrameData):
        """Process a single frame through the pipeline."""
        start_time = time.time()
        
        try:
            frame = frame_data.frame
            
            # 1. Detect faces
            detections = await self.face_detector.detect_faces(frame)
            self.stats.faces_detected += len(detections)
            
            if not detections:
                return
            
            # 2. Update face tracker
            tracks = await self.face_tracker.update(detections)
            
            # 3. Process tracks ready for recognition
            recognition_tracks = self.face_tracker.get_tracks_for_recognition(
                self.recognition_interval
            )
            
            for track in recognition_tracks:
                await self._process_recognition_track(frame, track)
            
        except Exception as e:
            logger.error("Error processing frame", error=str(e))
        finally:
            # Update performance stats
            processing_time = time.time() - start_time
            self.processing_times.append(processing_time)
            
            # Keep history limited
            if len(self.processing_times) > self.max_processing_times:
                self.processing_times.pop(0)
    
    async def _process_recognition_track(self, frame: np.ndarray, track: FaceTrack):
        """Process a face track for recognition."""
        try:
            # Get best detection from track
            best_detection = track.get_best_detection()
            if not best_detection:
                return
            
            # Extract face crop
            x, y, w, h = best_detection.bbox
            face_crop = frame[y:y+h, x:x+w]
            
            if face_crop.size == 0:
                return
            
            # Check liveness
            liveness_result = await self.liveness_detector.detect_liveness(frame, best_detection)
            
            if not liveness_result.is_live:
                logger.debug("Face failed liveness check", 
                           track_id=track.track_id,
                           liveness_score=liveness_result.score)
                return
            
            # Extract embedding
            embedding_result = await self.embedding_extractor.extract_embedding(face_crop)
            
            if embedding_result is None:
                logger.warning("Failed to extract embedding", track_id=track.track_id)
                return
            
            # Search for matches
            match_results = await self.index_sync.search_similar(embedding_result.embedding, k=1)
            match_result = match_results[0] if match_results else None
            
            if not match_result:
                logger.warning("No match result returned", track_id=track.track_id)
                return
            
            # Upload snapshot
            snapshot_metadata = None
            if self.snapshot_uploader:
                snapshot_metadata = await self.snapshot_uploader.upload_recognition_snapshot(
                    frame=frame,
                    face_bbox=best_detection.bbox,
                    employee_code=match_result.employee_code,
                    device_code=self.device_code,
                    confidence=best_detection.confidence,
                    is_match=match_result.is_match,
                    similarity=match_result.similarity,
                    aligned_face=face_crop
                )
            
            # Publish recognition event
            if self.recognition_publisher:
                success = await self.recognition_publisher.publish_recognition(
                    device_code=self.device_code,
                    match_result=match_result,
                    face_bbox=best_detection.bbox,
                    liveness_score=liveness_result.score,
                    snapshot_metadata=snapshot_metadata,
                    additional_metadata={
                        "track_id": track.track_id,
                        "track_duration": track.duration,
                        "detection_confidence": best_detection.confidence,
                        "liveness_method": liveness_result.method
                    }
                )
                
                if success:
                    self.stats.recognitions_published += 1
                    track.mark_recognized()
                    
                    if match_result.is_match:
                        self.stats.faces_recognized += 1
                    
                    logger.info("Recognition processed successfully",
                              track_id=track.track_id,
                              employee_code=match_result.employee_code,
                              is_match=match_result.is_match,
                              similarity=match_result.similarity,
                              liveness_score=liveness_result.score)
            
        except Exception as e:
            logger.error("Error processing recognition track", 
                        track_id=track.track_id,
                        error=str(e))
    
    def _update_fps_stats(self):
        """Update FPS and processing time statistics."""
        if len(self.processing_times) > 0:
            self.stats.avg_processing_time = sum(self.processing_times) / len(self.processing_times)
        
        # Calculate FPS based on recent frames
        if self.stats.frames_processed > 0 and self.stats.start_time > 0:
            elapsed_time = time.time() - self.stats.start_time
            self.stats.processing_fps = self.stats.frames_processed / elapsed_time
    
    def get_stats(self) -> dict:
        """Get comprehensive pipeline statistics."""
        pipeline_stats = self.stats.to_dict()
        
        # Add component stats
        component_stats = {}
        
        if self.rtsp_reader:
            component_stats["rtsp_reader"] = self.rtsp_reader.get_stats()
        if self.face_detector:
            component_stats["face_detector"] = self.face_detector.get_stats()
        if self.embedding_extractor:
            component_stats["embedding_extractor"] = self.embedding_extractor.get_stats()
        if self.liveness_detector:
            component_stats["liveness_detector"] = self.liveness_detector.get_stats()
        if self.face_tracker:
            component_stats["face_tracker"] = self.face_tracker.get_stats()
        if self.index_sync:
            component_stats["index_sync"] = self.index_sync.get_stats()
        if self.snapshot_uploader:
            component_stats["snapshot_uploader"] = self.snapshot_uploader.get_stats()
        if self.recognition_publisher:
            component_stats["recognition_publisher"] = self.recognition_publisher.get_stats()
        
        return {
            "pipeline": pipeline_stats,
            "components": component_stats,
            "device_code": self.device_code,
            "running": self.running
        }
