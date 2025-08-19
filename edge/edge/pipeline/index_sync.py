"""
Index synchronization and face matching component using FAISS.

Manages the FAISS index for fast similarity search of face embeddings
and synchronizes with the backend database.
"""

import faiss
import numpy as np
import asyncio
import aiohttp
import time
from typing import List, Optional, Tuple, Dict, Any
import structlog
from dataclasses import dataclass
import json
import pickle
import os

from ..config import EdgeConfig

logger = structlog.get_logger(__name__)


@dataclass
class FaceTemplate:
    """Represents a face template from the database."""
    employee_code: str
    template_id: str
    embedding: np.ndarray
    created_at: str
    metadata: dict = None
    
    def to_dict(self) -> dict:
        return {
            "employee_code": self.employee_code,
            "template_id": self.template_id,
            "embedding": self.embedding.tolist() if self.embedding is not None else None,
            "created_at": self.created_at,
            "metadata": self.metadata or {}
        }


@dataclass
class MatchResult:
    """Result of face matching against the index."""
    is_match: bool
    employee_code: Optional[str]
    template_id: Optional[str]
    similarity: float
    distance: float
    confidence: float
    search_time_ms: float
    
    def to_dict(self) -> dict:
        return {
            "is_match": self.is_match,
            "employee_code": self.employee_code,
            "template_id": self.template_id,
            "similarity": self.similarity,
            "distance": self.distance,
            "confidence": self.confidence,
            "search_time_ms": self.search_time_ms
        }


