"""
Main entry point for the face recognition edge service.

This service processes video streams (RTSP/file/webcam) to detect and recognize faces,
then publishes recognition events to the backend API.
"""

import asyncio
import signal
import sys
from pathlib import Path
import structlog
from rich.console import Console
from rich.live import Live
from rich.layout import Layout
from rich.panel import Panel
from rich.table import Table
from rich.text import Text
import argparse
import time

from .config import EdgeConfig
from .pipeline import FaceRecognitionPipeline

# Configure structured logging
structlog.configure(
    processors=[
        structlog.stdlib.filter_by_level,
        structlog.stdlib.add_logger_name,
        structlog.stdlib.add_log_level,
        structlog.stdlib.PositionalArgumentsFormatter(),
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.processors.StackInfoRenderer(),
        structlog.processors.format_exc_info,
        structlog.dev.ConsoleRenderer()
    ],
    wrapper_class=structlog.stdlib.BoundLogger,
    logger_factory=structlog.stdlib.LoggerFactory(),
    context_class=dict,
    cache_logger_on_first_use=True,
)

logger = structlog.get_logger(__name__)
console = Console()


class EdgeService:
    """Main edge service orchestrator."""
    
    def __init__(self, config: EdgeConfig):
        self.config = config
        self.pipeline: FaceRecognitionPipeline = None
        self.running = False
        self.stats_task = None
        
    async def initialize(self) -> bool:
        """Initialize the edge service."""
        try:
            logger.info("Initializing edge service", device_code=self.config.device_code)
            
            # Create and initialize pipeline
            self.pipeline = FaceRecognitionPipeline(self.config)
            
            if not await self.pipeline.initialize():
                logger.error("Failed to initialize pipeline")
                return False
                
            logger.info("Edge service initialized successfully")
            return True
            
        except Exception as e:
            logger.error("Failed to initialize edge service", error=str(e))
            return False
    
    async def start(self):
        """Start the edge service."""
        try:
            logger.info("Starting edge service")
            
            # Start pipeline
            await self.pipeline.start()
            self.running = True
            
            # Start stats display task
            if getattr(self.config, 'display_stats', True):
                self.stats_task = asyncio.create_task(self._stats_display_loop())
            
            logger.info("Edge service started successfully")
            
        except Exception as e:
            logger.error("Failed to start edge service", error=str(e))
            raise
    
    async def stop(self):
        """Stop the edge service."""
        try:
            logger.info("Stopping edge service")
            
            self.running = False
            
            # Stop stats display
            if self.stats_task and not self.stats_task.done():
                self.stats_task.cancel()
                try:
                    await self.stats_task
                except asyncio.CancelledError:
                    pass
            
            # Stop pipeline
            if self.pipeline:
                await self.pipeline.stop()
            
            logger.info("Edge service stopped")
            
        except Exception as e:
            logger.error("Error stopping edge service", error=str(e))
    
    async def cleanup(self):
        """Cleanup edge service resources."""
        try:
            await self.stop()
            
            if self.pipeline:
                await self.pipeline.cleanup()
                
            logger.info("Edge service cleanup completed")
            
        except Exception as e:
            logger.error("Error in edge service cleanup", error=str(e))
    
    async def _stats_display_loop(self):
        """Display live statistics in the console."""
        try:
            with Live(self._create_stats_display(), 
                     refresh_per_second=2, 
                     console=console) as live:
                
                while self.running:
                    live.update(self._create_stats_display())
                    await asyncio.sleep(0.5)
                    
        except asyncio.CancelledError:
            pass
        except Exception as e:
            logger.error("Error in stats display loop", error=str(e))
    
    def _create_stats_display(self) -> Layout:
        """Create rich layout for statistics display."""
        try:
            stats = self.pipeline.get_stats() if self.pipeline else {}
            pipeline_stats = stats.get("pipeline", {})
            component_stats = stats.get("components", {})
            
            layout = Layout()
            layout.split_column(
                Layout(name="header", size=3),
                Layout(name="body"),
                Layout(name="footer", size=3)
            )
            
            # Header
            header_text = Text("Face Recognition Edge Service", style="bold blue")
            header_text.append(f" - Device: {self.config.device_code}", style="dim")
            layout["header"].update(Panel(header_text, border_style="blue"))
            
            # Body with stats tables
            layout["body"].split_row(
                Layout(name="pipeline_stats"),
                Layout(name="component_stats")
            )
            
            # Pipeline stats table
            pipeline_table = Table(title="Pipeline Statistics", border_style="green")
            pipeline_table.add_column("Metric", style="cyan")
            pipeline_table.add_column("Value", style="white")
            
            pipeline_table.add_row("Frames Processed", str(pipeline_stats.get("frames_processed", 0)))
            pipeline_table.add_row("Faces Detected", str(pipeline_stats.get("faces_detected", 0)))
            pipeline_table.add_row("Faces Recognized", str(pipeline_stats.get("faces_recognized", 0)))
            pipeline_table.add_row("Events Published", str(pipeline_stats.get("recognitions_published", 0)))
            pipeline_table.add_row("Processing FPS", f"{pipeline_stats.get('processing_fps', 0):.2f}")
            pipeline_table.add_row("Avg Process Time", f"{pipeline_stats.get('avg_processing_time', 0)*1000:.1f}ms")
            pipeline_table.add_row("Uptime", f"{pipeline_stats.get('uptime_seconds', 0):.1f}s")
            
            layout["pipeline_stats"].update(Panel(pipeline_table, border_style="green"))
            
            # Component stats table
            component_table = Table(title="Component Statistics", border_style="yellow")
            component_table.add_column("Component", style="cyan")
            component_table.add_column("Key Metrics", style="white")
            
            # Add component stats
            for component_name, comp_stats in component_stats.items():
                if component_name == "rtsp_reader":
                    metrics = f"FPS: {comp_stats.get('current_fps', 0):.1f}, Frames: {comp_stats.get('frames_read', 0)}"
                elif component_name == "face_detector":
                    metrics = f"Detections: {comp_stats.get('detections_performed', 0)}, Avg Time: {comp_stats.get('avg_detection_time', 0)*1000:.1f}ms"
                elif component_name == "face_tracker":
                    metrics = f"Active: {comp_stats.get('active_tracks', 0)}, Stable: {comp_stats.get('stable_tracks', 0)}"
                elif component_name == "index_sync":
                    metrics = f"Templates: {comp_stats.get('template_count', 0)}, Matches: {comp_stats.get('matches_found', 0)}"
                elif component_name == "recognition_publisher":
                    success_rate = comp_stats.get('success_rate', 0) * 100
                    metrics = f"Published: {comp_stats.get('events_published', 0)}, Success: {success_rate:.1f}%"
                elif component_name == "snapshot_uploader":
                    success_rate = comp_stats.get('success_rate', 0) * 100
                    metrics = f"Uploaded: {comp_stats.get('uploads_completed', 0)}, Success: {success_rate:.1f}%"
                else:
                    metrics = "N/A"
                
                component_table.add_row(component_name.replace("_", " ").title(), metrics)
            
            layout["component_stats"].update(Panel(component_table, border_style="yellow"))
            
            # Footer
            footer_text = Text("Press Ctrl+C to stop", style="dim")
            layout["footer"].update(Panel(footer_text, border_style="dim"))
            
            return layout
            
        except Exception as e:
            logger.error("Error creating stats display", error=str(e))
            return Layout(Panel("Error creating display", border_style="red"))


