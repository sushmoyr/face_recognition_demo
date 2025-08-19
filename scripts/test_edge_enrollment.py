#!/usr/bin/env python3
"""
Test edge enrollment server functionality.
Tests the image processing endpoints without requiring backend.
"""

import asyncio
import time
import requests
import threading
import sys
from pathlib import Path


class EdgeEnrollmentTest:
    """Test edge enrollment server."""
    
    def __init__(self):
        self.edge_url = "http://localhost:8081"
        self.edge_thread = None
        
    def start_edge_server(self):
        """Start the edge enrollment server."""
        print("🚀 Starting edge enrollment server...")
        
        try:
            import sys
            sys.path.append('edge')
            from edge.enrollment_server import EdgeEnrollmentServer, SimpleEdgeConfig
            
            config = SimpleEdgeConfig(debug=True)
            server = EdgeEnrollmentServer(config)
            
            # Run server in background thread
            def run_server():
                asyncio.run(self._run_edge_server(server))
            
            self.edge_thread = threading.Thread(target=run_server, daemon=True)
            self.edge_thread.start()
            
            # Wait for server to start
            time.sleep(3)
            
            # Check if server is running
            response = requests.get(f"{self.edge_url}/health", timeout=5)
            if response.status_code == 200:
                print("✅ Edge server started successfully")
                return True
            else:
                print(f"❌ Edge server health check failed: {response.status_code}")
                return False
                
        except Exception as e:
            print(f"❌ Failed to start edge server: {e}")
            return False
    
    async def _run_edge_server(self, server):
        """Run the edge server async."""
        try:
            await server.initialize()
            await server.start_server()
            # Keep running
            await asyncio.Future()  # run forever
        except Exception as e:
            print(f"Edge server error: {e}")
    
    def test_health_endpoint(self):
        """Test health endpoint."""
        print("🧪 Testing health endpoint...")
        
        try:
            response = requests.get(f"{self.edge_url}/health", timeout=5)
            if response.status_code == 200:
                data = response.json()
                print(f"   ✅ Status: {data.get('status')}")
                print(f"   ✅ Service: {data.get('service')}")
                components = data.get('components', {})
                for component, status in components.items():
                    print(f"   ✅ {component}: {'OK' if status else 'FAIL'}")
                return True
            else:
                print(f"   ❌ Health check failed: {response.status_code}")
                return False
        except Exception as e:
            print(f"   ❌ Health endpoint error: {e}")
            return False
    
    def test_image_processing(self):
        """Test image processing endpoint."""
        print("🧪 Testing image processing...")
        
        # Test with sample images
        sample_images = [
            "samples/employee_E1001/face_1.jpg",
            "samples/employee_E1001/face_2.jpg",
            "samples/employee_E1002/face_1.jpg"
        ]
        
        for sample_image in sample_images:
            if not Path(sample_image).exists():
                print(f"   ⚠️  Sample image not found: {sample_image}")
                continue
                
            try:
                print(f"   Processing {Path(sample_image).name}...")
                
                with open(sample_image, 'rb') as f:
                    files = {'image': f}
                    response = requests.post(f"{self.edge_url}/process-image", 
                                           files=files, timeout=30)
                
                if response.status_code == 200:
                    data = response.json()
                    success = data.get('success', False)
                    face_count = data.get('face_count', 0)
                    message = data.get('message', '')
                    
                    print(f"     ✅ Success: {success}")
                    print(f"     ✅ Faces detected: {face_count}")
                    print(f"     ✅ Message: {message}")
                    
                    # Check embeddings if faces were detected
                    if face_count > 0:
                        embeddings = data.get('embeddings', [])
                        for i, embedding in enumerate(embeddings):
                            conf = embedding.get('confidence', 0)
                            liveness = embedding.get('liveness_score', 0)
                            is_live = embedding.get('is_live', False)
                            emb_len = len(embedding.get('embedding', []))
                            print(f"       Face {i+1}: conf={conf:.3f}, liveness={liveness:.3f}, live={is_live}, dim={emb_len}")
                    
                else:
                    print(f"     ❌ Processing failed: {response.status_code}")
                    print(f"     Error: {response.text}")
                    return False
                    
            except Exception as e:
                print(f"     ❌ Processing error: {e}")
                return False
        
        return True
    
    def test_invalid_requests(self):
        """Test error handling."""
        print("🧪 Testing error handling...")
        
        # Test with no image
        try:
            response = requests.post(f"{self.edge_url}/process-image", timeout=10)
            if response.status_code == 400:
                print("   ✅ Correctly rejected request with no image")
            else:
                print(f"   ⚠️  Unexpected status for no image: {response.status_code}")
        except Exception as e:
            print(f"   ❌ Error testing no image: {e}")
            return False
        
        # Test with invalid image data
        try:
            files = {'image': ('test.jpg', b'invalid image data', 'image/jpeg')}
            response = requests.post(f"{self.edge_url}/process-image", 
                                   files=files, timeout=10)
            if response.status_code == 400:
                print("   ✅ Correctly rejected invalid image data")
            else:
                print(f"   ⚠️  Unexpected status for invalid image: {response.status_code}")
        except Exception as e:
            print(f"   ❌ Error testing invalid image: {e}")
            return False
        
        return True
    
    def run_test(self):
        """Run all tests."""
        print("🚀 Edge Enrollment Server Test")
        print("=" * 50)
        
        try:
            # Start server
            if not self.start_edge_server():
                return False
            
            # Test health
            if not self.test_health_endpoint():
                return False
            
            # Test image processing
            if not self.test_image_processing():
                return False
            
            # Test error handling
            if not self.test_invalid_requests():
                return False
            
            print("\n" + "=" * 50)
            print("🎉 All edge enrollment tests PASSED!")
            print("\nEdge enrollment server is ready for integration!")
            print("You can now:")
            print("1. Start the backend server")
            print("2. Test full enrollment: python scripts/enroll_cli.py --employee-code E1001 --path samples/employee_E1001")
            
            return True
            
        except Exception as e:
            print(f"\n❌ Test failed with error: {e}")
            return False


def main():
    """Main test entry point."""
    test = EdgeEnrollmentTest()
    success = test.run_test()
    
    if success:
        print("\n✅ Edge enrollment server ready!")
        sys.exit(0)
    else:
        print("\n❌ Edge enrollment server needs fixes")
        sys.exit(1)


if __name__ == "__main__":
    main()
