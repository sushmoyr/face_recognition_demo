#!/usr/bin/env python3
"""
Demo script for testing edge service with sample video.

Creates a simple test video and processes it through the pipeline.
"""

import asyncio
import cv2
import numpy as np
from pathlib import Path
import sys

# Add edge package to path
sys.path.insert(0, str(Path(__file__).parent))

from edge.config import EdgeConfig
from edge.pipeline import FaceRecognitionPipeline


def create_demo_video(output_path: Path, duration_seconds: int = 10):
    """Create a simple demo video with moving rectangles (simulating faces)."""
    
    # Video parameters
    width, height = 640, 480
    fps = 30
    total_frames = duration_seconds * fps
    
    # Create video writer
    fourcc = cv2.VideoWriter_fourcc(*'mp4v')
    writer = cv2.VideoWriter(str(output_path), fourcc, fps, (width, height))
    
    print(f"Creating demo video: {output_path}")
    print(f"Duration: {duration_seconds}s, FPS: {fps}, Frames: {total_frames}")
    
    for frame_num in range(total_frames):
        # Create blank frame
        frame = np.zeros((height, width, 3), dtype=np.uint8)
        
        # Add some background noise
        noise = np.random.randint(0, 50, (height, width, 3), dtype=np.uint8)
        frame = cv2.add(frame, noise)
        
        # Add moving rectangles (simulating faces)
        t = frame_num / total_frames
        
        # Face 1: moves left to right
        x1 = int(50 + (width - 150) * t)
        y1 = 100
        cv2.rectangle(frame, (x1, y1), (x1 + 80, y1 + 100), (100, 150, 200), -1)
        cv2.putText(frame, "Person 1", (x1, y1-10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)
        
        # Face 2: moves up and down
        x2 = 300
        y2 = int(50 + 200 * abs(np.sin(t * np.pi * 2)))
        cv2.rectangle(frame, (x2, y2), (x2 + 80, y2 + 100), (200, 100, 150), -1)
        cv2.putText(frame, "Person 2", (x2, y2-10), cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)
        
        # Add frame info
        cv2.putText(frame, f"Frame: {frame_num}/{total_frames}", (10, 30), 
                   cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
        cv2.putText(frame, f"Time: {frame_num/fps:.1f}s", (10, 60), 
                   cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
        
        writer.write(frame)
        
        if frame_num % 30 == 0:  # Progress every second
            print(f"Progress: {frame_num/total_frames*100:.1f}%")
    
    writer.release()
    print(f"Demo video created: {output_path}")


async def test_pipeline_with_demo():
    """Test the pipeline with demo video."""
    
    # Create demo video
    demo_video_path = Path("demo_video.mp4")
    if not demo_video_path.exists():
        create_demo_video(demo_video_path, duration_seconds=30)
    
    # Configure edge service
    config = EdgeConfig()
    config.video_source = str(demo_video_path)
    config.device_code = "demo-edge"
    config.backend_url = "http://localhost:8080"
    config.display_stats = True
    
    # Override some settings for demo
    config.similarity_threshold = 0.5  # Lower threshold for demo
    config.recognition_interval = 2.0  # More frequent recognitions
    config.upload_enabled = False  # Disable MinIO for demo
    
    print("🚀 Starting edge service demo...")
    print(f"Video source: {demo_video_path}")
    print(f"Device code: {config.device_code}")
    print(f"Backend URL: {config.backend_url}")
    
    # Create and run pipeline
    pipeline = FaceRecognitionPipeline(config)
    
    try:
        # Initialize pipeline
        print("\n📋 Initializing pipeline components...")
        if not await pipeline.initialize():
            print("❌ Failed to initialize pipeline")
            return
        
        print("✅ Pipeline initialized successfully")
        
        # Start pipeline
        print("\n🎬 Starting video processing...")
        await pipeline.start()
        
        # Run for a limited time (demo mode)
        demo_duration = 60  # seconds
        start_time = asyncio.get_event_loop().time()
        
        while pipeline.running:
            current_time = asyncio.get_event_loop().time()
            
            if current_time - start_time > demo_duration:
                print(f"\n⏰ Demo completed after {demo_duration} seconds")
                break
            
            # Display stats every 5 seconds
            if int(current_time - start_time) % 5 == 0:
                stats = pipeline.get_stats()
                pipeline_stats = stats.get("pipeline", {})
                
                print(f"\n📊 Demo Progress ({int(current_time - start_time)}s):")
                print(f"  Frames Processed: {pipeline_stats.get('frames_processed', 0)}")
                print(f"  Faces Detected: {pipeline_stats.get('faces_detected', 0)}")
                print(f"  Processing FPS: {pipeline_stats.get('processing_fps', 0):.1f}")
            
            await asyncio.sleep(1)
        
        await pipeline.stop()
        
        # Final stats
        print("\n📈 Final Statistics:")
        final_stats = pipeline.get_stats()
        pipeline_stats = final_stats.get("pipeline", {})
        
        for metric, value in pipeline_stats.items():
            if isinstance(value, float):
                print(f"  {metric}: {value:.2f}")
            else:
                print(f"  {metric}: {value}")
        
        print("\n🎉 Demo completed successfully!")
        
    except KeyboardInterrupt:
        print("\n⏹️ Demo interrupted by user")
    except Exception as e:
        print(f"\n💥 Demo failed: {e}")
    finally:
        await pipeline.cleanup()
        print("🧹 Cleanup completed")


async def main():
    """Main demo entry point."""
    print("🎭 Face Recognition Edge Service Demo")
    print("=" * 50)
    
    # Check if backend is available
    import aiohttp
    
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get("http://localhost:8080/actuator/health", timeout=5) as response:
                if response.status == 200:
                    print("✅ Backend is available")
                else:
                    print("⚠️  Backend responded but may not be healthy")
    except:
        print("❌ Backend is not available - recognition events will be queued offline")
    
    print("\nStarting demo in 3 seconds...")
    await asyncio.sleep(3)
    
    await test_pipeline_with_demo()


if __name__ == "__main__":
    asyncio.run(main())
