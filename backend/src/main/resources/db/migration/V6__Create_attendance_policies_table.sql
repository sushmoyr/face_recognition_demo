-- Migration: Add attendance_policies table for configurable attendance rules
-- Author: Face Attendance System
-- Date: 2024-01-15

-- Create attendance_policies table
CREATE TABLE attendance_policies (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    description TEXT,
    shift_id UUID NOT NULL,
    
    -- Entry/Clock-in Window Configuration (minutes relative to shift start/end)
    entry_window_start_minutes INTEGER NOT NULL DEFAULT 30,  -- 30 minutes before shift start
    entry_window_end_minutes INTEGER NOT NULL DEFAULT 120,   -- 2 hours after shift start
    
    -- Exit/Clock-out Window Configuration (minutes relative to shift end)
    exit_window_start_minutes INTEGER NOT NULL DEFAULT 30,   -- 30 minutes before shift end
    exit_window_end_minutes INTEGER NOT NULL DEFAULT 120,    -- 2 hours after shift end
    
    -- Grace Period Configuration (minutes)
    early_arrival_grace_minutes INTEGER NOT NULL DEFAULT 15,    -- Don't mark as early if within 15 min
    late_arrival_grace_minutes INTEGER NOT NULL DEFAULT 10,     -- Don't mark as late if within 10 min
    early_departure_grace_minutes INTEGER NOT NULL DEFAULT 15,  -- Don't mark as early departure if within 15 min
    overtime_threshold_minutes INTEGER NOT NULL DEFAULT 30,     -- Start counting overtime after 30 min
    
    -- Cooldown Configuration (minutes between events)
    in_to_out_cooldown_minutes INTEGER NOT NULL DEFAULT 30,  -- Minimum 30 minutes between IN and OUT
    out_to_in_cooldown_minutes INTEGER NOT NULL DEFAULT 15,  -- Minimum 15 minutes between OUT and IN
    
    -- Break Window Configuration (optional)
    break_start_time TIME,
    break_end_time TIME,
    break_duration_minutes INTEGER,
    
    -- Weekend and Holiday Configuration
    allow_weekend_attendance BOOLEAN NOT NULL DEFAULT FALSE,
    allow_holiday_attendance BOOLEAN NOT NULL DEFAULT FALSE,
    
    -- Validation Configuration
    require_both_in_out BOOLEAN NOT NULL DEFAULT TRUE,
    auto_clock_out_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    auto_clock_out_time TIME,
    
    -- Status flags
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    
    -- Constraints
    CONSTRAINT fk_attendance_policy_shift FOREIGN KEY (shift_id) REFERENCES shifts(id) ON DELETE CASCADE,
    CONSTRAINT chk_entry_window_valid CHECK (entry_window_start_minutes >= 0 AND entry_window_end_minutes >= 0),
    CONSTRAINT chk_exit_window_valid CHECK (exit_window_start_minutes >= 0 AND exit_window_end_minutes >= 0),
    CONSTRAINT chk_grace_periods_valid CHECK (
        early_arrival_grace_minutes >= 0 AND 
        late_arrival_grace_minutes >= 0 AND 
        early_departure_grace_minutes >= 0 AND 
        overtime_threshold_minutes >= 0
    ),
    CONSTRAINT chk_cooldown_valid CHECK (in_to_out_cooldown_minutes >= 0 AND out_to_in_cooldown_minutes >= 0),
    CONSTRAINT chk_break_times_valid CHECK (
        (break_start_time IS NULL AND break_end_time IS NULL AND break_duration_minutes IS NULL) OR
        (break_start_time IS NOT NULL AND break_end_time IS NOT NULL AND break_duration_minutes IS NOT NULL)
    ),
    CONSTRAINT chk_auto_clock_out_valid CHECK (
        (auto_clock_out_enabled = FALSE) OR 
        (auto_clock_out_enabled = TRUE AND auto_clock_out_time IS NOT NULL)
    )
);

-- Create indexes for performance
CREATE INDEX idx_attendance_policies_shift_id ON attendance_policies(shift_id);
CREATE INDEX idx_attendance_policies_active ON attendance_policies(is_active);
CREATE INDEX idx_attendance_policies_default ON attendance_policies(is_default, is_active);
CREATE UNIQUE INDEX idx_attendance_policies_shift_unique ON attendance_policies(shift_id) WHERE is_active = TRUE;
CREATE UNIQUE INDEX idx_attendance_policies_default_unique ON attendance_policies(is_default) WHERE is_default = TRUE AND is_active = TRUE;

-- Add trigger for updated_at
CREATE TRIGGER trigger_attendance_policies_updated_at 
    BEFORE UPDATE ON attendance_policies 
    FOR EACH ROW 
    EXECUTE FUNCTION update_updated_at_column();

-- Add foreign key to employees table for shift relationship (if not exists)
DO $$ 
BEGIN 
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns 
        WHERE table_name = 'employees' AND column_name = 'shift_id'
    ) THEN
        ALTER TABLE employees ADD COLUMN shift_id UUID REFERENCES shifts(id);
        CREATE INDEX idx_employees_shift_id ON employees(shift_id);
    END IF;
END $$;

-- Insert default attendance policy for existing shifts
INSERT INTO attendance_policies (name, description, shift_id, is_default)
SELECT 
    'Default Policy for ' || COALESCE(s.name, 'Shift ' || s.id::TEXT),
    'Default attendance policy with standard business rules',
    s.id,
    NOT EXISTS (SELECT 1 FROM shifts s2 WHERE s2.id != s.id LIMIT 1) -- Set as default only if it's the only shift
FROM shifts s
WHERE NOT EXISTS (
    SELECT 1 FROM attendance_policies ap 
    WHERE ap.shift_id = s.id AND ap.is_active = TRUE
);

-- Comment on table
COMMENT ON TABLE attendance_policies IS 'Configurable attendance policies with time windows, grace periods, and business rules';
COMMENT ON COLUMN attendance_policies.entry_window_start_minutes IS 'Minutes before shift start when clock-in is allowed';
COMMENT ON COLUMN attendance_policies.entry_window_end_minutes IS 'Minutes after shift start when clock-in is allowed';
COMMENT ON COLUMN attendance_policies.exit_window_start_minutes IS 'Minutes before shift end when clock-out is allowed';
COMMENT ON COLUMN attendance_policies.exit_window_end_minutes IS 'Minutes after shift end when clock-out is allowed';
COMMENT ON COLUMN attendance_policies.late_arrival_grace_minutes IS 'Grace period in minutes before marking as late';
COMMENT ON COLUMN attendance_policies.early_departure_grace_minutes IS 'Grace period in minutes before marking as early departure';
COMMENT ON COLUMN attendance_policies.overtime_threshold_minutes IS 'Minutes after shift end before counting as overtime';
