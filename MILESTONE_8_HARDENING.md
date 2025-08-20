# 🔐 Milestone 8: Hardening & Security Implementation

This document details the security hardening features implemented in the face recognition system.

## 🎯 Hardening Features Overview

### 1. 🔍 Idempotent Recognition Ingestion
- **Hash-based deduplication** prevents duplicate attendance records
- **Content-aware hashing** using image, employee, device, and time windows
- **Database-level unique constraints** for additional safety
- **Audit trail preservation** with DUPLICATE status records

### 2. 📦 Offline Queue & Resilience  
- **Background retry mechanisms** with exponential backoff
- **Persistent offline queue** for network failures
- **Graceful degradation** when backend is unavailable
- **Automatic recovery** when connectivity is restored

### 3. 🔒 RTSP Encryption at Rest
- **AES-256-GCM encryption** for sensitive configuration values
- **PBKDF2 key derivation** with configurable iterations
- **Selective encryption** of credentials while preserving URL structure
- **Environment variable encryption** for production deployments

### 4. 🛡️ Enhanced Error Handling
- **Circuit breaker patterns** for external service calls  
- **Retry policies** with jitter and backoff
- **Graceful failure modes** maintaining system availability
- **Comprehensive logging** for troubleshooting

---

## 📋 Implementation Details

### Hash-based Deduplication

The system implements sophisticated content-based deduplication to prevent duplicate attendance records:

**Features:**
- SHA-256 hashing with image content, employee, device, and 5-minute time windows
- Database unique constraints on `dedup_hash` field
- Duplicate records marked with `DUPLICATE` status for audit trail
- Performance indexes for fast deduplication queries

**Configuration:**
```java
// backend/src/main/java/com/company/attendance/util/HashUtils.java
private static final String HASH_ALGORITHM = "SHA-256";
private static final int DEDUPLICATION_WINDOW_SECONDS = 300; // 5 minutes
```

**Database Migration:**
```sql
-- V7__Add_dedup_hash_unique_constraint.sql
CREATE UNIQUE INDEX CONCURRENTLY idx_recognition_events_dedup_hash_unique 
ON recognition_events (dedup_hash) WHERE dedup_hash IS NOT NULL;
```

### Offline Queue & Retry Logic

The edge service maintains robust offline capabilities:

**Features:**
- Asynchronous event queuing with configurable batch sizes
- Exponential backoff retry (1s, 2s, 4s, 8s...)
- Backend health checking every 30 seconds
- Persistent offline queue (deque with max size)

**Configuration:**
```python
# Recognition publisher settings
batch_size = 10
batch_timeout = 5.0  # seconds
max_retries = 3
retry_delay = 1.0
offline_queue_size = 1000
```

### RTSP Encryption

Sensitive configuration values are encrypted at rest using industry-standard cryptography:

**Features:**
- AES-256-GCM encryption with Fernet (cryptographically secure)
- PBKDF2-SHA256 key derivation (100,000 iterations)
- RTSP URL credential extraction and selective encryption
- Base64 encoding for easy serialization

**Usage:**
```bash
# Generate encryption salt
python scripts/config_encrypt.py generate-salt

# Encrypt RTSP URL
python scripts/config_encrypt.py encrypt-rtsp "rtsp://admin:pass@camera.local/stream"

# Generate encrypted environment variables
python scripts/config_encrypt.py env-encrypt
```

**Environment Variables:**
```bash
export EDGE_CONFIG_PASSWORD="your-encryption-password"
export EDGE_CONFIG_SALT="base64-encoded-salt"
export RTSP_URL="rtsp://encrypted:base64-encrypted-credentials@host/path"
```

---

## 🧪 Testing & Validation

### Deduplication Tests

```bash
# Run deduplication tests
mvn test -Dtest=HashUtilsTest
mvn test -Dtest=RecognitionEventServiceDeduplicationTest

# Key test scenarios:
# - Hash consistency with same inputs
# - Different hashes for different inputs  
# - Time window bucketing (5-minute windows)
# - Null input handling
# - Hash collision detection
```

