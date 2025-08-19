-- Create core tables for the attendance system

-- Employees table
CREATE TABLE employees (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_code VARCHAR(50) UNIQUE NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255),
    department VARCHAR(100),
    position VARCHAR(100),
    hire_date DATE,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Devices table (cameras/edge nodes)
CREATE TABLE devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_code VARCHAR(50) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    location VARCHAR(255),
    rtsp_url_encrypted TEXT, -- Encrypted RTSP URL
    device_type VARCHAR(20) NOT NULL DEFAULT 'CAMERA' CHECK (device_type IN ('CAMERA', 'TERMINAL', 'MOBILE')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'INACTIVE', 'MAINTENANCE')),
    last_seen_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Face templates table with pgvector embeddings
CREATE TABLE face_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    embedding vector(512) NOT NULL, -- 512-dimensional face embedding
    quality_score FLOAT NOT NULL CHECK (quality_score >= 0 AND quality_score <= 1),
    source_image_url TEXT,
    extraction_model VARCHAR(100),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Recognition events table
CREATE TABLE recognition_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL REFERENCES devices(id),
    employee_id UUID REFERENCES employees(id), -- NULL if no match found
    captured_at TIMESTAMP WITH TIME ZONE NOT NULL,
    embedding vector(512), -- The detected face embedding
    similarity_score FLOAT, -- Similarity to matched template
    liveness_score FLOAT,
    liveness_passed BOOLEAN,
    face_box_x INTEGER,
    face_box_y INTEGER,
    face_box_width INTEGER,
    face_box_height INTEGER,
    snapshot_url TEXT,
    processing_duration_ms INTEGER,
    dedup_hash VARCHAR(64), -- For deduplication
    status VARCHAR(20) NOT NULL DEFAULT 'PROCESSED' CHECK (status IN ('PENDING', 'PROCESSED', 'FAILED', 'DUPLICATE')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Shifts table
CREATE TABLE shifts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(100) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    grace_period_minutes INTEGER NOT NULL DEFAULT 15,
    is_overnight BOOLEAN NOT NULL DEFAULT false, -- For shifts that span midnight
    timezone VARCHAR(50) NOT NULL DEFAULT 'Asia/Dhaka',
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Employee shift schedules
CREATE TABLE employee_schedules (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES employees(id) ON DELETE CASCADE,
    shift_id UUID NOT NULL REFERENCES shifts(id),
    day_of_week INTEGER NOT NULL CHECK (day_of_week >= 1 AND day_of_week <= 7), -- 1=Monday, 7=Sunday
    effective_from DATE NOT NULL,
    effective_until DATE,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (employee_id, shift_id, day_of_week, effective_from)
);

-- Attendance records table
CREATE TABLE attendance_records (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    employee_id UUID NOT NULL REFERENCES employees(id),
    device_id UUID NOT NULL REFERENCES devices(id),
    recognition_event_id UUID REFERENCES recognition_events(id),
    attendance_date DATE NOT NULL,
    event_time TIMESTAMP WITH TIME ZONE NOT NULL,
    event_type VARCHAR(10) NOT NULL CHECK (event_type IN ('IN', 'OUT')),
    shift_id UUID REFERENCES shifts(id),
    is_late BOOLEAN DEFAULT false,
    is_early_leave BOOLEAN DEFAULT false,
    is_overtime BOOLEAN DEFAULT false,
    duration_minutes INTEGER, -- For OUT events, duration since last IN
    notes TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'VALID' CHECK (status IN ('VALID', 'INVALID', 'ADJUSTED', 'DISPUTED')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Users table for authentication
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username VARCHAR(50) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    role VARCHAR(20) NOT NULL CHECK (role IN ('ADMIN', 'HR', 'VIEWER', 'EDGE_NODE')),
    is_active BOOLEAN NOT NULL DEFAULT true,
    last_login_at TIMESTAMP WITH TIME ZONE,
    password_changed_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Audit log table
CREATE TABLE audit_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID REFERENCES users(id),
    action VARCHAR(50) NOT NULL,
    entity_type VARCHAR(50) NOT NULL,
    entity_id UUID,
    old_values JSONB,
    new_values JSONB,
    ip_address INET,
    user_agent TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for performance
CREATE INDEX idx_employees_code ON employees(employee_code);
CREATE INDEX idx_employees_status ON employees(status);
CREATE INDEX idx_devices_code ON devices(device_code);
CREATE INDEX idx_devices_status ON devices(status);
CREATE INDEX idx_face_templates_employee ON face_templates(employee_id);
CREATE INDEX idx_face_templates_active ON face_templates(is_active);
CREATE INDEX idx_recognition_events_device ON recognition_events(device_id);
CREATE INDEX idx_recognition_events_employee ON recognition_events(employee_id);
CREATE INDEX idx_recognition_events_captured_at ON recognition_events(captured_at);
CREATE INDEX idx_recognition_events_dedup_hash ON recognition_events(dedup_hash);
CREATE INDEX idx_attendance_records_employee ON attendance_records(employee_id);
CREATE INDEX idx_attendance_records_date ON attendance_records(attendance_date);
CREATE INDEX idx_attendance_records_event_time ON attendance_records(event_time);
CREATE INDEX idx_users_username ON users(username);

-- Vector similarity search indexes (using cosine distance)
CREATE INDEX idx_face_templates_embedding ON face_templates 
USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);

-- Functions for updating timestamps
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Triggers for automatic timestamp updates
CREATE TRIGGER update_employees_updated_at BEFORE UPDATE ON employees 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_devices_updated_at BEFORE UPDATE ON devices 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_face_templates_updated_at BEFORE UPDATE ON face_templates 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_shifts_updated_at BEFORE UPDATE ON shifts 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_employee_schedules_updated_at BEFORE UPDATE ON employee_schedules 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_attendance_records_updated_at BEFORE UPDATE ON attendance_records 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users 
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Insert default admin user (password: admin123)
INSERT INTO users (username, password_hash, email, first_name, last_name, role) 
VALUES (
    'admin', 
    '$2a$10$N4lJjFhsS5NNRlNzFkzIFeu1EDmVa5oJLZkfZrCqOdLdkKGJJnLYO', -- BCrypt hash of 'admin123'
    'admin@company.com',
    'System',
    'Administrator',
    'ADMIN'
);

-- Insert default general shift
INSERT INTO shifts (name, start_time, end_time, grace_period_minutes, timezone) 
VALUES ('General Shift', '09:00:00', '18:00:00', 15, 'Asia/Dhaka');
