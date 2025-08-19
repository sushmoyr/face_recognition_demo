package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Attendance record entity representing processed attendance events.
 * 
 * Generated from recognition events and contains calculated attendance
 * information including late arrivals, early departures, and duration.
 */
@Entity
@Table(name = "attendance_records")
@Data
@EqualsAndHashCode(exclude = {"employee", "device", "recognitionEvent", "shift"})
@ToString(exclude = {"employee", "device", "recognitionEvent", "shift"})
public class AttendanceRecord {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recognition_event_id")
    private RecognitionEvent recognitionEvent;
    
    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;
    
    @Column(name = "event_time", nullable = false)
    private Instant eventTime;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false)
    private EventType eventType;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private Shift shift;
    
    @Column(name = "is_late")
    private Boolean isLate = false;
    
    @Column(name = "is_early_leave")
    private Boolean isEarlyLeave = false;
    
    @Column(name = "is_overtime")
    private Boolean isOvertime = false;
    
    @Column(name = "duration_minutes")
    private Integer durationMinutes;
    
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AttendanceStatus status = AttendanceStatus.VALID;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    /**
     * Check if this is an entry event.
     * 
     * @return true if event type is IN
     */
    public boolean isEntry() {
        return eventType == EventType.IN;
    }
    
    /**
     * Check if this is an exit event.
     * 
     * @return true if event type is OUT
     */
    public boolean isExit() {
        return eventType == EventType.OUT;
    }
    
    /**
     * Check if this record has any compliance issues.
     * 
     * @return true if late, early leave, or other issues
     */
    public boolean hasComplianceIssues() {
        return Boolean.TRUE.equals(isLate) || 
               Boolean.TRUE.equals(isEarlyLeave) || 
               status != AttendanceStatus.VALID;
    }
    
    /**
     * Get duration as formatted string.
     * 
     * @return Duration in HH:MM format or null
     */
    public String getFormattedDuration() {
        if (durationMinutes == null) {
            return null;
        }
        int hours = durationMinutes / 60;
        int minutes = durationMinutes % 60;
        return String.format("%02d:%02d", hours, minutes);
    }
    
    public enum EventType {
        IN, OUT
    }
    
    public enum AttendanceStatus {
        VALID, INVALID, ADJUSTED, DISPUTED
    }
}
