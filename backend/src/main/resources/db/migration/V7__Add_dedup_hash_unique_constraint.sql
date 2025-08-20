-- Add unique constraint on dedup_hash to prevent duplicate recognition events
-- at the database level (as an additional safety net)

-- First, clean up any potential duplicates (shouldn't be any in new system)
-- Keep the first occurrence of any duplicates based on created_at
WITH duplicates AS (
    SELECT id, 
           ROW_NUMBER() OVER (
               PARTITION BY dedup_hash 
               ORDER BY created_at ASC
           ) as rn
    FROM recognition_events 
    WHERE dedup_hash IS NOT NULL
)
DELETE FROM recognition_events 
WHERE id IN (
    SELECT id FROM duplicates WHERE rn > 1
);

-- Add unique constraint on dedup_hash for non-null values
-- Note: NULL values are not considered duplicates in unique constraints
CREATE UNIQUE INDEX CONCURRENTLY idx_recognition_events_dedup_hash_unique 
ON recognition_events (dedup_hash) 
WHERE dedup_hash IS NOT NULL;

-- Add partial index on status for performance
CREATE INDEX CONCURRENTLY idx_recognition_events_status_captured_at 
ON recognition_events (status, captured_at);

-- Add index for deduplication queries
CREATE INDEX CONCURRENTLY idx_recognition_events_employee_device_captured 
ON recognition_events (employee_id, device_id, captured_at)
WHERE status != 'DUPLICATE';

-- Update table comment
COMMENT ON TABLE recognition_events IS 'Face recognition events with deduplication support. Uses dedup_hash for preventing duplicate attendance records.';
COMMENT ON COLUMN recognition_events.dedup_hash IS 'SHA-256 hash for deduplication based on image content, employee, device, and time window';
COMMENT ON COLUMN recognition_events.status IS 'Processing status: PENDING, PROCESSED, FAILED, or DUPLICATE';
