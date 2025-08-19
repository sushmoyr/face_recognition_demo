package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Shift entity representing work schedules.
 * 
 * Defines working hours, grace periods, and timezone information
 * for calculating attendance compliance.
 */
@Entity
@Table(name = "shifts")
@Data
@EqualsAndHashCode(exclude = {"schedules"})
@ToString(exclude = {"schedules"})
public class Shift {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    
    @Column(name = "grace_period_minutes", nullable = false)
    private Integer gracePeriodMinutes = 15;
    
    @Column(name = "is_overnight", nullable = false)
    private Boolean isOvernight = false;
    
    @Column(name = "timezone", nullable = false, length = 50)
    private String timezone = "Asia/Dhaka";
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @OneToMany(mappedBy = "shift", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EmployeeSchedule> schedules = new ArrayList<>();
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    /**
     * Check if a time is within the entry window.
     * 
     * @param time Time to check
     * @return true if within entry window
     */
    public boolean isWithinEntryWindow(LocalTime time) {
        LocalTime entryStart = startTime.minusMinutes(gracePeriodMinutes);
        LocalTime entryEnd = startTime.plusMinutes(gracePeriodMinutes * 2); // Extended window for entry
        
        if (!isOvernight) {
            return !time.isBefore(entryStart) && !time.isAfter(entryEnd);
        } else {
            // Handle overnight shifts
            return !time.isBefore(entryStart) || !time.isAfter(entryEnd);
        }
    }
    
    /**
     * Check if a time is within the exit window.
     * 
     * @param time Time to check
     * @return true if within exit window
     */
    public boolean isWithinExitWindow(LocalTime time) {
        LocalTime exitStart = endTime.minusMinutes(gracePeriodMinutes * 2); // Extended window for early exit
        LocalTime exitEnd = endTime.plusMinutes(gracePeriodMinutes);
        
        if (!isOvernight) {
            return !time.isBefore(exitStart) && !time.isAfter(exitEnd);
        } else {
            // Handle overnight shifts
            return !time.isBefore(exitStart) || !time.isAfter(exitEnd);
        }
    }
    
    /**
     * Check if entry time is considered late.
     * 
     * @param time Entry time
     * @return true if late
     */
    public boolean isLateEntry(LocalTime time) {
        return time.isAfter(startTime.plusMinutes(gracePeriodMinutes));
    }
    
    /**
     * Check if exit time is considered early leave.
     * 
     * @param time Exit time
     * @return true if early leave
     */
    public boolean isEarlyLeave(LocalTime time) {
        return time.isBefore(endTime.minusMinutes(gracePeriodMinutes));
    }
}
