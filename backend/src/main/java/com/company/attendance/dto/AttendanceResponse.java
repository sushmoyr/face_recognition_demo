package com.company.attendance.dto;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for attendance record information.
 */
@Data
public class AttendanceResponse {
    
    private UUID id;
    private UUID employeeId;
    private String employeeCode;
    private String employeeName;
    private String deviceCode;
    private String deviceLocation;
    private LocalDate attendanceDate;
    private Instant eventTime;
    private String eventType; // IN/OUT
    private String shiftName;
    private Boolean isLate;
    private Boolean isEarlyLeave;
    private Boolean isOvertime;
    private Integer durationMinutes;
    private String formattedDuration;
    private String notes;
    private String status;
    private String snapshotUrl;
    private Double similarityScore;
    private Boolean livenessPassed;
}
