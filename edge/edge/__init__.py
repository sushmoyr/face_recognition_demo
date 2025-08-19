"""
Face Recognition Edge Service

A Python service for real-time face detection, recognition, and liveness detection
from RTSP streams or video files. Processes video frames, extracts face embeddings,
performs similarity matching against a local index, and submits recognition events
to the backend attendance system.

Key Features:
- RTSP stream processing with OpenCV
- Face detection using RetinaFace or YOLOv8
- Face alignment and embedding extraction
- Passive liveness detection
- Multi-object tracking with ByteTrack/DeepSORT
- FAISS-based similarity search
- Automatic deduplication with configurable cooldown
- MinIO integration for snapshot storage
- Prometheus metrics and structured logging
- Offline queue with automatic retry
- GPU acceleration support

Architecture:
- Modular pipeline design with pluggable components
- Async processing for improved performance
- Configuration-driven model loading
- Comprehensive error handling and monitoring
"""

__version__ = "1.0.0"
__author__ = "Face Recognition Attendance System"
