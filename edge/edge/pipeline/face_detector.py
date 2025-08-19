"""
Face detection component using OpenCV or ONNX models.

Provides face detection with bounding boxes and confidence scores.
Can use different detection models based on configuration.
"""

import cv2
import numpy as np
import asyncio
from typing import List, Tuple, Optional
import structlog
from pathlib import Path

from ..config import EdgeConfig

logger = structlog.get_logger(__name__)


class FaceDetection:
    """Represents a detected face."""
    
    def __init__(self, bbox: Tuple[int, int, int, int], confidence: float, landmarks: Optional[np.ndarray] = None):
        self.bbox = bbox  # (x, y, width, height)
        self.confidence = confidence
        self.landmarks = landmarks
        self.face_crop: Optional[np.ndarray] = None
    
    @property
    def x(self) -> int:
        return self.bbox[0]
    
    @property 
    def y(self) -> int:
        return self.bbox[1]
        
    @property
    def width(self) -> int:
        return self.bbox[2]
        
    @property
    def height(self) -> int:
        return self.bbox[3]
    
    @property
    def area(self) -> int:
        return self.width * self.height
    
    def to_dict(self) -> dict:
        return {
            "bbox": self.bbox,
            "confidence": self.confidence,
            "area": self.area
        }


class FaceDetector:
    """
    Face detector with multiple backend support.
    
    Supports:
    - OpenCV Haar cascades (fast, less accurate)
    - OpenCV DNN face detector (good balance)
    - ONNX models (configurable, best accuracy)
    """
    
    def __init__(self, config: EdgeConfig):
        self.config = config
        self.detector = None
        self.detection_method = config.detection_method
        self.confidence_threshold = config.detection_confidence_threshold
        self.min_face_size = getattr(config, 'min_face_size', (30, 30))
        self.max_face_size = getattr(config, 'max_face_size', (300, 300))
        
        # Performance tracking
        self.detections_count = 0
        self.total_faces_detected = 0
    
    async def initialize(self) -> bool:
        """Initialize the face detection model."""
        try:
            logger.info("Initializing face detector", method=self.detection_method)
            
            if self.detection_method == "haar":
                await self._init_haar_detector()
            elif self.detection_method == "dnn":
                await self._init_dnn_detector()
            elif self.detection_method == "onnx":
                await self._init_onnx_detector()
            else:
                logger.error("Unknown detection method", method=self.detection_method)
                return False
            
            logger.info("Face detector initialized successfully")
            return True
            
        except Exception as e:
            logger.error("Failed to initialize face detector", error=str(e))
            return False
    
    async def _init_haar_detector(self):
        """Initialize OpenCV Haar cascade detector."""
        cascade_path = cv2.data.haarcascades + 'haarcascade_frontalface_default.xml'
        self.detector = cv2.CascadeClassifier(cascade_path)
        
        if self.detector.empty():
            raise RuntimeError("Failed to load Haar cascade classifier")
    
    async def _init_dnn_detector(self):
        """Initialize OpenCV DNN face detector."""
        # Use OpenCV's pre-trained DNN face detector
        model_file = Path(__file__).parent.parent / "models" / "opencv_face_detector_uint8.pb"
        config_file = Path(__file__).parent.parent / "models" / "opencv_face_detector.pbtxt"
        
        # If model files don't exist, create stub detector
        if not model_file.exists() or not config_file.exists():
            logger.warning("DNN model files not found, falling back to Haar detector")
            await self._init_haar_detector()
            return
        
        self.detector = cv2.dnn.readNetFromTensorflow(str(model_file), str(config_file))
        
    async def _init_onnx_detector(self):
        """Initialize ONNX face detector."""
        # Stub implementation - would load ONNX model here
        logger.warning("ONNX detector not implemented yet, falling back to Haar")
        await self._init_haar_detector()
    
    async def detect_faces(self, frame: np.ndarray) -> List[FaceDetection]:
        """
        Detect faces in the given frame.
        
        Args:
            frame: Input image frame
            
        Returns:
            List of detected faces with bounding boxes and confidence
        """
        if self.detector is None:
            return []
        
        try:
            self.detections_count += 1
            
            if self.detection_method == "haar":
                faces = await self._detect_haar(frame)
            elif self.detection_method == "dnn":
                faces = await self._detect_dnn(frame)
            elif self.detection_method == "onnx":
                faces = await self._detect_onnx(frame)
            else:
                faces = []
            
            # Filter faces by size
            filtered_faces = []
            for face in faces:
                if (self.min_face_size[0] <= face.width <= self.max_face_size[0] and 
                    self.min_face_size[1] <= face.height <= self.max_face_size[1]):
                    
                    # Crop face from frame
                    face.face_crop = frame[face.y:face.y+face.height, face.x:face.x+face.width]
                    filtered_faces.append(face)
            
            self.total_faces_detected += len(filtered_faces)
            
            if filtered_faces:
                logger.debug("Faces detected", count=len(filtered_faces))
            
            return filtered_faces
            
        except Exception as e:
            logger.error("Error detecting faces", error=str(e))
            return []
    
    async def _detect_haar(self, frame: np.ndarray) -> List[FaceDetection]:
        """Detect faces using Haar cascade."""
        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        
        # Run detection
        faces = self.detector.detectMultiScale(
            gray,
            scaleFactor=1.1,
            minNeighbors=5,
            minSize=self.min_face_size,
            maxSize=self.max_face_size
        )
        
        detections = []
        for (x, y, w, h) in faces:
            # Haar doesn't provide confidence, use a default value
            detection = FaceDetection((x, y, w, h), confidence=0.8)
            detections.append(detection)
        
        return detections
    
    async def _detect_dnn(self, frame: np.ndarray) -> List[FaceDetection]:
        """Detect faces using DNN model."""
        if isinstance(self.detector, cv2.CascadeClassifier):
            # Fallback to Haar if DNN model wasn't loaded
            return await self._detect_haar(frame)
        
        h, w = frame.shape[:2]
        
        # Create blob from frame
        blob = cv2.dnn.blobFromImage(frame, 1.0, (300, 300), [104, 117, 123])
        self.detector.setInput(blob)
        detections = self.detector.forward()
        
        faces = []
        for i in range(detections.shape[2]):
            confidence = detections[0, 0, i, 2]
            
            if confidence > self.confidence_threshold:
                x1 = int(detections[0, 0, i, 3] * w)
                y1 = int(detections[0, 0, i, 4] * h)
                x2 = int(detections[0, 0, i, 5] * w)
                y2 = int(detections[0, 0, i, 6] * h)
                
                face = FaceDetection((x1, y1, x2-x1, y2-y1), confidence)
                faces.append(face)
        
        return faces
    
    async def _detect_onnx(self, frame: np.ndarray) -> List[FaceDetection]:
        """Detect faces using ONNX model."""
        # Stub implementation - would use ONNX runtime here
        return await self._detect_haar(frame)
    
    def get_stats(self) -> dict:
        """Get detection performance statistics."""
        avg_faces_per_frame = (self.total_faces_detected / self.detections_count 
                              if self.detections_count > 0 else 0)
        
        return {
            "detection_method": self.detection_method,
            "detections_count": self.detections_count,
            "total_faces_detected": self.total_faces_detected,
            "average_faces_per_frame": avg_faces_per_frame,
            "confidence_threshold": self.confidence_threshold
        }
