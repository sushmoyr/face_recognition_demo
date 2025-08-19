"""
Recognition publisher component for backend API integration.

Publishes recognition events to the backend API with retry logic,
batching, and offline queue management.
"""

import asyncio
import aiohttp
import json
import time
from typing import List, Dict, Any, Optional
from datetime import datetime
import uuid
from dataclasses import dataclass, asdict
import structlog
from collections import deque

from ..config import EdgeConfig
from .index_sync import MatchResult
from .snapshot_uploader import SnapshotMetadata

logger = structlog.get_logger(__name__)


@dataclass
class RecognitionEvent:
    """Recognition event to be published to backend."""
    event_id: str
    device_code: str
    timestamp: datetime
    employee_code: Optional[str]
    is_match: bool
    confidence: float
    similarity: float
    liveness_score: float
    face_bbox: tuple  # (x, y, w, h)
    template_id: Optional[str]
    snapshot_path: Optional[str]
    metadata: Dict[str, Any]
    
    def to_dict(self) -> dict:
        """Convert to dictionary for JSON serialization."""
        data = asdict(self)
        data['timestamp'] = self.timestamp.isoformat()
        data['face_bbox'] = list(self.face_bbox)
        return data


class RecognitionPublisher:
    """
    Recognition event publisher with backend API integration.
    
    Features:
    - Async HTTP publishing to backend API
    - Retry logic with exponential backoff
    - Offline queue for network failures
    - Batch processing for efficiency
    - Event deduplication
    - Performance monitoring
    """
    
    def __init__(self, config: EdgeConfig):
        self.config = config
        
        # Backend API configuration
        self.backend_url = getattr(config, 'backend_url', 'http://localhost:8080')
        self.api_endpoint = f"{self.backend_url}/api/recognitions"
        self.auth_token = getattr(config, 'auth_token', None)
        
        # Publishing settings
        self.batch_size = getattr(config, 'publish_batch_size', 10)
        self.batch_timeout = getattr(config, 'publish_batch_timeout', 5.0)  # seconds
        self.max_retries = getattr(config, 'publish_max_retries', 3)
        self.retry_delay = getattr(config, 'publish_retry_delay', 1.0)
        self.offline_queue_size = getattr(config, 'offline_queue_size', 1000)
        
        # HTTP settings
        self.timeout = getattr(config, 'http_timeout', 30.0)
        
        # Publisher state
        self.session: Optional[aiohttp.ClientSession] = None
        self.publish_queue: asyncio.Queue = None
        self.offline_queue: deque = deque(maxlen=self.offline_queue_size)
        self.batch_worker_task: Optional[asyncio.Task] = None
        self.offline_retry_task: Optional[asyncio.Task] = None
        
        # Event deduplication
        self.recent_events: Dict[str, float] = {}  # event_hash -> timestamp
        self.dedup_window = 60.0  # seconds
        
        # Performance stats
        self.events_published = 0
        self.events_failed = 0
        self.events_deduplicated = 0
        self.offline_events = 0
        self.batches_sent = 0
        self.last_publish_time = 0.0
        
        # Connection state
        self.backend_available = True
        self.last_connection_check = 0.0
        self.connection_check_interval = 30.0
    
    async def initialize(self) -> bool:
        """Initialize the recognition publisher."""
        try:
            logger.info("Initializing recognition publisher",
                       backend_url=self.backend_url,
                       batch_size=self.batch_size)
            
            # Create HTTP session
            timeout = aiohttp.ClientTimeout(total=self.timeout)
            self.session = aiohttp.ClientSession(timeout=timeout)
            
            # Test backend connectivity
            await self._check_backend_connectivity()
            
            # Initialize queues
            self.publish_queue = asyncio.Queue()
            
            # Start background workers
            self.batch_worker_task = asyncio.create_task(self._batch_worker())
            self.offline_retry_task = asyncio.create_task(self._offline_retry_worker())
            
            logger.info("Recognition publisher initialized successfully")
            return True
            
        except Exception as e:
            logger.error("Failed to initialize recognition publisher", error=str(e))
            return False
    
    async def cleanup(self):
        """Cleanup publisher resources."""
        # Cancel background tasks
        for task in [self.batch_worker_task, self.offline_retry_task]:
            if task and not task.done():
                task.cancel()
                try:
                    await task
                except asyncio.CancelledError:
                    pass
        
        # Process remaining events in queue
        await self._flush_pending_events()
        
        # Close HTTP session
        if self.session:
            await self.session.close()
        
        logger.info("Recognition publisher cleanup completed")
    
    async def publish_recognition(
        self,
        device_code: str,
        match_result: MatchResult,
        face_bbox: tuple,
        liveness_score: float,
        snapshot_metadata: Optional[SnapshotMetadata] = None,
        additional_metadata: Dict[str, Any] = None
    ) -> bool:
        """
        Publish a recognition event.
        
        Args:
            device_code: Source device identifier
            match_result: Face matching result
            face_bbox: Face bounding box (x, y, w, h)
            liveness_score: Liveness detection score
            snapshot_metadata: Uploaded snapshot metadata
            additional_metadata: Extra metadata to include
            
        Returns:
            True if queued successfully
        """
        try:
            # Create recognition event
            event = RecognitionEvent(
                event_id=str(uuid.uuid4()),
                device_code=device_code,
                timestamp=datetime.now(),
                employee_code=match_result.employee_code,
                is_match=match_result.is_match,
                confidence=match_result.confidence,
                similarity=match_result.similarity,
                liveness_score=liveness_score,
                face_bbox=face_bbox,
                template_id=match_result.template_id,
                snapshot_path=snapshot_metadata.file_path if snapshot_metadata else None,
                metadata={
                    "search_time_ms": match_result.search_time_ms,
                    "distance": match_result.distance,
                    **(additional_metadata or {})
                }
            )
            
            # Check for duplicate
            event_hash = self._generate_event_hash(event)
            current_time = time.time()
            
            if event_hash in self.recent_events:
                last_time = self.recent_events[event_hash]
                if current_time - last_time < self.dedup_window:
                    self.events_deduplicated += 1
                    logger.debug("Deduplicated recognition event", 
                               event_id=event.event_id,
                               employee_code=event.employee_code)
                    return True
            
            # Record event for deduplication
            self.recent_events[event_hash] = current_time
            
            # Clean old dedup entries
            self._cleanup_dedup_cache(current_time)
            
            # Queue for publishing
            await self.publish_queue.put(event)
            
            logger.debug("Queued recognition event for publishing",
                        event_id=event.event_id,
                        employee_code=event.employee_code,
                        is_match=event.is_match)
            
            return True
            
        except Exception as e:
            logger.error("Error queueing recognition event", error=str(e))
            return False
    
    def _generate_event_hash(self, event: RecognitionEvent) -> str:
        """Generate hash for event deduplication."""
        # Use device, employee, and rounded timestamp for deduplication
        timestamp_rounded = int(event.timestamp.timestamp() / 10) * 10  # 10-second windows
        hash_data = f"{event.device_code}:{event.employee_code}:{timestamp_rounded}"
        return hash_data
    
    def _cleanup_dedup_cache(self, current_time: float):
        """Remove old entries from deduplication cache."""
        cutoff_time = current_time - self.dedup_window
        self.recent_events = {
            event_hash: timestamp 
            for event_hash, timestamp in self.recent_events.items()
            if timestamp > cutoff_time
        }
    
    async def _batch_worker(self):
        """Background worker for batch processing events."""
        logger.info("Started recognition batch worker", batch_size=self.batch_size)
        
        batch = []
        last_batch_time = time.time()
        
        while True:
            try:
                # Get event from queue with timeout
                try:
                    event = await asyncio.wait_for(
                        self.publish_queue.get(),
                        timeout=1.0
                    )
                except asyncio.TimeoutError:
                    event = None
                
                current_time = time.time()
                
                # Add event to batch if received
                if event is not None:
                    batch.append(event)
                
                # Check if we should send batch
                should_send = (
                    len(batch) >= self.batch_size or
                    (len(batch) > 0 and current_time - last_batch_time >= self.batch_timeout)
                )
                
                if should_send and batch:
                    await self._send_batch(batch)
                    batch.clear()
                    last_batch_time = current_time
                
            except asyncio.CancelledError:
                logger.info("Batch worker cancelled")
                # Send remaining batch
                if batch:
                    await self._send_batch(batch)
                break
            except Exception as e:
                logger.error("Error in batch worker", error=str(e))
    
    async def _send_batch(self, events: List[RecognitionEvent]):
        """Send batch of events to backend."""
        if not events:
            return
        
        try:
            # Convert events to payload
            payload = {
                "events": [event.to_dict() for event in events],
                "batch_id": str(uuid.uuid4()),
                "timestamp": datetime.now().isoformat(),
                "device_code": events[0].device_code  # Assume same device in batch
            }
            
            # Attempt to send with retries
            success = await self._send_with_retries(payload)
            
            if success:
                self.events_published += len(events)
                self.batches_sent += 1
                self.last_publish_time = time.time()
                self.backend_available = True
                
                logger.debug("Successfully published event batch",
                           batch_size=len(events),
                           batch_id=payload["batch_id"])
            else:
                # Add to offline queue
                self._queue_offline_events(events)
                self.events_failed += len(events)
                self.backend_available = False
                
                logger.warning("Failed to publish event batch, queued offline",
                             batch_size=len(events))
            
        except Exception as e:
            logger.error("Error sending event batch", error=str(e))
            self._queue_offline_events(events)
            self.events_failed += len(events)
    
    async def _send_with_retries(self, payload: Dict[str, Any]) -> bool:
        """Send payload with retry logic."""
        headers = {"Content-Type": "application/json"}
        
        if self.auth_token:
            headers["Authorization"] = f"Bearer {self.auth_token}"
        
        for attempt in range(self.max_retries + 1):
            try:
                async with self.session.post(
                    self.api_endpoint,
                    json=payload,
                    headers=headers
                ) as response:
                    
                    if response.status in [200, 201, 202]:
                        return True
                    elif response.status == 409:
                        # Conflict - events already processed (deduplication on server)
                        logger.debug("Events already processed by server", 
                                   status=response.status)
                        return True
                    else:
                        error_text = await response.text()
                        logger.warning("Backend returned error",
                                     status=response.status,
                                     error=error_text,
                                     attempt=attempt + 1)
                        
                        if attempt < self.max_retries and response.status >= 500:
                            # Retry on server errors
                            delay = self.retry_delay * (2 ** attempt)
                            await asyncio.sleep(delay)
                            continue
                        else:
                            return False
                            
            except asyncio.TimeoutError:
                logger.warning("Request timeout", attempt=attempt + 1)
                if attempt < self.max_retries:
                    delay = self.retry_delay * (2 ** attempt)
                    await asyncio.sleep(delay)
                    continue
                else:
                    return False
                    
            except Exception as e:
                logger.warning("Request failed", 
                             error=str(e),
                             attempt=attempt + 1)
                if attempt < self.max_retries:
                    delay = self.retry_delay * (2 ** attempt)
                    await asyncio.sleep(delay)
                    continue
                else:
                    return False
        
        return False
    
    def _queue_offline_events(self, events: List[RecognitionEvent]):
        """Queue events for offline retry."""
        for event in events:
            if len(self.offline_queue) < self.offline_queue_size:
                self.offline_queue.append(event)
                self.offline_events += 1
            else:
                logger.warning("Offline queue full, dropping event",
                             event_id=event.event_id)
    
    async def _offline_retry_worker(self):
        """Background worker for retrying offline events."""
        logger.info("Started offline retry worker")
        
        while True:
            try:
                await asyncio.sleep(30.0)  # Check every 30 seconds
                
                if not self.offline_queue:
                    continue
                
                # Check backend connectivity
                await self._check_backend_connectivity()
                
                if not self.backend_available:
                    continue
                
                # Process offline events in batches
                batch_size = min(self.batch_size, len(self.offline_queue))
                if batch_size > 0:
                    batch = []
                    for _ in range(batch_size):
                        if self.offline_queue:
                            batch.append(self.offline_queue.popleft())
                    
                    if batch:
                        logger.info("Retrying offline events", count=len(batch))
                        await self._send_batch(batch)
                
            except asyncio.CancelledError:
                logger.info("Offline retry worker cancelled")
                break
            except Exception as e:
                logger.error("Error in offline retry worker", error=str(e))
    
    async def _check_backend_connectivity(self) -> bool:
        """Check if backend is reachable."""
        current_time = time.time()
        
        # Skip check if done recently
        if current_time - self.last_connection_check < self.connection_check_interval:
            return self.backend_available
        
        try:
            health_url = f"{self.backend_url}/actuator/health"
            
            async with self.session.get(health_url) as response:
                self.backend_available = response.status == 200
                
        except Exception as e:
            logger.debug("Backend connectivity check failed", error=str(e))
            self.backend_available = False
        
        self.last_connection_check = current_time
        return self.backend_available
    
    async def _flush_pending_events(self):
        """Flush any remaining events in queues."""
        try:
            # Process remaining events in publish queue
            remaining_events = []
            while not self.publish_queue.empty():
                try:
                    event = await asyncio.wait_for(self.publish_queue.get(), timeout=0.1)
                    remaining_events.append(event)
                except asyncio.TimeoutError:
                    break
            
            if remaining_events:
                logger.info("Flushing remaining events", count=len(remaining_events))
                await self._send_batch(remaining_events)
                
        except Exception as e:
            logger.error("Error flushing pending events", error=str(e))
    
    def get_stats(self) -> dict:
        """Get publishing performance statistics."""
        total_events = self.events_published + self.events_failed
        success_rate = (self.events_published / total_events 
                       if total_events > 0 else 0)
        
        queue_size = 0
        if self.publish_queue:
            queue_size = self.publish_queue.qsize()
        
        return {
            "events_published": self.events_published,
            "events_failed": self.events_failed,
            "events_deduplicated": self.events_deduplicated,
            "success_rate": success_rate,
            "batches_sent": self.batches_sent,
            "offline_events": len(self.offline_queue),
            "queue_size": queue_size,
            "backend_available": self.backend_available,
            "last_publish_time": self.last_publish_time,
            "backend_url": self.backend_url,
            "batch_size": self.batch_size
        }
