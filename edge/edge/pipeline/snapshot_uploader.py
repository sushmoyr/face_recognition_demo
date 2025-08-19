"""
Snapshot uploader component for MinIO storage.

Handles uploading face snapshots and related images to MinIO object storage
with metadata and organized folder structure.
"""

import io
import asyncio
import time
from typing import Optional, Dict, Any, List
import numpy as np
import cv2
from datetime import datetime
import uuid
import structlog
from dataclasses import dataclass

from ..config import EdgeConfig

logger = structlog.get_logger(__name__)

try:
    from minio import Minio
    from minio.error import S3Error
    MINIO_AVAILABLE = True
except ImportError:
    MINIO_AVAILABLE = False
    logger.warning("MinIO not available, snapshot uploading will be disabled")


@dataclass
class SnapshotMetadata:
    """Metadata for uploaded snapshots."""
    snapshot_id: str
    employee_code: Optional[str]
    device_code: str
    timestamp: datetime
    bbox: tuple  # (x, y, w, h)
    confidence: float
    is_match: bool
    similarity: float
    file_path: str
    content_type: str
    size_bytes: int
    
    def to_dict(self) -> dict:
        return {
            "snapshot_id": self.snapshot_id,
            "employee_code": self.employee_code,
            "device_code": self.device_code,
            "timestamp": self.timestamp.isoformat(),
            "bbox": list(self.bbox),
            "confidence": self.confidence,
            "is_match": self.is_match,
            "similarity": self.similarity,
            "file_path": self.file_path,
            "content_type": self.content_type,
            "size_bytes": self.size_bytes
        }


