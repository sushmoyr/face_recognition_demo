"""
Configuration management for the edge service.

Handles environment variables, model paths, thresholds,
and service configuration with validation and defaults.
"""

import os
from typing import Optional
from dataclasses import dataclass, field
from pathlib import Path

import structlog

logger = structlog.get_logger(__name__)


@dataclass
class ModelConfig:
    """Configuration for ML models."""
    
    # Face detection model
    detection_model_path: str = "models/retinaface.onnx"
    detection_confidence_threshold: float = 0.8
    detection_nms_threshold: float = 0.4
    
    # Face embedding model
    embedding_model_path: str = "models/arcface.onnx"
    embedding_dimension: int = 512
    
    # Liveness detection model
    liveness_model_path: str = "models/liveness.onnx"
    liveness_threshold: float = 0.5
    
    # Model URLs for automatic download
    detection_model_url: Optional[str] = None
    embedding_model_url: Optional[str] = None
    liveness_model_url: Optional[str] = None


@dataclass
class ProcessingConfig:
    """Configuration for video processing."""
    
    # Video input
    rtsp_url: Optional[str] = None
    video_file_path: Optional[str] = None
    camera_index: int = 0
    
    # Processing parameters
    target_fps: int = 10
    frame_skip: int = 3
    max_queue_size: int = 100
    
    # Face processing
    min_face_size: int = 80
    max_face_size: int = 640
    face_crop_margin: float = 0.2
    quality_threshold: float = 0.7


@dataclass
class RecognitionConfig:
    """Configuration for face recognition."""
    
    # Similarity matching
    similarity_threshold: float = 0.6
    max_candidates: int = 5
    
    # Index management
    index_sync_interval: int = 300  # seconds
    index_rebuild_threshold: int = 1000  # templates
    
    # Deduplication
    cooldown_seconds: int = 30
    max_recognitions_per_minute: int = 10


@dataclass
class DeviceConfig:
    """Configuration for device identity and location."""
    
    device_id: str = "edge-001"
    device_location: str = "Main Entrance"
    device_type: str = "CAMERA"


@dataclass
class BackendConfig:
    """Configuration for backend API communication."""
    
    base_url: str = "http://localhost:8080"
    timeout: int = 30
    max_retries: int = 3
    retry_delay: int = 5
    
    # Authentication
    username: Optional[str] = None
    password: Optional[str] = None
    api_key: Optional[str] = None
    
    # Endpoints
    recognition_endpoint: str = "/api/recognitions"
    templates_endpoint: str = "/api/employees/templates"
    device_heartbeat_endpoint: str = "/api/devices/heartbeat"


@dataclass
class StorageConfig:
    """Configuration for MinIO/S3 storage."""
    
    url: str = "http://localhost:9000"
    access_key: str = "minioadmin"
    secret_key: str = "minioadmin123"
    bucket_name: str = "attendance-faces"
    
    # Upload settings
    upload_snapshots: bool = True
    compress_images: bool = True
    jpeg_quality: int = 85
    max_image_size: tuple = (1920, 1080)


@dataclass
class MonitoringConfig:
    """Configuration for monitoring and metrics."""
    
    # Logging
    log_level: str = "INFO"
    log_format: str = "json"
    log_file: Optional[str] = None
    
    # Metrics
    prometheus_port: int = 8000
    metrics_enabled: bool = True
    
    # Health checks
    health_check_interval: int = 60
    max_consecutive_failures: int = 5


@dataclass
class EdgeConfig:
    """Main configuration class for the edge service."""
    
    # Sub-configurations
    model: ModelConfig = field(default_factory=ModelConfig)
    processing: ProcessingConfig = field(default_factory=ProcessingConfig)
    recognition: RecognitionConfig = field(default_factory=RecognitionConfig)
    device: DeviceConfig = field(default_factory=DeviceConfig)
    backend: BackendConfig = field(default_factory=BackendConfig)
    storage: StorageConfig = field(default_factory=StorageConfig)
    monitoring: MonitoringConfig = field(default_factory=MonitoringConfig)
    
    # Runtime flags
    debug: bool = False
    dry_run: bool = False
    offline_mode: bool = False
    gpu_enabled: bool = False