class IndexSync:
    """
    FAISS index manager for face recognition.
    
    Features:
    - Fast similarity search using FAISS
    - Periodic synchronization with backend database
    - In-memory and persistent index storage
    - Template management and updates
    """
    
    def __init__(self, config: EdgeConfig):
        self.config = config
        self.backend_url = getattr(config, 'backend_url', 'http://localhost:8080')
        self.sync_interval = getattr(config, 'index_sync_interval', 300)  # 5 minutes
        self.similarity_threshold = getattr(config, 'similarity_threshold', 0.7)
        self.embedding_dim = getattr(config, 'embedding_dim', 512)
        
        # FAISS index and metadata
        self.index: Optional[faiss.Index] = None
        self.templates: List[FaceTemplate] = []
        self.template_map: Dict[int, FaceTemplate] = {}  # index_id -> template
        
        # Index persistence
        self.index_cache_path = getattr(config, 'index_cache_path', './cache/face_index.faiss')
        self.metadata_cache_path = getattr(config, 'metadata_cache_path', './cache/face_metadata.pkl')
        
        # Performance tracking
        self.searches_performed = 0
        self.matches_found = 0
        self.last_sync_time = 0.0
        self.sync_count = 0
        
        # Background sync task
        self.sync_task: Optional[asyncio.Task] = None
        self.session: Optional[aiohttp.ClientSession] = None
    
    async def initialize(self) -> bool:
        """Initialize the index system."""
        try:
            logger.info("Initializing face index", 
                       embedding_dim=self.embedding_dim,
                       similarity_threshold=self.similarity_threshold)
            
            # Create HTTP session
            self.session = aiohttp.ClientSession()
            
            # Create cache directories
            os.makedirs(os.path.dirname(self.index_cache_path), exist_ok=True)
            os.makedirs(os.path.dirname(self.metadata_cache_path), exist_ok=True)
            
            # Try to load cached index
            if not await self._load_cached_index():
                logger.info("No cached index found, creating new index")
                await self._create_new_index()
            
            # Perform initial sync
            await self.sync_with_backend()
            
            # Start background sync task
            self.sync_task = asyncio.create_task(self._background_sync_loop())
            
            logger.info("Face index initialized successfully", 
                       template_count=len(self.templates))
            return True
            
        except Exception as e:
            logger.error("Failed to initialize face index", error=str(e))
            return False
    
    async def cleanup(self):
        """Cleanup resources."""
        if self.sync_task and not self.sync_task.done():
            self.sync_task.cancel()
            try:
                await self.sync_task
            except asyncio.CancelledError:
                pass
        
        if self.session:
            await self.session.close()
        
        # Save index to cache
        await self._save_index_cache()
    
    async def _create_new_index(self):
        """Create a new FAISS index."""
        # Use IndexFlatIP for cosine similarity (inner product on normalized vectors)
        self.index = faiss.IndexFlatIP(self.embedding_dim)
        self.templates = []
        self.template_map = {}
        logger.info("Created new FAISS index", dimension=self.embedding_dim)
    
    async def _load_cached_index(self) -> bool:
        """Load index and metadata from cache."""
        try:
            if (os.path.exists(self.index_cache_path) and 
                os.path.exists(self.metadata_cache_path)):
                
                # Load FAISS index
                self.index = faiss.read_index(self.index_cache_path)
                
                # Load metadata
                with open(self.metadata_cache_path, 'rb') as f:
                    cache_data = pickle.load(f)
                    self.templates = cache_data['templates']
                    self.template_map = cache_data['template_map']
                
                logger.info("Loaded cached index", 
                           template_count=len(self.templates),
                           index_size=self.index.ntotal)
                return True
                
        except Exception as e:
            logger.warning("Failed to load cached index", error=str(e))
            
        return False
    
    async def _save_index_cache(self):
        """Save index and metadata to cache."""
        try:
            if self.index is not None:
                # Save FAISS index
                faiss.write_index(self.index, self.index_cache_path)
                
                # Save metadata
                cache_data = {
                    'templates': self.templates,
                    'template_map': self.template_map,
                    'last_sync_time': self.last_sync_time
                }
                
                with open(self.metadata_cache_path, 'wb') as f:
                    pickle.dump(cache_data, f)
                
                logger.debug("Saved index cache", template_count=len(self.templates))
                
        except Exception as e:
            logger.error("Failed to save index cache", error=str(e))
    
    async def sync_with_backend(self) -> bool:
        """Sync templates from backend database."""
        try:
            logger.info("Syncing face templates from backend")
            
            # Fetch templates from backend
            templates = await self._fetch_templates_from_backend()
            
            if templates is None:
                logger.warning("Failed to fetch templates from backend")
                return False
            
            # Rebuild index with new templates
            await self._rebuild_index(templates)
            
            self.last_sync_time = time.time()
            self.sync_count += 1
            
            logger.info("Successfully synced face templates", 
                       template_count=len(self.templates),
                       sync_count=self.sync_count)
            
            # Save updated index to cache
            await self._save_index_cache()
            
            return True
            
        except Exception as e:
            logger.error("Error syncing with backend", error=str(e))
            return False
    
    async def _fetch_templates_from_backend(self) -> Optional[List[FaceTemplate]]:
        """Fetch all face templates from backend API."""
        try:
            url = f"{self.backend_url}/api/face-templates"
            
            async with self.session.get(url) as response:
                if response.status == 200:
                    data = await response.json()
                    templates = []
                    
                    for item in data:
                        # Convert embedding from list back to numpy array
                        embedding = np.array(item['embedding'], dtype=np.float32)
                        # Ensure embedding is normalized for cosine similarity
                        embedding = embedding / np.linalg.norm(embedding)
                        
                        template = FaceTemplate(
                            employee_code=item['employee_code'],
                            template_id=item['template_id'],
                            embedding=embedding,
                            created_at=item['created_at'],
                            metadata=item.get('metadata', {})
                        )
                        templates.append(template)
                    
                    return templates
                else:
                    logger.warning("Backend returned error", 
                                 status=response.status,
                                 text=await response.text())
                    return None
                    
        except Exception as e:
            logger.error("Error fetching templates from backend", error=str(e))
            return None
    
    async def _rebuild_index(self, templates: List[FaceTemplate]):
        """Rebuild FAISS index with new templates."""
        # Create new index
        await self._create_new_index()
        
        if not templates:
            return
        
        # Prepare embeddings matrix
        embeddings = np.vstack([t.embedding for t in templates]).astype(np.float32)
        
        # Add to FAISS index
        self.index.add(embeddings)
        
        # Update template mappings
        self.templates = templates
        self.template_map = {i: template for i, template in enumerate(templates)}
        
        logger.info("Rebuilt FAISS index", 
                   template_count=len(templates),
                   index_size=self.index.ntotal)
    
    async def search_similar(self, embedding: np.ndarray, k: int = 1) -> List[MatchResult]:
        """
        Search for similar faces in the index.
        
        Args:
            embedding: Face embedding to search for (should be normalized)
            k: Number of top matches to return
            
        Returns:
            List of match results sorted by similarity
        """
        self.searches_performed += 1
        start_time = time.time()
        
        try:
            if self.index is None or self.index.ntotal == 0:
                return [MatchResult(
                    is_match=False,
                    employee_code=None,
                    template_id=None,
                    similarity=0.0,
                    distance=float('inf'),
                    confidence=0.0,
                    search_time_ms=0.0
                )]
            
            # Ensure embedding is normalized and in correct format
            query_embedding = embedding.astype(np.float32).reshape(1, -1)
            query_embedding = query_embedding / np.linalg.norm(query_embedding)
            
            # Perform search
            similarities, indices = self.index.search(query_embedding, k)
            
            search_time_ms = (time.time() - start_time) * 1000
            
            # Convert results
            results = []
            for i in range(k):
                if i < len(similarities[0]) and indices[0][i] != -1:
                    similarity = float(similarities[0][i])
                    template_idx = int(indices[0][i])
                    
                    if template_idx in self.template_map:
                        template = self.template_map[template_idx]
                        is_match = similarity >= self.similarity_threshold
                        
                        if is_match:
                            self.matches_found += 1
                        
                        result = MatchResult(
                            is_match=is_match,
                            employee_code=template.employee_code,
                            template_id=template.template_id,
                            similarity=similarity,
                            distance=1.0 - similarity,  # Convert to distance
                            confidence=min(similarity / self.similarity_threshold, 1.0),
                            search_time_ms=search_time_ms
                        )
                        results.append(result)
            
            # If no results, return empty match
            if not results:
                results.append(MatchResult(
                    is_match=False,
                    employee_code=None,
                    template_id=None,
                    similarity=0.0,
                    distance=float('inf'),
                    confidence=0.0,
                    search_time_ms=search_time_ms
                ))
            
            return results
            
        except Exception as e:
            logger.error("Error in similarity search", error=str(e))
            return [MatchResult(
                is_match=False,
                employee_code=None,
                template_id=None,
                similarity=0.0,
                distance=float('inf'),
                confidence=0.0,
                search_time_ms=(time.time() - start_time) * 1000
            )]
    
    async def _background_sync_loop(self):
        """Background task for periodic synchronization."""
        while True:
            try:
                await asyncio.sleep(self.sync_interval)
                await self.sync_with_backend()
                
            except asyncio.CancelledError:
                logger.info("Background sync task cancelled")
                break
            except Exception as e:
                logger.error("Error in background sync", error=str(e))
    
    def get_stats(self) -> dict:
        """Get index performance statistics."""
        match_rate = (self.matches_found / self.searches_performed 
                     if self.searches_performed > 0 else 0)
        
        return {
            "template_count": len(self.templates),
            "index_size": self.index.ntotal if self.index else 0,
            "searches_performed": self.searches_performed,
            "matches_found": self.matches_found,
            "match_rate": match_rate,
            "similarity_threshold": self.similarity_threshold,
            "last_sync_time": self.last_sync_time,
            "sync_count": self.sync_count,
            "sync_interval": self.sync_interval
        }
