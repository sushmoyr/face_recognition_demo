"""
Face embedding extraction component.

Converts face crops to 512-dimensional normalized embeddings for similarity matching.
Supports multiple embedding models and provides consistent vector output.
"""

import numpy as np
import cv2
import asyncio
from typing import Optional, List
import structlog
from pathlib import Path

from ..config import EdgeConfig
from .face_detector import FaceDetection

logger = structlog.get_logger(__name__)


class FaceEmbedding:
    """Represents a face embedding with metadata."""
    
    def __init__(self, vector: np.ndarray, confidence: float = 1.0, quality_score: float = 1.0):
        # Ensure vector is normalized and 512-dimensional
        self.vector = self._normalize_vector(vector)
        self.confidence = confidence
        self.quality_score = quality_score
        self.dimension = len(self.vector)
    
    def _normalize_vector(self, vector: np.ndarray) -> np.ndarray:
        """Normalize vector to unit length."""
        if len(vector.shape) > 1:
            vector = vector.flatten()
        
        # Pad or truncate to 512 dimensions
        if len(vector) < 512:
            padded = np.zeros(512, dtype=np.float32)
            padded[:len(vector)] = vector
            vector = padded
        elif len(vector) > 512:
            vector = vector[:512]
        
        # Normalize to unit length
        norm = np.linalg.norm(vector)
        if norm > 0:
            vector = vector / norm
        
        return vector.astype(np.float32)
    
    def to_dict(self) -> dict:
        return {
            "vector": self.vector.tolist(),
            "confidence": self.confidence,
            "quality_score": self.quality_score,
            "dimension": self.dimension
        }