def load_config() -> EdgeConfig:
    """Load configuration from environment variables."""
    
    config = EdgeConfig()
    
    # Model configuration
    config.model.detection_model_path = os.getenv(
        "DETECTION_MODEL_PATH", config.model.detection_model_path
    )
    config.model.embedding_model_path = os.getenv(
        "EMBEDDING_MODEL_PATH", config.model.embedding_model_path
    )
    config.model.liveness_model_path = os.getenv(
        "LIVENESS_MODEL_PATH", config.model.liveness_model_path
    )
    config.model.detection_model_url = os.getenv("DETECTION_MODEL_URL")
    config.model.embedding_model_url = os.getenv("EMBEDDING_MODEL_URL")
    config.model.liveness_model_url = os.getenv("LIVENESS_MODEL_URL")
    
    # Processing configuration with RTSP encryption support
    rtsp_url = os.getenv("RTSP_URL")
    if rtsp_url and rtsp_url.startswith("rtsp://encrypted:"):
        # Decrypt RTSP URL if encrypted
        try:
            from .utils.config_encryption import ConfigEncryption
            encryptor = ConfigEncryption()
            config.processing.rtsp_url = encryptor.decrypt_rtsp_url(rtsp_url)
            logger.debug("RTSP URL decrypted successfully")
        except ImportError:
            logger.error("Config encryption module not available for encrypted RTSP URL")
            config.processing.rtsp_url = None
        except Exception as e:
            logger.error("Failed to decrypt RTSP URL", error=str(e))
            config.processing.rtsp_url = None
    else:
        config.processing.rtsp_url = rtsp_url
    
    config.processing.video_file_path = os.getenv("VIDEO_FILE_PATH")
    config.processing.camera_index = int(os.getenv("CAMERA_INDEX", "0"))
    config.processing.target_fps = int(os.getenv("TARGET_FPS", "10"))
    config.processing.frame_skip = int(os.getenv("FRAME_SKIP", "3"))
    
    # Recognition configuration
    config.recognition.similarity_threshold = float(
        os.getenv("SIMILARITY_THRESHOLD", "0.6")
    )
    config.recognition.cooldown_seconds = int(
        os.getenv("COOLDOWN_SECONDS", "30")
    )
    config.recognition.index_sync_interval = int(
        os.getenv("SYNC_INTERVAL_SECONDS", "300")
    )
    
    # Device configuration
    config.device.device_id = os.getenv("DEVICE_ID", "edge-001")
    config.device.device_location = os.getenv("DEVICE_LOCATION", "Main Entrance")
    
    # Backend configuration with encryption support for sensitive values
    config.backend.base_url = os.getenv("BACKEND_URL", "http://localhost:8080")
    config.backend.username = os.getenv("BACKEND_USERNAME")
    config.backend.password = _decrypt_if_encrypted(os.getenv("BACKEND_PASSWORD"))
    config.backend.api_key = _decrypt_if_encrypted(os.getenv("BACKEND_API_KEY"))
    
    # Storage configuration with encryption support
    config.storage.url = os.getenv("MINIO_URL", "http://localhost:9000")
    config.storage.access_key = _decrypt_if_encrypted(os.getenv("MINIO_ACCESS_KEY", "minioadmin"))
    config.storage.secret_key = _decrypt_if_encrypted(os.getenv("MINIO_SECRET_KEY", "minioadmin123"))
    config.storage.bucket_name = os.getenv("MINIO_BUCKET", "attendance-faces")
    
    # Monitoring configuration
    config.monitoring.log_level = os.getenv("LOG_LEVEL", "INFO")
    config.monitoring.prometheus_port = int(os.getenv("PROMETHEUS_PORT", "8000"))
    
    # Runtime flags
    config.debug = os.getenv("DEBUG", "false").lower() == "true"
    config.dry_run = os.getenv("DRY_RUN", "false").lower() == "true"
    config.offline_mode = os.getenv("OFFLINE_MODE", "false").lower() == "true"
    config.gpu_enabled = os.getenv("GPU_ENABLED", "false").lower() == "true"
    
    # Validate configuration
    _validate_config(config)
    
    logger.info("Configuration loaded successfully", 
               device_id=config.device.device_id,
               backend_url=config.backend.base_url,
               gpu_enabled=config.gpu_enabled,
               rtsp_encrypted=rtsp_url and rtsp_url.startswith("rtsp://encrypted:"))
    
    return config


def _decrypt_if_encrypted(value: Optional[str]) -> Optional[str]:
    """Decrypt a value if it appears to be encrypted."""
    if not value:
        return value
    
    # Simple heuristic: if it looks like base64 and is long enough, try to decrypt
    if len(value) > 20 and value.replace('+', '').replace('/', '').replace('=', '').isalnum():
        try:
            from .utils.config_encryption import ConfigEncryption
            encryptor = ConfigEncryption()
            return encryptor.decrypt_value(value)
        except Exception:
            # If decryption fails, assume it's not encrypted
            return value
    
    return value


def _validate_config(config: EdgeConfig) -> None:
    """Validate configuration values."""
    
    # Check required paths exist or can be created
    model_dir = Path(config.model.detection_model_path).parent
    model_dir.mkdir(parents=True, exist_ok=True)
    
    # Validate thresholds
    assert 0.0 <= config.recognition.similarity_threshold <= 1.0, \
        "Similarity threshold must be between 0.0 and 1.0"
    assert 0.0 <= config.model.liveness_threshold <= 1.0, \
        "Liveness threshold must be between 0.0 and 1.0"
    
    # Check input source
    has_rtsp = config.processing.rtsp_url is not None
    has_video_file = config.processing.video_file_path is not None
    has_camera = config.processing.camera_index is not None
    
    if not (has_rtsp or has_video_file or has_camera):
        logger.warning("No video source configured, will use camera index 0")
    
    # Validate device ID
    assert config.device.device_id, "Device ID cannot be empty"
    
    logger.debug("Configuration validation passed")


# Global configuration instance
config: Optional[EdgeConfig] = None


def get_config() -> EdgeConfig:
    """Get the global configuration instance."""
    global config
    if config is None:
        config = load_config()
    return config


def reload_config() -> EdgeConfig:
    """Reload configuration from environment."""
    global config
    config = load_config()
    return config
