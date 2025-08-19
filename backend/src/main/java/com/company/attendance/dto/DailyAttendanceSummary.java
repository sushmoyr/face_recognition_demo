package com.company.attendance.dto;

import lombok.Data;

/**
 * Response DTO for daily attendance summary.
 */
@Data
public class DailyAttendanceSummary {
    
    private String date;
    private Long totalEmployeesIn;
    private Long totalEmployeesOut;
    private Long lateArrivals;
    private Long earlyDepartures;
    private Long incompleteRecords;
    private Double averageWorkingHours;
    private Long totalRecognitionEvents;
    private Long successfulMatches;
}