class SnapshotUploader:
    """
    MinIO snapshot uploader for face recognition images.
    
    Features:
    - Async image upload to MinIO
    - Organized folder structure by date/device/employee
    - Multiple image formats (full frame, face crop, aligned face)
    - Metadata tracking
    - Retry logic with exponential backoff
    - Upload queue management
    """
    
    def __init__(self, config: EdgeConfig):
        self.config = config
        
        # MinIO configuration
        self.endpoint = getattr(config, 'minio_endpoint', 'localhost:9000')
        self.access_key = getattr(config, 'minio_access_key', 'minioadmin')
        self.secret_key = getattr(config, 'minio_secret_key', 'minioadmin')
        self.bucket_name = getattr(config, 'minio_bucket', 'face-recognition')
        self.secure = getattr(config, 'minio_secure', False)
        
        # Upload settings
        self.upload_enabled = getattr(config, 'upload_enabled', True) and MINIO_AVAILABLE
        self.max_retries = getattr(config, 'upload_max_retries', 3)
        self.retry_delay = getattr(config, 'upload_retry_delay', 1.0)
        self.queue_size = getattr(config, 'upload_queue_size', 100)
        
        # Image settings
        self.jpeg_quality = getattr(config, 'jpeg_quality', 85)
        self.face_crop_size = getattr(config, 'face_crop_size', 224)
        
        # MinIO client
        self.client: Optional[Minio] = None
        
        # Upload queue and stats
        self.upload_queue: asyncio.Queue = None
        self.upload_task: Optional[asyncio.Task] = None
        self.uploads_completed = 0
        self.uploads_failed = 0
        self.total_bytes_uploaded = 0
        
        if not self.upload_enabled:
            logger.warning("Snapshot uploading is disabled")
    
    async def initialize(self) -> bool:
        """Initialize the snapshot uploader."""
        if not self.upload_enabled:
            logger.info("Snapshot uploader disabled")
            return True
        
        try:
            logger.info("Initializing snapshot uploader", 
                       endpoint=self.endpoint,
                       bucket=self.bucket_name)
            
            # Create MinIO client
            self.client = Minio(
                self.endpoint,
                access_key=self.access_key,
                secret_key=self.secret_key,
                secure=self.secure
            )
            
            # Check if bucket exists, create if not
            if not self.client.bucket_exists(self.bucket_name):
                self.client.make_bucket(self.bucket_name)
                logger.info("Created MinIO bucket", bucket=self.bucket_name)
            else:
                logger.info("MinIO bucket exists", bucket=self.bucket_name)
            
            # Initialize upload queue
            self.upload_queue = asyncio.Queue(maxsize=self.queue_size)
            
            # Start background upload worker
            self.upload_task = asyncio.create_task(self._upload_worker())
            
            logger.info("Snapshot uploader initialized successfully")
            return True
            
        except Exception as e:
            logger.error("Failed to initialize snapshot uploader", error=str(e))
            self.upload_enabled = False
            return False
    
    async def cleanup(self):
        """Cleanup uploader resources."""
        if self.upload_task and not self.upload_task.done():
            # Add sentinel to stop worker
            try:
                await self.upload_queue.put(None)
                await self.upload_task
            except Exception as e:
                logger.error("Error during upload worker cleanup", error=str(e))
    
    async def upload_recognition_snapshot(
        self, 
        frame: np.ndarray,
        face_bbox: tuple,
        employee_code: Optional[str],
        device_code: str,
        confidence: float,
        is_match: bool,
        similarity: float = 0.0,
        aligned_face: Optional[np.ndarray] = None
    ) -> Optional[SnapshotMetadata]:
        """
        Upload recognition snapshot with full frame and face crop.
        
        Args:
            frame: Full video frame
            face_bbox: Face bounding box (x, y, w, h)
            employee_code: Matched employee code (if any)
            device_code: Source device identifier
            confidence: Face detection confidence
            is_match: Whether face matched someone
            similarity: Similarity score if matched
            aligned_face: Aligned face image (optional)
            
        Returns:
            Snapshot metadata if successful
        """
        if not self.upload_enabled:
            return None
        
        try:
            # Generate snapshot ID and timestamp
            snapshot_id = str(uuid.uuid4())
            timestamp = datetime.now()
            
            # Create upload items
            upload_items = []
            
            # 1. Full frame
            full_frame_path = self._generate_path(
                timestamp, device_code, employee_code, snapshot_id, "full_frame.jpg"
            )
            full_frame_data = self._encode_image(frame, "jpg")
            upload_items.append({
                "path": full_frame_path,
                "data": full_frame_data,
                "content_type": "image/jpeg"
            })
            
            # 2. Face crop
            x, y, w, h = face_bbox
            face_crop = frame[y:y+h, x:x+w]
            
            # Resize face crop to standard size
            if face_crop.size > 0:
                face_crop = cv2.resize(face_crop, (self.face_crop_size, self.face_crop_size))
                
                face_crop_path = self._generate_path(
                    timestamp, device_code, employee_code, snapshot_id, "face_crop.jpg"
                )
                face_crop_data = self._encode_image(face_crop, "jpg")
                upload_items.append({
                    "path": face_crop_path,
                    "data": face_crop_data,
                    "content_type": "image/jpeg"
                })
            
            # 3. Aligned face (if provided)
            if aligned_face is not None and aligned_face.size > 0:
                aligned_face_path = self._generate_path(
                    timestamp, device_code, employee_code, snapshot_id, "aligned_face.jpg"
                )
                aligned_face_data = self._encode_image(aligned_face, "jpg")
                upload_items.append({
                    "path": aligned_face_path,
                    "data": aligned_face_data,
                    "content_type": "image/jpeg"
                })
            
            # Queue uploads
            total_size = sum(len(item["data"]) for item in upload_items)
            
            for item in upload_items:
                await self._queue_upload(item["path"], item["data"], item["content_type"])
            
            # Create metadata
            metadata = SnapshotMetadata(
                snapshot_id=snapshot_id,
                employee_code=employee_code,
                device_code=device_code,
                timestamp=timestamp,
                bbox=face_bbox,
                confidence=confidence,
                is_match=is_match,
                similarity=similarity,
                file_path=full_frame_path,
                content_type="image/jpeg",
                size_bytes=total_size
            )
            
            logger.debug("Queued recognition snapshot for upload", 
                        snapshot_id=snapshot_id,
                        employee_code=employee_code,
                        files=len(upload_items))
            
            return metadata
            
        except Exception as e:
            logger.error("Error preparing recognition snapshot", error=str(e))
            return None
    
    async def upload_enrollment_snapshot(
        self,
        face_image: np.ndarray,
        employee_code: str,
        device_code: str,
        template_id: str
    ) -> Optional[str]:
        """
        Upload enrollment snapshot for face template creation.
        
        Args:
            face_image: Face image for enrollment
            employee_code: Employee identifier
            device_code: Source device identifier
            template_id: Template identifier
            
        Returns:
            Upload path if successful
        """
        if not self.upload_enabled:
            return None
        
        try:
            timestamp = datetime.now()
            
            # Generate path for enrollment image
            file_path = self._generate_path(
                timestamp, device_code, employee_code, template_id, "enrollment.jpg"
            )
            
            # Encode image
            image_data = self._encode_image(face_image, "jpg")
            
            # Queue upload
            await self._queue_upload(file_path, image_data, "image/jpeg")
            
            logger.debug("Queued enrollment snapshot for upload",
                        employee_code=employee_code,
                        template_id=template_id,
                        path=file_path)
            
            return file_path
            
        except Exception as e:
            logger.error("Error preparing enrollment snapshot", error=str(e))
            return None
    
    def _generate_path(
        self, 
        timestamp: datetime, 
        device_code: str, 
        employee_code: Optional[str], 
        snapshot_id: str, 
        filename: str
    ) -> str:
        """Generate organized file path for MinIO."""
        # Format: YYYY/MM/DD/device_code/employee_code/snapshot_id/filename
        date_path = timestamp.strftime("%Y/%m/%d")
        
        if employee_code:
            return f"{date_path}/{device_code}/{employee_code}/{snapshot_id}/{filename}"
        else:
            return f"{date_path}/{device_code}/unknown/{snapshot_id}/{filename}"
    
    def _encode_image(self, image: np.ndarray, format: str = "jpg") -> bytes:
        """Encode image to bytes."""
        if format.lower() in ["jpg", "jpeg"]:
            encode_params = [cv2.IMWRITE_JPEG_QUALITY, self.jpeg_quality]
            success, encoded = cv2.imencode('.jpg', image, encode_params)
        elif format.lower() == "png":
            encode_params = [cv2.IMWRITE_PNG_COMPRESSION, 6]
            success, encoded = cv2.imencode('.png', image, encode_params)
        else:
            raise ValueError(f"Unsupported image format: {format}")
        
        if not success:
            raise RuntimeError("Failed to encode image")
        
        return encoded.tobytes()
    
    async def _queue_upload(self, file_path: str, data: bytes, content_type: str):
        """Queue an upload item."""
        upload_item = {
            "path": file_path,
            "data": data,
            "content_type": content_type,
            "timestamp": time.time()
        }
        
        try:
            await asyncio.wait_for(
                self.upload_queue.put(upload_item), 
                timeout=1.0
            )
        except asyncio.TimeoutError:
            logger.warning("Upload queue full, dropping snapshot", path=file_path)
            raise
    
    async def _upload_worker(self):
        """Background worker for processing upload queue."""
        logger.info("Started snapshot upload worker")
        
        while True:
            try:
                # Get upload item from queue
                upload_item = await self.upload_queue.get()
                
                # Sentinel value to stop worker
                if upload_item is None:
                    break
                
                # Perform upload with retries
                await self._upload_with_retries(upload_item)
                
                self.upload_queue.task_done()
                
            except asyncio.CancelledError:
                logger.info("Upload worker cancelled")
                break
            except Exception as e:
                logger.error("Error in upload worker", error=str(e))
    
    async def _upload_with_retries(self, upload_item: Dict[str, Any]):
        """Upload item with retry logic."""
        file_path = upload_item["path"]
        data = upload_item["data"]
        content_type = upload_item["content_type"]
        
        for attempt in range(self.max_retries + 1):
            try:
                # Create data stream
                data_stream = io.BytesIO(data)
                data_length = len(data)
                
                # Upload to MinIO
                self.client.put_object(
                    bucket_name=self.bucket_name,
                    object_name=file_path,
                    data=data_stream,
                    length=data_length,
                    content_type=content_type
                )
                
                # Success
                self.uploads_completed += 1
                self.total_bytes_uploaded += data_length
                
                logger.debug("Successfully uploaded snapshot", 
                           path=file_path,
                           size_bytes=data_length,
                           attempt=attempt + 1)
                return
                
            except Exception as e:
                if attempt < self.max_retries:
                    delay = self.retry_delay * (2 ** attempt)  # Exponential backoff
                    logger.warning("Upload failed, retrying", 
                                 path=file_path,
                                 attempt=attempt + 1,
                                 error=str(e),
                                 retry_delay=delay)
                    await asyncio.sleep(delay)
                else:
                    logger.error("Upload failed after all retries", 
                               path=file_path,
                               attempts=self.max_retries + 1,
                               error=str(e))
                    self.uploads_failed += 1
                    break
    
    def get_stats(self) -> dict:
        """Get upload performance statistics."""
        total_uploads = self.uploads_completed + self.uploads_failed
        success_rate = (self.uploads_completed / total_uploads 
                       if total_uploads > 0 else 0)
        
        queue_size = 0
        if self.upload_queue:
            queue_size = self.upload_queue.qsize()
        
        return {
            "upload_enabled": self.upload_enabled,
            "uploads_completed": self.uploads_completed,
            "uploads_failed": self.uploads_failed,
            "success_rate": success_rate,
            "total_bytes_uploaded": self.total_bytes_uploaded,
            "queue_size": queue_size,
            "max_queue_size": self.queue_size,
            "bucket_name": self.bucket_name,
            "endpoint": self.endpoint
        }
