# Face Recognition Edge Service

A complete face recognition edge service that processes video streams in real-time to detect, track, and recognize faces.

## Features

### Core Pipeline Components
- **Video Stream Processing**: RTSP/file/webcam input with automatic reconnection
- **Face Detection**: Multiple backends (Haar cascades, DNN, ONNX) with confidence filtering
- **Face Tracking**: Centroid-based tracking across frames for temporal consistency
- **Liveness Detection**: Anti-spoofing with motion, blink, and texture analysis
- **Face Embedding**: 512D normalized vectors with stub/OpenCV/ONNX model support
- **Similarity Matching**: Fast FAISS-based search against enrolled face templates
- **Event Publishing**: Async batch publishing to backend API with offline queuing
- **Snapshot Storage**: MinIO integration for face images with organized folder structure

### Key Capabilities
- Real-time processing with async/await patterns
- Automatic backend synchronization for face templates
- Resilient design with retry logic and offline operation
- Comprehensive performance monitoring and live statistics
- Configurable recognition intervals and thresholds
- Event deduplication and idempotent processing

## Quick Start

### Prerequisites
- Python 3.11+
- OpenCV system libraries
- FAISS for similarity search
- MinIO for object storage (optional)
- Backend API running (from milestone 3)

### Installation

```bash
# Clone the repository
git clone <repo-url>
cd face-recognition-demo/edge

# Install dependencies
pip install -r requirements.txt

# Install in development mode
pip install -e .
```

### Basic Usage

```bash
# Run with webcam (camera index 0)
python -m edge.main --source 0

# Run with video file
python -m edge.main --source demo.mp4

# Run with RTSP stream
python -m edge.main --source "rtsp://username:password@ip:port/stream"

# Run with custom backend
python -m edge.main --backend-url "http://your-backend:8080"
```

### Configuration

The edge service can be configured via environment variables or command line arguments:

```bash
# Environment variables
export DEVICE_CODE="edge-001"
export BACKEND_URL="http://localhost:8080"
export VIDEO_SOURCE="rtsp://admin:pass@192.168.1.100/stream"
export SIMILARITY_THRESHOLD="0.7"
export RECOGNITION_INTERVAL="5.0"

# Command line arguments
python -m edge.main \
    --source "rtsp://admin:pass@192.168.1.100/stream" \
    --device-code "lobby-camera" \
    --backend-url "http://localhost:8080" \
    --debug
```

## Architecture

### Pipeline Flow

```
Video Source → Face Detection → Face Tracking → Liveness Check
     ↓              ↓              ↓              ↓
Snapshot Upload ← Recognition ← Embedding ← Face Validation
     ↓              ↓              ↓              ↓
MinIO Storage   Backend API   FAISS Index   Live Face Crop
```

### Component Overview

1. **RTSPReader**: Handles video input from various sources
2. **FaceDetector**: Detects faces using configurable models
3. **FaceTracker**: Maintains face identities across frames
4. **LivenessDetector**: Validates face liveness to prevent spoofing
5. **EmbeddingExtractor**: Generates face embeddings for matching
6. **IndexSync**: Manages FAISS index and backend synchronization
7. **SnapshotUploader**: Uploads face images to MinIO storage
8. **RecognitionPublisher**: Publishes events to backend API
9. **FaceRecognitionPipeline**: Orchestrates all components

## Configuration Options

### Core Settings
- `device_code`: Unique identifier for this edge device
- `video_source`: Video input source (RTSP URL, file path, camera index)
- `backend_url`: Backend API base URL
- `similarity_threshold`: Face matching threshold (0.0-1.0)
- `recognition_interval`: Minimum seconds between recognitions per face

### Performance Tuning
- `target_fps`: Target processing frame rate
- `face_crop_size`: Size for face crop images (pixels)
- `max_disappeared`: Frames before removing lost tracks
- `batch_size`: Recognition event batch size
- `queue_size`: Internal queue sizes for components

### Storage Settings
- `minio_endpoint`: MinIO server endpoint
- `minio_access_key`: MinIO access key
- `minio_secret_key`: MinIO secret key
- `minio_bucket`: Storage bucket name
- `upload_enabled`: Enable/disable snapshot uploads

## API Integration

The edge service integrates with the backend API created in Milestone 3:

### Face Templates Synchronization
- `GET /api/face-templates`: Fetch all enrolled face templates
- Automatic periodic sync every 5 minutes
- Cached FAISS index for fast similarity search

### Recognition Event Publishing
- `POST /api/recognitions`: Publish recognition events
- Batch processing for efficiency
- Retry logic with exponential backoff
- Offline queuing for network failures

### Event Payload Structure
```json
{
  "events": [
    {
      "event_id": "uuid",
      "device_code": "edge-001", 
      "timestamp": "2024-01-15T10:30:00Z",
      "employee_code": "E1001",
      "is_match": true,
      "confidence": 0.95,
      "similarity": 0.87,
      "liveness_score": 0.92,
      "face_bbox": [100, 150, 200, 250],
      "template_id": "template-uuid",
      "snapshot_path": "2024/01/15/edge-001/E1001/event-uuid/full_frame.jpg",
      "metadata": {
        "track_id": "track-uuid",
        "detection_confidence": 0.93,
        "liveness_method": "motion"
      }
    }
  ],
  "batch_id": "batch-uuid",
  "timestamp": "2024-01-15T10:30:00Z",
  "device_code": "edge-001"
}
```