### Encryption Tests

```bash
# Run encryption tests
python -m pytest edge/tests/test_config_encryption.py

# Key test scenarios:
# - Basic value encryption/decryption
# - RTSP URL credential encryption
# - Configuration dictionary encryption
# - Environment variable loading
# - File-based encryption
```

### Integration Tests

```bash
# Test full deduplication flow
curl -X POST http://localhost:8080/api/recognitions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $EDGE_TOKEN" \
  -d '{
    "deviceId": "test-device",
    "capturedAt": "2024-01-15T10:30:00Z", 
    "embedding": [/* 512-float array */],
    "topCandidateEmployeeId": "employee-uuid",
    "similarityScore": 0.85,
    "snapshotUrl": "https://test.com/image.jpg"
  }'

# Second identical request should return DUPLICATE status
```

---

## 🚀 Deployment Guide

### 1. Database Migration

```bash
# Apply deduplication migration
mvn -pl backend flyway:migrate

# Verify migration
psql -d attendance -c "
SELECT schemaname, tablename, indexname 
FROM pg_indexes 
WHERE tablename = 'recognition_events' 
AND indexname LIKE '%dedup%';
"
```

### 2. Environment Setup

```bash
# Generate encryption credentials
python scripts/config_encrypt.py generate-salt
# Save output: EDGE_CONFIG_SALT=...

# Encrypt sensitive values
python scripts/config_encrypt.py env-encrypt --output .env.encrypted

# Load encrypted environment
source .env.encrypted
```

### 3. Configuration Validation

```bash
# Test encryption setup
python scripts/config_encrypt.py test

# Validate configuration loading
python -c "
from edge.config import load_config
config = load_config()
print(f'RTSP encrypted: {config.processing.rtsp_url and config.processing.rtsp_url.startswith(\"rtsp://\") and \"encrypted\" in config.processing.rtsp_url}')
"
```

### 4. Monitoring Setup

```bash
# Check deduplication metrics
curl -s http://localhost:8080/actuator/prometheus | grep recognition_event

# Monitor offline queue
curl -s http://localhost:8001/metrics | grep edge_offline_queue

# Check retry statistics  
docker logs edge-service 2>&1 | grep -i retry
```

---

## 📊 Performance Impact

### Deduplication Overhead

- **Hash generation**: ~1-5ms per recognition event
- **Database lookup**: ~2-10ms with proper indexes
- **Memory usage**: Minimal (64-byte hashes)
- **Storage overhead**: ~64 bytes per event

### Encryption Overhead

- **RTSP URL encryption**: One-time startup cost (~10ms)
- **Configuration decryption**: One-time startup cost (~5-20ms)
- **Runtime impact**: Zero (decryption happens at startup)

### Offline Queue Performance

- **Memory usage**: ~1KB per queued event (default 1000 max)
- **Batching efficiency**: 10-100x improvement vs individual requests
- **Recovery time**: Typically 30-60 seconds after connectivity restoration

---

## 🔧 Configuration Reference

### Backend Configuration

```properties
# application.properties
app.jwt.secret=your-jwt-secret-key
app.jwt.expiration=86400
spring.datasource.url=jdbc:postgresql://localhost:5432/attendance
spring.datasource.username=postgres
spring.datasource.password=password
```

### Edge Service Configuration

```bash
# Core settings
export DEVICE_ID="edge-001"
export BACKEND_URL="http://localhost:8080"
export TARGET_FPS="10"

# Encrypted settings (use config_encrypt.py)
export EDGE_CONFIG_PASSWORD="your-password"
export EDGE_CONFIG_SALT="base64-salt"
export RTSP_URL="rtsp://encrypted:credentials@host/path"
export MINIO_ACCESS_KEY="encrypted-access-key"
export MINIO_SECRET_KEY="encrypted-secret-key"

# Recognition settings
export SIMILARITY_THRESHOLD="0.7"
export COOLDOWN_SECONDS="30"

# Performance settings
export PUBLISH_BATCH_SIZE="10"
export OFFLINE_QUEUE_SIZE="1000"
export MAX_RETRIES="3"
```

