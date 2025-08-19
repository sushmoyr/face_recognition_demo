package com.company.attendance.entity;

/**
 * Detailed attendance status enumeration.
 * 
 * Provides granular status tracking beyond simple IN/OUT events,
 * including timing compliance and overtime detection.
 */
public enum AttendanceStatus {
    
    // Entry status
    EARLY_IN("Early Arrival"),
    ON_TIME_IN("On-Time Arrival"), 
    LATE_IN("Late Arrival"),
    
    // Exit status
    EARLY_OUT("Early Departure"),
    ON_TIME_OUT("On-Time Departure"),
    OVERTIME_OUT("Overtime Departure"),
    
    // Break status
    BREAK_START("Break Start"),
    BREAK_END("Break End"),
    
    // Special status
    AUTO_OUT("Auto Clock-Out"),
    MANUAL_ADJUSTMENT("Manual Adjustment"),
    
    // Violation status
    MISSED_IN("Missed Clock-In"),
    MISSED_OUT("Missed Clock-Out"),
    DUPLICATE("Duplicate Entry");
    
    private final String displayName;
    
    AttendanceStatus(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public boolean isClockIn() {
        return this == EARLY_IN || this == ON_TIME_IN || this == LATE_IN;
    }
    
    public boolean isClockOut() {
        return this == EARLY_OUT || this == ON_TIME_OUT || this == OVERTIME_OUT;
    }
    
    public boolean isCompliant() {
        return this == ON_TIME_IN || this == ON_TIME_OUT;
    }
    
    public boolean requiresAttention() {
        return this == EARLY_IN || this == LATE_IN || this == EARLY_OUT || 
               this == MISSED_IN || this == MISSED_OUT;
    }
}