## Testing

### Unit Tests
```bash
# Run all tests
pytest

# Run with coverage
pytest --cov=edge

# Run specific test module
pytest tests/test_pipeline.py
```

### Integration Tests
```bash
# Test with demo video
python -m edge.main --source ./tests/assets/demo.mp4 --debug

# Test face detection only
python -c "
import asyncio
import cv2
from edge.pipeline import FaceDetector
from edge.config import EdgeConfig

async def test_detection():
    detector = FaceDetector(EdgeConfig())
    await detector.initialize()
    
    cap = cv2.VideoCapture(0)
    ret, frame = cap.read()
    
    if ret:
        detections = await detector.detect_faces(frame)
        print(f'Detected {len(detections)} faces')
        
        for detection in detections:
            print(f'Face at {detection.bbox} with confidence {detection.confidence}')
    
    cap.release()

asyncio.run(test_detection())
"
```

### Performance Benchmarks
```bash
# Benchmark face detection
python scripts/benchmark_detection.py

# Benchmark full pipeline  
python scripts/benchmark_pipeline.py

# Memory profiling
python -m memory_profiler edge/main.py --source demo.mp4
```

## Monitoring

### Live Statistics Display
The edge service provides real-time statistics via rich console interface:

- Pipeline metrics (frames processed, faces detected, recognition rate)
- Component performance (FPS, processing times, queue sizes)
- Network status (backend connectivity, upload success rates)
- Resource usage (CPU, memory, GPU if enabled)

### Log Output
Structured logging with multiple levels:
- `INFO`: General operation info
- `DEBUG`: Detailed processing information  
- `WARNING`: Non-critical issues (network timeouts, etc.)
- `ERROR`: Critical errors requiring attention

### Performance Metrics
Key metrics tracked for each component:
- Processing times and throughput
- Success/failure rates 
- Queue depths and backlogs
- Resource utilization

## Deployment

### Docker Deployment
```bash
# Build image
docker build -t face-recognition-edge .

# Run container
docker run -it \
  -e DEVICE_CODE=edge-001 \
  -e BACKEND_URL=http://backend:8080 \
  -e VIDEO_SOURCE="rtsp://camera:554/stream" \
  face-recognition-edge
```

### Production Configuration
- Use environment variables for sensitive config
- Enable proper logging levels
- Configure resource limits
- Set up health check endpoints
- Use process managers (systemd, supervisor)

### Scaling Considerations
- Multiple edge devices can run independently  
- Each device should have a unique `device_code`
- Backend handles deduplication across devices
- MinIO provides distributed object storage
- FAISS index synced from centralized backend

## Troubleshooting

### Common Issues

**Video Source Connection**
```bash
# Test video source
python -c "
import cv2
cap = cv2.VideoCapture('your-source')
print(f'Video source connected: {cap.isOpened()}')
cap.release()
"
```

**Backend API Connection** 
```bash
# Test backend connectivity
curl http://localhost:8080/actuator/health
```

**MinIO Storage**
```bash
# Test MinIO connectivity
python -c "
from minio import Minio
client = Minio('localhost:9000', 'minioadmin', 'minioadmin', secure=False)
print(f'MinIO connected: {client.bucket_exists('face-recognition')}')
"
```

**FAISS Index Issues**
```bash
# Check FAISS installation
python -c "import faiss; print(f'FAISS version: {faiss.__version__}')"
```

### Debug Mode
Enable comprehensive debug logging:
```bash
python -m edge.main --debug --source demo.mp4
```

### Performance Issues
- Reduce target FPS for slower hardware
- Decrease face crop size to reduce processing
- Disable snapshot uploads if storage is slow
- Use CPU-only mode if GPU causes issues

## Development

### Code Structure
```
edge/
├── edge/
│   ├── config.py           # Configuration management
│   ├── main.py            # Main entry point
│   └── pipeline/          # Pipeline components
│       ├── __init__.py
│       ├── rtsp_reader.py         # Video input
│       ├── face_detector.py       # Face detection  
│       ├── face_tracker.py        # Face tracking
│       ├── liveness_detector.py   # Liveness detection
│       ├── embedding_extractor.py # Face embeddings
│       ├── index_sync.py          # FAISS indexing
│       ├── snapshot_uploader.py   # MinIO uploads
│       ├── recognition_publisher.py # API publishing
│       └── face_recognition_pipeline.py # Main orchestrator
├── tests/                 # Test suite
├── requirements.txt       # Dependencies
├── Dockerfile            # Container image
└── README.md            # This file
```

### Contributing
1. Follow async/await patterns for all I/O operations
2. Add structured logging with appropriate levels
3. Include comprehensive error handling
4. Write tests for new components
5. Update documentation for API changes
6. Use type hints for better code clarity

### Adding New Components
1. Create component class with async `initialize()` method
2. Implement proper cleanup in async `cleanup()` method  
3. Add performance statistics via `get_stats()` method
4. Include component in pipeline orchestrator
5. Add configuration options to `EdgeConfig`
6. Write unit tests for component logic

## License

[Add appropriate license information]
