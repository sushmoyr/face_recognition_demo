#!/usr/bin/env python3
"""
Demo script for face recognition pipeline with file input.
This shows the edge service processing a demo video file.
"""

import asyncio
import time
import logging
from pathlib import Path

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
)

from edge.simple_config import SimpleEdgeConfig
from edge.pipeline import FaceRecognitionPipeline


async def main():
    """Run the face recognition pipeline demo."""
    
    # Create demo video file (placeholder)
    demo_video_path = Path("demo_video.mp4") 
    
    # Configuration for file input
    config = SimpleEdgeConfig(
        device_code="demo-edge",
        video_source=str(demo_video_path) if demo_video_path.exists() else "0",  # fallback to webcam
        target_fps=5.0,  # Lower FPS for demo
        backend_url="http://localhost:8080",
        upload_enabled=False,  # Disable MinIO for demo
        debug=True
    )
    
    print("🚀 Face Recognition Edge Service Demo")
    print(f"📹 Video source: {config.video_source}")
    print(f"🌐 Backend URL: {config.backend_url}")
    print(f"📊 Target FPS: {config.target_fps}")
    print()
    
    # Create and initialize pipeline
    pipeline = FaceRecognitionPipeline(config)
    
    try:
        print("🔧 Initializing pipeline...")
        if not await pipeline.initialize():
            print("❌ Failed to initialize pipeline")
            return
        
        print("✅ Pipeline initialized successfully!")
        print()
        
        print("🎬 Starting face recognition processing...")
        print("   (This will run for 10 seconds as a demo)")
        
        # Start the pipeline
        await pipeline.start()
        
        # Run for 10 seconds
        start_time = time.time()
        while time.time() - start_time < 10:
            await asyncio.sleep(1)
            
            # Print status every 2 seconds  
            if int(time.time() - start_time) % 2 == 0:
                print(f"⏱️  Running for {int(time.time() - start_time)} seconds...")
        
        print("⏹️  Demo completed!")
        
    except Exception as e:
        print(f"❌ Error during demo: {e}")
        
    finally:
        print("🧹 Cleaning up...")
        await pipeline.stop()
        print("✅ Demo finished!")


if __name__ == "__main__":
    asyncio.run(main())
