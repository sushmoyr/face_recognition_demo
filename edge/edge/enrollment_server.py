"""
HTTP API server for edge service enrollment functionality.

Provides endpoints for processing images during employee enrollment:
- POST /process-image: Process uploaded image and return face embeddings
- GET /health: Health check endpoint
"""

import asyncio
import io
import base64
from typing import Optional, List, Dict, Any
import numpy as np
from PIL import Image
import cv2

from aiohttp import web, MultipartReader
from aiohttp.web_request import Request
from aiohttp.web_response import Response
import aiohttp_cors
import structlog

from .pipeline import FaceDetector, EmbeddingExtractor, LivenessDetector
from .simple_config import SimpleEdgeConfig

logger = structlog.get_logger(__name__)


class EdgeEnrollmentServer:
    """HTTP server for enrollment image processing."""
    
    def __init__(self, config: SimpleEdgeConfig):
        self.config = config
        self.face_detector: Optional[FaceDetector] = None
        self.embedding_extractor: Optional[EmbeddingExtractor] = None
        self.liveness_detector: Optional[LivenessDetector] = None
        self.app: Optional[web.Application] = None
        
    async def initialize(self) -> bool:
        """Initialize the server components."""
        try:
            logger.info("Initializing enrollment server components")
            
            # Initialize face processing components
            self.face_detector = FaceDetector(self.config)
            if not await self.face_detector.initialize():
                logger.error("Failed to initialize face detector")
                return False
                
            self.embedding_extractor = EmbeddingExtractor(self.config)
            if not await self.embedding_extractor.initialize():
                logger.error("Failed to initialize embedding extractor") 
                return False
                
            self.liveness_detector = LivenessDetector(self.config)
            if not await self.liveness_detector.initialize():
                logger.error("Failed to initialize liveness detector")
                return False
                
            # Create aiohttp application
            self.app = web.Application()
            
            # Setup CORS
            cors = aiohttp_cors.setup(self.app, defaults={
                "*": aiohttp_cors.ResourceOptions(
                    allow_credentials=True,
                    expose_headers="*",
                    allow_headers="*",
                    allow_methods="*"
                )
            })
            
            # Add routes
            self.app.router.add_post('/process-image', self.process_image)
            self.app.router.add_get('/health', self.health_check)
            
            # Add CORS to all routes
            for route in list(self.app.router.routes()):
                cors.add(route)
            
            logger.info("Enrollment server initialized successfully")
            return True
            
        except Exception as e:
            logger.error("Failed to initialize enrollment server", error=str(e))
            return False
    
    async def process_image(self, request: Request) -> Response:
        """Process uploaded image and return face embeddings."""
        try:
            # Parse multipart data
            reader = await request.multipart()
            field = await reader.next()
            
            if field.name != 'image':
                return web.json_response({
                    'error': 'Expected field named "image"'
                }, status=400)
            
            # Read image data
            image_data = await field.read()
            
            # Convert to numpy array
            image_array = np.frombuffer(image_data, np.uint8)
            image = cv2.imdecode(image_array, cv2.IMREAD_COLOR)
            
            if image is None:
                return web.json_response({
                    'error': 'Invalid image format'
                }, status=400)
            
            logger.info("Processing uploaded image", shape=image.shape)
            
            # Detect faces
            detections = await self.face_detector.detect_faces(image)
            
            if not detections:
                return web.json_response({
                    'success': False,
                    'message': 'No faces detected in image',
                    'face_count': 0,
                    'embeddings': []
                })
            
            # Process each detected face
            results = []
            for detection in detections:
                try:
                    # Extract face crop
                    x, y, w, h = detection.bbox
                    face_crop = image[y:y+h, x:x+w]
                    detection.face_crop = face_crop
                    
                    # Check liveness
                    liveness_result = await self.liveness_detector.check_liveness(
                        detection, image
                    )
                    
                    # Extract embedding
                    embedding_result = await self.embedding_extractor.extract_embedding(detection)
                    
                    if embedding_result:
                        results.append({
                            'bbox': detection.bbox,
                            'confidence': detection.confidence,
                            'embedding': embedding_result.vector.tolist(),  # Convert to list for JSON
                            'embedding_confidence': embedding_result.confidence,
                            'liveness_score': liveness_result.confidence if liveness_result else 0.0,
                            'is_live': liveness_result.is_live if liveness_result else False
                        })
                        
                except Exception as e:
                    logger.warning("Failed to process face detection", error=str(e))
                    continue
            
            logger.info("Image processing completed", face_count=len(results))
            
            return web.json_response({
                'success': True,
                'message': f'Processed {len(results)} faces',
                'face_count': len(results),
                'embeddings': results
            })
            
        except Exception as e:
            logger.error("Error processing image", error=str(e))
            return web.json_response({
                'error': f'Processing failed: {str(e)}'
            }, status=500)
    
    async def health_check(self, request: Request) -> Response:
        """Health check endpoint."""
        return web.json_response({
            'status': 'healthy',
            'service': 'edge-enrollment-server',
            'components': {
                'face_detector': self.face_detector is not None,
                'embedding_extractor': self.embedding_extractor is not None,
                'liveness_detector': self.liveness_detector is not None
            }
        })
    
    async def start_server(self, host: str = "localhost", port: int = 8081):
        """Start the HTTP server."""
        if not self.app:
            raise RuntimeError("Server not initialized")
        
        logger.info("Starting enrollment server", host=host, port=port)
        runner = web.AppRunner(self.app)
        await runner.setup()
        
        site = web.TCPSite(runner, host, port)
        await site.start()
        
        logger.info("Enrollment server started", url=f"http://{host}:{port}")
        return runner


async def main():
    """Run the enrollment server standalone."""
    config = SimpleEdgeConfig(
        device_code="enrollment-server",
        debug=True
    )
    
    server = EdgeEnrollmentServer(config)
    
    if not await server.initialize():
        print("Failed to initialize server")
        return
    
    runner = await server.start_server()
    
    try:
        print("Enrollment server running at http://localhost:8081")
        print("Use Ctrl+C to stop")
        
        # Keep running
        await asyncio.Future()  # run forever
        
    except KeyboardInterrupt:
        print("Shutting down...")
    finally:
        await runner.cleanup()


if __name__ == "__main__":
    asyncio.run(main())
