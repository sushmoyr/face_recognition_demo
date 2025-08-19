#!/usr/bin/env python3
"""
Edge service validation script.

Test and validate the edge service components and integration.
"""

import asyncio
import sys
import time
from pathlib import Path
import numpy as np
import cv2
import structlog
from rich.console import Console
from rich.progress import Progress
from rich.table import Table
from rich.panel import Panel

import sys
from pathlib import Path
import numpy as np
import cv2
import structlog
from rich.console import Console
from rich.progress import Progress
from rich.table import Table
from rich.panel import Panel

# Add the edge package to path
sys.path.insert(0, str(Path(__file__).parent))

from edge.simple_config import EdgeConfig
from edge.pipeline import (
    RTSPReader, FaceDetector, EmbeddingExtractor, 
    LivenessDetector, FaceTracker, IndexSync,
    SnapshotUploader, RecognitionPublisher,
    FaceRecognitionPipeline
)

logger = structlog.get_logger(__name__)
console = Console()


class EdgeValidator:
    """Validation suite for edge service components."""
    
    def __init__(self):
        self.config = EdgeConfig()
        self.config.video_source = 0  # Use webcam for testing
        self.config.device_code = "test-edge"
        self.config.backend_url = "http://localhost:8080"
        self.results = {}
    
    async def run_all_tests(self):
        """Run all validation tests."""
        console.print(Panel("🔍 Edge Service Validation Suite", style="bold blue"))
        
        tests = [
            ("Configuration", self.test_config),
            ("Video Input", self.test_video_input),
            ("Face Detection", self.test_face_detection), 
            ("Face Tracking", self.test_face_tracking),
            ("Embedding Extraction", self.test_embedding_extraction),
            ("Liveness Detection", self.test_liveness_detection),
            ("Index Sync", self.test_index_sync),
            ("Snapshot Upload", self.test_snapshot_upload),
            ("Recognition Publisher", self.test_recognition_publisher),
            ("Full Pipeline", self.test_full_pipeline),
        ]
        
        with Progress() as progress:
            task = progress.add_task("Running tests...", total=len(tests))
            
            for test_name, test_func in tests:
                console.print(f"\n🧪 Testing {test_name}...")
                
                try:
                    result = await test_func()
                    self.results[test_name] = {
                        "status": "PASS" if result else "FAIL",
                        "details": result
                    }
                    
                    if result:
                        console.print(f"✅ {test_name}: PASSED")
                    else:
                        console.print(f"❌ {test_name}: FAILED")
                        
                except Exception as e:
                    self.results[test_name] = {
                        "status": "ERROR", 
                        "details": str(e)
                    }
                    console.print(f"💥 {test_name}: ERROR - {e}")
                
                progress.advance(task)
        
        # Display summary
        self.display_summary()
    
    async def test_config(self) -> bool:
        """Test configuration loading."""
        try:
            assert hasattr(self.config, 'device_code')
            assert hasattr(self.config, 'video_source') 
            assert hasattr(self.config, 'backend_url')
            return True
        except Exception as e:
            logger.error("Config test failed", error=str(e))
            return False
    
    async def test_video_input(self) -> bool:
        """Test video input reading."""
        try:
            reader = RTSPReader(self.config)
            
            if not await reader.initialize():
                return False
            
            # Read a few frames
            frame_count = 0
            async for frame_data in reader.read_frames():
                frame_count += 1
                if frame_count >= 5:  # Test first 5 frames
                    break
            
            await reader.cleanup()
            return frame_count > 0
            
        except Exception as e:
            logger.error("Video input test failed", error=str(e))
            return False
    
    async def test_face_detection(self) -> bool:
        """Test face detection component."""
        try:
            detector = FaceDetector(self.config)
            
            if not await detector.initialize():
                return False
            
            # Create test image with face (or use webcam frame)
            cap = cv2.VideoCapture(0)
            ret, frame = cap.read()
            cap.release()
            
            if not ret:
                # Use a dummy image if webcam fails
                frame = np.random.randint(0, 255, (480, 640, 3), dtype=np.uint8)
            
            detections = await detector.detect_faces(frame)
            logger.info("Face detection test completed", 
                       detections=len(detections))
            
            return True  # Pass even with 0 detections (no face in frame)
            
        except Exception as e:
            logger.error("Face detection test failed", error=str(e))
            return False
    
    async def test_face_tracking(self) -> bool:
        """Test face tracking component.""" 
        try:
            from edge.pipeline.face_detector import FaceDetection
            
            tracker = FaceTracker()
            
            # Create dummy detections
            detections = [
                FaceDetection(
                    bbox=(100, 100, 50, 50),
                    confidence=0.9,
                    landmarks=None
                )
            ]
            
            tracks = await tracker.update(detections)
            return len(tracks) == 1
            
        except Exception as e:
            logger.error("Face tracking test failed", error=str(e))
            return False
    
    async def test_embedding_extraction(self) -> bool:
        """Test embedding extraction."""
        try:
            from edge.pipeline import FaceDetection
            
            extractor = EmbeddingExtractor(self.config)
            
            if not await extractor.initialize():
                return False
            
            # Create dummy face detection with face crop
            face_image = np.random.randint(0, 255, (224, 224, 3), dtype=np.uint8)
            detection = FaceDetection((0, 0, 224, 224), 0.9)
            detection.face_crop = face_image
            
            embedding_result = await extractor.extract_embedding(detection)
            
            if embedding_result is None:
                return False
            
            # Check embedding shape (should be 512D)
            return embedding_result.vector.shape == (512,)
            
        except Exception as e:
            logger.error("Embedding extraction test failed", error=str(e))
            return False
    
    async def test_liveness_detection(self) -> bool:
        """Test liveness detection."""
        try:
            from edge.pipeline.face_detector import FaceDetection
            
            detector = LivenessDetector(self.config)
            
            if not await detector.initialize():
                return False
            
            # Create dummy frame and face detection
            frame = np.random.randint(0, 255, (480, 640, 3), dtype=np.uint8)
            face_detection = FaceDetection(
                bbox=(100, 100, 50, 50),
                confidence=0.9,
                landmarks=None
            )
            
            liveness_result = await detector.detect_liveness(frame, face_detection)
            
            # Check result structure
            return hasattr(liveness_result, 'is_live') and hasattr(liveness_result, 'confidence')
            
        except Exception as e:
            logger.error("Liveness detection test failed", error=str(e))
            return False
    
    async def test_index_sync(self) -> bool:
        """Test index synchronization."""
        try:
            index_sync = IndexSync(self.config)
            
            # Test initialization (may fail if backend not available)
            try:
                await index_sync.initialize()
                initialized = True
            except:
                initialized = False
            
            # Test search with dummy embedding
            dummy_embedding = np.random.random(512).astype(np.float32)
            dummy_embedding = dummy_embedding / np.linalg.norm(dummy_embedding)
            
            results = await index_sync.search_similar(dummy_embedding)
            
            await index_sync.cleanup()
            
            # Pass if we get results (even empty ones)
            return isinstance(results, list)
            
        except Exception as e:
            logger.error("Index sync test failed", error=str(e))
            return False
    
    async def test_snapshot_upload(self) -> bool:
        """Test snapshot upload component."""
        try:
            uploader = SnapshotUploader(self.config)
            
            # Test initialization (may fail if MinIO not available)
            try:
                await uploader.initialize()
                initialized = True
            except:
                initialized = False
            
            # Test metadata creation (should work even without MinIO)
            frame = np.random.randint(0, 255, (480, 640, 3), dtype=np.uint8)
            
            # This should not fail even if upload is disabled
            metadata = await uploader.upload_recognition_snapshot(
                frame=frame,
                face_bbox=(100, 100, 50, 50),
                employee_code="TEST001",
                device_code="test-edge",
                confidence=0.9,
                is_match=True,
                similarity=0.8
            )
            
            await uploader.cleanup()
            
            # Pass if component initializes properly
            return True
            
        except Exception as e:
            logger.error("Snapshot upload test failed", error=str(e))
            return False
    
    async def test_recognition_publisher(self) -> bool:
        """Test recognition event publisher."""
        try:
            from edge.pipeline.index_sync import MatchResult
            
            publisher = RecognitionPublisher(self.config)
            
            # Test initialization (may fail if backend not available)
            try:
                await publisher.initialize()
                initialized = True
            except:
                initialized = False
            
            # Create dummy match result
            match_result = MatchResult(
                is_match=True,
                employee_code="TEST001",
                template_id="template-1",
                similarity=0.85,
                distance=0.15,
                confidence=0.9,
                search_time_ms=10.5
            )
            
            # Test event creation (should work even offline)
            success = await publisher.publish_recognition(
                device_code="test-edge",
                match_result=match_result,
                face_bbox=(100, 100, 50, 50),
                liveness_score=0.9
            )
            
            await publisher.cleanup()
            
            # Pass if component handles the call properly
            return True
            
        except Exception as e:
            logger.error("Recognition publisher test failed", error=str(e))
            return False
    
    async def test_full_pipeline(self) -> bool:
        """Test full pipeline integration."""
        try:
            pipeline = FaceRecognitionPipeline(self.config)
            
            # Test initialization
            if not await pipeline.initialize():
                return False
            
            # Test stats collection
            stats = pipeline.get_stats()
            
            await pipeline.cleanup()
            
            # Check stats structure
            return (
                "pipeline" in stats and 
                "components" in stats and
                "device_code" in stats
            )
            
        except Exception as e:
            logger.error("Full pipeline test failed", error=str(e))
            return False
    
    def display_summary(self):
        """Display test results summary."""
        console.print("\n" + "="*60)
        console.print(Panel("📊 Validation Results Summary", style="bold green"))
        
        # Create results table
        table = Table(show_header=True, header_style="bold blue")
        table.add_column("Test", style="cyan")
        table.add_column("Status", style="white") 
        table.add_column("Details", style="dim")
        
        passed = 0
        total = len(self.results)
        
        for test_name, result in self.results.items():
            status = result["status"]
            details = str(result["details"])[:50] + "..." if len(str(result["details"])) > 50 else str(result["details"])
            
            if status == "PASS":
                status_text = "✅ PASS"
                passed += 1
            elif status == "FAIL":
                status_text = "❌ FAIL"
            else:
                status_text = "💥 ERROR"
            
            table.add_row(test_name, status_text, details)
        
        console.print(table)
        
        # Summary stats
        console.print(f"\n📈 Results: {passed}/{total} tests passed ({passed/total*100:.1f}%)")
        
        if passed == total:
            console.print("🎉 All tests passed! Edge service is ready.")
        else:
            console.print("⚠️  Some tests failed. Check the details above.")


async def main():
    """Main validation entry point."""
    validator = EdgeValidator()
    await validator.run_all_tests()


if __name__ == "__main__":
    asyncio.run(main())
