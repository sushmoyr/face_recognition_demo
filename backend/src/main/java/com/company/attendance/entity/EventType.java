package com.company.attendance.entity;

/**
 * Event type enumeration for attendance events.
 * 
 * Represents the type of attendance event being processed.
 */
public enum EventType {
    
    /**
     * Clock-in / Entry event
     */
    IN("Clock In"),
    
    /**
     * Clock-out / Exit event
     */
    OUT("Clock Out");
    
    private final String displayName;
    
    EventType(String displayName) {
        this.displayName = displayName;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Get the opposite event type.
     */
    public EventType getOpposite() {
        return this == IN ? OUT : IN;
    }
}
