"""
RTSP stream reader with fallback to video files.

Provides frame-by-frame reading from RTSP streams or local video files
with proper error handling and reconnection logic.
"""

import cv2
import asyncio
import time
from pathlib import Path
from typing import Optional, AsyncGenerator, Tuple
import structlog
import numpy as np
from dataclasses import dataclass

from ..config import EdgeConfig

logger = structlog.get_logger(__name__)


@dataclass
class FrameData:
    """Data structure for video frame with metadata."""
    frame: np.ndarray
    timestamp: float
    frame_number: int
    source: str
    
    def __post_init__(self):
        """Validate frame data after initialization."""
        if self.frame is None:
            raise ValueError("Frame cannot be None")
        if len(self.frame.shape) != 3:
            raise ValueError("Frame must be a 3D array (H, W, C)")


class RTSPReader:
    """
    RTSP stream reader with video file fallback support.
    
    Features:
    - Auto-reconnection on stream failures
    - FPS control and frame dropping
    - Multiple source types (RTSP, file, webcam)
    - Performance metrics
    """
    
    def __init__(self, config: EdgeConfig):
        self.config = config
        self.cap: Optional[cv2.VideoCapture] = None
        # Convert source to string if it's an integer (for webcam)
        if isinstance(config.video_source, int):
            self.source = str(config.video_source)
        else:
            self.source = config.video_source
        self.target_fps = config.target_fps
        self.reconnect_attempts = 0
        self.max_reconnect_attempts = config.max_reconnect_attempts
        self.last_frame_time = 0
        self.frame_interval = 1.0 / self.target_fps if self.target_fps > 0 else 0
        
        # Performance tracking
        self.frames_read = 0
        self.frames_dropped = 0
        self.start_time = time.time()
        
    async def initialize(self) -> bool:
        """Initialize video capture source."""
        try:
            logger.info("Initializing video source", source=self.source)
            
            # Determine source type
            if self.source.startswith(('rtsp://', 'http://', 'https://')):
                # Network stream
                self.cap = cv2.VideoCapture(self.source, cv2.CAP_FFMPEG)
                # Configure for RTSP
                self.cap.set(cv2.CAP_PROP_BUFFERSIZE, 1)  # Reduce buffering
                self.cap.set(cv2.CAP_PROP_FPS, self.target_fps)
                
            elif self.source.isdigit():
                # Webcam
                self.cap = cv2.VideoCapture(int(self.source))
                
            else:
                # File path
                file_path = Path(self.source)
                if not file_path.exists():
                    logger.error("Video file not found", path=str(file_path))
                    return False
                self.cap = cv2.VideoCapture(str(file_path))
            
            if not self.cap or not self.cap.isOpened():
                logger.error("Failed to open video source", source=self.source)
                return False
            
            # Get source properties
            width = int(self.cap.get(cv2.CAP_PROP_FRAME_WIDTH))
            height = int(self.cap.get(cv2.CAP_PROP_FRAME_HEIGHT))
            fps = self.cap.get(cv2.CAP_PROP_FPS)
            
            logger.info("Video source initialized", 
                       source=self.source, width=width, height=height, fps=fps)
            
            self.reconnect_attempts = 0
            return True
            
        except Exception as e:
            logger.error("Error initializing video source", error=str(e), source=self.source)
            return False
    
    async def read_frames(self) -> AsyncGenerator[FrameData, None]:
        """
        Async generator that yields frames with metadata.
        
        Yields:
            FrameData: Frame with timestamp and metadata
        """
        if not await self.initialize():
            return
        
        try:
            while True:
                current_time = time.time()
                
                # FPS control - skip if we're reading too fast
                if self.frame_interval > 0 and (current_time - self.last_frame_time) < self.frame_interval:
                    await asyncio.sleep(0.001)  # Small sleep to prevent busy waiting
                    continue
                
                ret, frame = self.cap.read()
                
                if not ret:
                    if self.source.startswith(('rtsp://', 'http://')):
                        # Stream ended or connection lost, try to reconnect
                        if await self._handle_reconnection():
                            continue
                        else:
                            logger.error("Max reconnection attempts exceeded")
                            break
                    else:
                        # File ended
                        logger.info("Video file ended", frames_read=self.frames_read)
                        break
                
                self.frames_read += 1
                self.last_frame_time = current_time
                
                # Resize frame if needed
                if hasattr(self.config, 'frame_width') and hasattr(self.config, 'frame_height'):
                    if self.config.frame_width and self.config.frame_height:
                        frame = cv2.resize(frame, (self.config.frame_width, self.config.frame_height))
                
                # Create FrameData object
                frame_data = FrameData(
                    frame=frame,
                    timestamp=current_time,
                    frame_number=self.frames_read,
                    source=str(self.source)
                )
                
                yield frame_data
                
                # Small yield to allow other tasks to run
                await asyncio.sleep(0.001)
                
        except Exception as e:
            logger.error("Error reading frames", error=str(e))
        finally:
            await self.cleanup()
    
    async def _handle_reconnection(self) -> bool:
        """Handle stream reconnection logic."""
        if self.reconnect_attempts >= self.max_reconnect_attempts:
            return False
        
        self.reconnect_attempts += 1
        logger.warning("Stream connection lost, attempting reconnection", 
                      attempt=self.reconnect_attempts, 
                      max_attempts=self.max_reconnect_attempts)
        
        await self.cleanup()
        await asyncio.sleep(2 ** self.reconnect_attempts)  # Exponential backoff
        
        return await self.initialize()
    
    async def cleanup(self):
        """Clean up video capture resources."""
        if self.cap:
            self.cap.release()
            self.cap = None
    
    def get_stats(self) -> dict:
        """Get performance statistics."""
        runtime = time.time() - self.start_time
        fps = self.frames_read / runtime if runtime > 0 else 0
        
        return {
            "frames_read": self.frames_read,
            "frames_dropped": self.frames_dropped,
            "runtime_seconds": runtime,
            "average_fps": fps,
            "reconnect_attempts": self.reconnect_attempts,
            "source": self.source
        }
