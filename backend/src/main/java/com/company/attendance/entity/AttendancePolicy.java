package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Attendance policy entity for configurable attendance rules.
 * 
 * Defines time windows, grace periods, cooldowns, and other
 * business rules for attendance processing on a per-shift basis.
 */
@Entity
@Table(name = "attendance_policies")
@Data
@EqualsAndHashCode(exclude = {"shift"})
@ToString(exclude = {"shift"})
public class AttendancePolicy {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id", nullable = false)
    private Shift shift;
    
    // Entry/Clock-in Window Configuration
    @Column(name = "entry_window_start_minutes", nullable = false)
    private Integer entryWindowStartMinutes = 30; // 30 minutes before shift start
    
    @Column(name = "entry_window_end_minutes", nullable = false)
    private Integer entryWindowEndMinutes = 120; // 2 hours after shift start
    
    // Exit/Clock-out Window Configuration
    @Column(name = "exit_window_start_minutes", nullable = false)
    private Integer exitWindowStartMinutes = 30; // 30 minutes before shift end
    
    @Column(name = "exit_window_end_minutes", nullable = false)
    private Integer exitWindowEndMinutes = 120; // 2 hours after shift end
    
    // Grace Period Configuration
    @Column(name = "early_arrival_grace_minutes", nullable = false)
    private Integer earlyArrivalGraceMinutes = 15; // Don't mark as early if within 15 min of shift start
    
    @Column(name = "late_arrival_grace_minutes", nullable = false)
    private Integer lateArrivalGraceMinutes = 10; // Don't mark as late if within 10 min after shift start
    
    @Column(name = "early_departure_grace_minutes", nullable = false)
    private Integer earlyDepartureGraceMinutes = 15; // Don't mark as early departure if within 15 min of shift end
    
    @Column(name = "overtime_threshold_minutes", nullable = false)
    private Integer overtimeThresholdMinutes = 30; // Start counting overtime after 30 min past shift end
    
    // Cooldown Configuration
    @Column(name = "in_to_out_cooldown_minutes", nullable = false)
    private Integer inToOutCooldownMinutes = 30; // Minimum 30 minutes between IN and OUT
    
    @Column(name = "out_to_in_cooldown_minutes", nullable = false)
    private Integer outToInCooldownMinutes = 15; // Minimum 15 minutes between OUT and IN
    
    // Break Window Configuration (optional)
    @Column(name = "break_start_time")
    private LocalTime breakStartTime;
    
    @Column(name = "break_end_time")
    private LocalTime breakEndTime;
    
    @Column(name = "break_duration_minutes")
    private Integer breakDurationMinutes;
    
    // Weekend and Holiday Configuration
    @Column(name = "allow_weekend_attendance", nullable = false)
    private Boolean allowWeekendAttendance = false;
    
    @Column(name = "allow_holiday_attendance", nullable = false)
    private Boolean allowHolidayAttendance = false;
    
    // Validation Configuration
    @Column(name = "require_both_in_out", nullable = false)
    private Boolean requireBothInOut = true;
    
    @Column(name = "auto_clock_out_enabled", nullable = false)
    private Boolean autoClockOutEnabled = false;
    
    @Column(name = "auto_clock_out_time")
    private LocalTime autoClockOutTime;
    
    // Status flags
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "is_default", nullable = false)
    private Boolean isDefault = false;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    /**
     * Calculate the entry window start time for the shift.
     */
    public LocalTime getEntryWindowStart() {
        return shift.getStartTime().minusMinutes(entryWindowStartMinutes);
    }
    
    /**
     * Calculate the entry window end time for the shift.
     */
    public LocalTime getEntryWindowEnd() {
        return shift.getStartTime().plusMinutes(entryWindowEndMinutes);
    }
    
    /**
     * Calculate the exit window start time for the shift.
     */
    public LocalTime getExitWindowStart() {
        return shift.getEndTime().minusMinutes(exitWindowStartMinutes);
    }
    
    /**
     * Calculate the exit window end time for the shift.
     */
    public LocalTime getExitWindowEnd() {
        return shift.getEndTime().plusMinutes(exitWindowEndMinutes);
    }
    
    /**
     * Check if a time is within the entry window.
     */
    public boolean isWithinEntryWindow(LocalTime time) {
        LocalTime windowStart = getEntryWindowStart();
        LocalTime windowEnd = getEntryWindowEnd();
        
        if (!shift.getIsOvernight()) {
            return !time.isBefore(windowStart) && !time.isAfter(windowEnd);
        } else {
            // Handle overnight shifts
            return !time.isBefore(windowStart) || !time.isAfter(windowEnd);
        }
    }
    
    /**
     * Check if a time is within the exit window.
     */
    public boolean isWithinExitWindow(LocalTime time) {
        LocalTime windowStart = getExitWindowStart();
        LocalTime windowEnd = getExitWindowEnd();
        
        if (!shift.getIsOvernight()) {
            return !time.isBefore(windowStart) && !time.isAfter(windowEnd);
        } else {
            // Handle overnight shifts
            return !time.isBefore(windowStart) || !time.isAfter(windowEnd);
        }
    }
    
    /**
     * Determine attendance status based on arrival time.
     */
    public AttendanceStatus determineArrivalStatus(LocalTime arrivalTime) {
        LocalTime shiftStart = shift.getStartTime();
        LocalTime earlyThreshold = shiftStart.minusMinutes(earlyArrivalGraceMinutes);
        LocalTime lateThreshold = shiftStart.plusMinutes(lateArrivalGraceMinutes);
        
        if (arrivalTime.isBefore(earlyThreshold)) {
            return AttendanceStatus.EARLY_IN;
        } else if (arrivalTime.isAfter(lateThreshold)) {
            return AttendanceStatus.LATE_IN;
        } else {
            return AttendanceStatus.ON_TIME_IN;
        }
    }
    
    /**
     * Determine attendance status based on departure time.
     */
    public AttendanceStatus determineDepartureStatus(LocalTime departureTime) {
        LocalTime shiftEnd = shift.getEndTime();
        LocalTime earlyThreshold = shiftEnd.minusMinutes(earlyDepartureGraceMinutes);
        LocalTime overtimeThreshold = shiftEnd.plusMinutes(overtimeThresholdMinutes);
        
        if (departureTime.isBefore(earlyThreshold)) {
            return AttendanceStatus.EARLY_OUT;
        } else if (departureTime.isAfter(overtimeThreshold)) {
            return AttendanceStatus.OVERTIME_OUT;
        } else {
            return AttendanceStatus.ON_TIME_OUT;
        }
    }
    
    /**
     * Check if it's within break time window.
     */
    public boolean isWithinBreakWindow(LocalTime time) {
        if (breakStartTime == null || breakEndTime == null) {
            return false;
        }
        
        if (!shift.getIsOvernight()) {
            return !time.isBefore(breakStartTime) && !time.isAfter(breakEndTime);
        } else {
            // Handle overnight shifts
            return !time.isBefore(breakStartTime) || !time.isAfter(breakEndTime);
        }
    }
}
