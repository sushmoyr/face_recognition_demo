"""
Pipeline components for face recognition edge service.
"""

from .rtsp_reader import RTSPReader, FrameData
from .face_detector import FaceDetector, FaceDetection
from .embedding_extractor import EmbeddingExtractor, FaceEmbedding
from .liveness_detector import LivenessDetector, LivenessResult
from .face_tracker import FaceTracker, FaceTrack
from .index_sync import IndexSync, FaceTemplate, MatchResult
from .snapshot_uploader import SnapshotUploader, SnapshotMetadata
from .recognition_publisher import RecognitionPublisher, RecognitionEvent
from .face_recognition_pipeline import FaceRecognitionPipeline, PipelineStats

__all__ = [
    "RTSPReader",
    "FrameData",
    "FaceDetector", 
    "FaceDetection",
    "EmbeddingExtractor",
    "FaceEmbedding",
    "LivenessDetector",
    "LivenessResult",
    "FaceTracker",
    "FaceTrack",
    "IndexSync",
    "FaceTemplate",
    "MatchResult",
    "SnapshotUploader",
    "SnapshotMetadata",
    "RecognitionPublisher",
    "RecognitionEvent",
    "FaceRecognitionPipeline",
    "PipelineStats",
]