async def main():
    """Main application entry point."""
    parser = argparse.ArgumentParser(description="Face Recognition Edge Service")
    parser.add_argument("--config", type=str, help="Path to configuration file")
    parser.add_argument("--source", type=str, help="Video source (RTSP URL, file path, or camera index)")
    parser.add_argument("--device-code", type=str, help="Device identifier", default="edge-001")
    parser.add_argument("--backend-url", type=str, help="Backend API URL", default="http://localhost:8080")
    parser.add_argument("--no-stats", action="store_true", help="Disable live statistics display")
    parser.add_argument("--debug", action="store_true", help="Enable debug logging")
    
    args = parser.parse_args()
    
    # Configure logging level
    if args.debug:
        import logging
        logging.getLogger().setLevel(logging.DEBUG)
    
    # Create configuration
    config = EdgeConfig()
    
    # Override config with command line arguments
    if args.source:
        config.video_source = args.source
    if args.device_code:
        config.device_code = args.device_code
    if args.backend_url:
        config.backend_url = args.backend_url
    
    config.display_stats = not args.no_stats
    
    # Load additional config from file if provided
    if args.config and Path(args.config).exists():
        # TODO: Implement config file loading
        logger.info("Config file loading not implemented yet", config_file=args.config)
    
    # Create and run edge service
    edge_service = EdgeService(config)
    
    def signal_handler(signum, frame):
        logger.info("Received shutdown signal", signal=signum)
        edge_service.running = False
    
    # Register signal handlers
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)
    
    try:
        # Initialize service
        if not await edge_service.initialize():
            logger.error("Failed to initialize edge service")
            sys.exit(1)
        
        # Start service
        await edge_service.start()
        
        # Keep running until shutdown signal
        while edge_service.running:
            await asyncio.sleep(0.1)
            
    except KeyboardInterrupt:
        logger.info("Received keyboard interrupt")
    except Exception as e:
        logger.error("Unexpected error", error=str(e))
        sys.exit(1)
    finally:
        await edge_service.cleanup()
        logger.info("Edge service shutdown completed")


if __name__ == "__main__":
    asyncio.run(main())