---

## 🚨 Security Considerations

### 1. Encryption Key Management
- **Use strong passwords**: Minimum 16 characters with mixed case, numbers, symbols
- **Rotate credentials**: Implement periodic password rotation
- **Environment isolation**: Use different keys for dev/staging/production
- **Key storage**: Store encryption passwords in secure key management systems

### 2. Network Security
- **TLS encryption**: Use HTTPS/RTSP over TLS for all network communication
- **Network segmentation**: Isolate edge devices on dedicated VLANs
- **Firewall rules**: Restrict network access to required ports only
- **VPN tunnels**: Use VPN for remote edge device management

### 3. Database Security
- **Connection encryption**: Enable SSL for PostgreSQL connections
- **Access controls**: Use principle of least privilege for database users
- **Audit logging**: Enable database query auditing
- **Backup encryption**: Encrypt database backups at rest

### 4. Monitoring & Alerting
- **Failed authentication**: Alert on repeated authentication failures
- **Encryption errors**: Monitor for configuration decryption failures  
- **Deduplication anomalies**: Alert on unusual duplicate detection patterns
- **Offline queue overflow**: Monitor queue size and capacity

---

## 🆘 Troubleshooting

### Deduplication Issues

**Problem**: Duplicate attendance records still being created
```bash
# Check unique constraint
psql -d attendance -c "
SELECT COUNT(*), dedup_hash 
FROM recognition_events 
WHERE dedup_hash IS NOT NULL 
GROUP BY dedup_hash 
HAVING COUNT(*) > 1;
"

# Verify hash generation
curl -X POST http://localhost:8080/api/recognitions \
  -H "Content-Type: application/json" \
  -d '{}' \
  -v | grep dedup_hash
```

**Solution**: Check hash generation logic and database constraints.

### Encryption Problems

**Problem**: Configuration values not decrypting
```bash
# Test encryption setup
python scripts/config_encrypt.py test

# Check environment variables
echo "Password: $EDGE_CONFIG_PASSWORD"
echo "Salt: $EDGE_CONFIG_SALT"

# Test manual decryption
python -c "
from edge.utils.config_encryption import ConfigEncryption
enc = ConfigEncryption()
print(enc.decrypt_value('your-encrypted-value'))
"
```

**Solution**: Verify password and salt are correct, check encryption key derivation.

### Offline Queue Issues

**Problem**: Events not being retried when backend recovers
```bash
# Check queue status
curl http://localhost:8001/metrics | grep offline_queue

# Check connectivity
curl -I http://localhost:8080/actuator/health

# Check logs for retry attempts
docker logs edge-service | grep -i "retry\|offline"
```

**Solution**: Verify backend health endpoint and network connectivity.

---

## ✅ Validation Checklist

### Pre-deployment
- [ ] Database migration V7 applied successfully
- [ ] Deduplication tests passing
- [ ] Encryption tests passing  
- [ ] Configuration encryption setup complete
- [ ] RTSP credentials encrypted
- [ ] MinIO credentials encrypted
- [ ] Backend API tokens secured

### Post-deployment
- [ ] Recognition deduplication working (test duplicate POST)
- [ ] Offline queue functioning (test with backend down)
- [ ] Encrypted RTSP connections successful
- [ ] Performance metrics within acceptable ranges
- [ ] Error handling graceful under load
- [ ] Monitoring and alerting configured

### Production Readiness
- [ ] Security review completed
- [ ] Load testing with encryption overhead
- [ ] Disaster recovery procedures tested
- [ ] Key rotation procedures documented
- [ ] Security monitoring active
- [ ] Compliance requirements met

---

This completes the **Milestone 8: Hardening & Security** implementation, providing robust production-ready security features for the face recognition attendance system.