class EmbeddingExtractor:
    """
    Face embedding extractor with multiple model support.
    
    Supports:
    - Stub embeddings (random normalized vectors)
    - OpenCV DNN embeddings 
    - ONNX model embeddings
    - Pre-trained models (FaceNet, ArcFace, etc.)
    """
    
    def __init__(self, config: EdgeConfig):
        self.config = config
        self.model = None
        self.embedding_method = getattr(config, 'embedding_method', 'stub')
        self.embedding_size = 512
        
        # Performance tracking
        self.embeddings_extracted = 0
        self.total_extraction_time = 0
    
    async def initialize(self) -> bool:
        """Initialize the embedding extraction model."""
        try:
            logger.info("Initializing embedding extractor", method=self.embedding_method)
            
            if self.embedding_method == "stub":
                await self._init_stub_extractor()
            elif self.embedding_method == "opencv":
                await self._init_opencv_extractor()  
            elif self.embedding_method == "onnx":
                await self._init_onnx_extractor()
            else:
                logger.error("Unknown embedding method", method=self.embedding_method)
                return False
            
            logger.info("Embedding extractor initialized successfully")
            return True
            
        except Exception as e:
            logger.error("Failed to initialize embedding extractor", error=str(e))
            return False
    
    async def _init_stub_extractor(self):
        """Initialize stub extractor that generates deterministic embeddings."""
        self.model = "stub"
        logger.info("Using stub embedding extractor")
    
    async def _init_opencv_extractor(self):
        """Initialize OpenCV-based embedding extractor."""
        # Stub - would load OpenCV DNN model here
        logger.warning("OpenCV embeddings not implemented yet, falling back to stub")
        await self._init_stub_extractor()
    
    async def _init_onnx_extractor(self):
        """Initialize ONNX embedding extractor."""
        # Stub - would load ONNX model here
        logger.warning("ONNX embeddings not implemented yet, falling back to stub")
        await self._init_stub_extractor()
    
    async def extract_embedding(self, face_detection: FaceDetection) -> Optional[FaceEmbedding]:
        """
        Extract embedding from face detection.
        
        Args:
            face_detection: Face detection with cropped face image
            
        Returns:
            Face embedding or None if extraction failed
        """
        if face_detection.face_crop is None:
            logger.warning("No face crop available for embedding extraction")
            return None
        
        try:
            import time
            start_time = time.time()
            
            if self.embedding_method == "stub":
                embedding = await self._extract_stub_embedding(face_detection)
            elif self.embedding_method == "opencv":
                embedding = await self._extract_opencv_embedding(face_detection)
            elif self.embedding_method == "onnx":
                embedding = await self._extract_onnx_embedding(face_detection)
            else:
                return None
            
            extraction_time = time.time() - start_time
            self.total_extraction_time += extraction_time
            self.embeddings_extracted += 1
            
            logger.debug("Embedding extracted", 
                        dimension=embedding.dimension,
                        confidence=embedding.confidence,
                        extraction_time=extraction_time)
            
            return embedding
            
        except Exception as e:
            logger.error("Error extracting embedding", error=str(e))
            return None
    
    async def extract_batch_embeddings(self, face_detections: List[FaceDetection]) -> List[Optional[FaceEmbedding]]:
        """
        Extract embeddings for multiple faces.
        
        Args:
            face_detections: List of face detections
            
        Returns:
            List of embeddings (same order as input)
        """
        embeddings = []
        for detection in face_detections:
            embedding = await self.extract_embedding(detection)
            embeddings.append(embedding)
        
        return embeddings
    
    async def _extract_stub_embedding(self, face_detection: FaceDetection) -> FaceEmbedding:
        """Generate stub embedding based on face characteristics."""
        face_crop = face_detection.face_crop
        
        # Create a deterministic embedding based on image statistics
        # This allows for consistent matching during testing
        mean_color = np.mean(face_crop.flatten())
        std_color = np.std(face_crop.flatten())
        
        # Generate a deterministic but pseudo-random vector
        seed = int((mean_color * 1000 + std_color * 100) % 2147483647)
        np.random.seed(seed)
        
        # Create a random vector and normalize it
        vector = np.random.normal(0, 1, 512).astype(np.float32)
        
        # Add some face-specific characteristics
        vector[0] = mean_color / 255.0  # Brightness indicator
        vector[1] = std_color / 255.0   # Contrast indicator
        vector[2] = face_detection.confidence  # Detection confidence
        
        quality_score = min(face_detection.confidence, 1.0)
        
        return FaceEmbedding(vector, confidence=0.8, quality_score=quality_score)
    
    async def _extract_opencv_embedding(self, face_detection: FaceDetection) -> FaceEmbedding:
        """Extract embedding using OpenCV DNN model."""
        # Stub implementation
        return await self._extract_stub_embedding(face_detection)
    
    async def _extract_onnx_embedding(self, face_detection: FaceDetection) -> FaceEmbedding:
        """Extract embedding using ONNX model.""" 
        # Stub implementation
        return await self._extract_stub_embedding(face_detection)
    
    def preprocess_face(self, face_crop: np.ndarray, target_size: tuple = (112, 112)) -> np.ndarray:
        """
        Preprocess face crop for embedding extraction.
        
        Args:
            face_crop: Face image crop
            target_size: Target size for the model
            
        Returns:
            Preprocessed face image
        """
        # Resize face to target size
        face_resized = cv2.resize(face_crop, target_size)
        
        # Convert to RGB if needed
        if len(face_resized.shape) == 3 and face_resized.shape[2] == 3:
            face_resized = cv2.cvtColor(face_resized, cv2.COLOR_BGR2RGB)
        
        # Normalize to [0, 1]
        face_normalized = face_resized.astype(np.float32) / 255.0
        
        # Add batch dimension
        face_batch = np.expand_dims(face_normalized, axis=0)
        
        return face_batch
    
    def get_stats(self) -> dict:
        """Get embedding extraction performance statistics."""
        avg_extraction_time = (self.total_extraction_time / self.embeddings_extracted 
                              if self.embeddings_extracted > 0 else 0)
        
        return {
            "embedding_method": self.embedding_method,
            "embeddings_extracted": self.embeddings_extracted,
            "total_extraction_time": self.total_extraction_time,
            "average_extraction_time": avg_extraction_time,
            "embedding_size": self.embedding_size
        }
