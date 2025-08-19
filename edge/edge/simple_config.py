"""
Simple configuration adapter for pipeline components.

Provides a flat configuration interface that our pipeline components expect,
while maintaining compatibility with the complex nested configuration.
"""

from typing import Union, Optional
from dataclasses import dataclass


@dataclass
class SimpleEdgeConfig:
    """Simple flat configuration for pipeline components."""
    
    # Device configuration
    device_code: str = "edge-001"
    
    # Video input configuration
    video_source: Union[str, int] = 0  # Webcam by default
    source: Union[str, int] = 0  # Alias for compatibility
    target_fps: float = 30.0
    fps: int = 30  # Alias for compatibility
    frame_width: Optional[int] = None
    frame_height: Optional[int] = None
    retry_interval: float = 5.0
    max_reconnect_attempts: int = 10
    
    # Backend API configuration
    backend_url: str = "http://localhost:8080"
    auth_token: Optional[str] = None
    
    # Face detection configuration
    detection_method: str = "haar"  # haar, dnn, onnx
    method: str = "opencv"  # Alias for compatibility
    confidence_threshold: float = 0.5
    detection_confidence_threshold: float = 0.5  # Alias for compatibility
    min_face_size: tuple = (30, 30)
    scale_factor: float = 1.1
    min_neighbors: int = 5
    
    # Face embedding configuration
    embedding_method: str = "stub"  # stub, opencv, onnx
    embedding_dim: int = 512
    
    # Face matching configuration
    similarity_threshold: float = 0.7
    recognition_interval: float = 5.0  # seconds between recognitions
    
    # Liveness detection configuration
    liveness_method: str = "motion"  # motion, blink, texture, ml
    liveness_threshold: float = 0.5
    
    # MinIO storage configuration
    minio_endpoint: str = "localhost:9000"
    minio_access_key: str = "minioadmin" 
    minio_secret_key: str = "minioadmin"
    minio_bucket: str = "face-recognition"
    minio_secure: bool = False
    upload_enabled: bool = True
    
    # Performance configuration
    batch_size: int = 10
    queue_size: int = 100
    max_retries: int = 3
    retry_delay: float = 1.0
    
    # Index sync configuration
    index_sync_interval: float = 300.0  # 5 minutes
    index_cache_path: str = "./cache/face_index.faiss"
    metadata_cache_path: str = "./cache/face_metadata.pkl"
    
    # Image processing configuration
    jpeg_quality: int = 85
    face_crop_size: int = 224
    
    # Publishing configuration
    publish_batch_size: int = 10
    publish_batch_timeout: float = 5.0
    publish_max_retries: int = 3
    publish_retry_delay: float = 1.0
    offline_queue_size: int = 1000
    
    # HTTP configuration
    http_timeout: float = 30.0
    
    # Display configuration
    display_stats: bool = True
    
    # Other configuration
    debug: bool = False


# Alias for compatibility
EdgeConfig = SimpleEdgeConfig
