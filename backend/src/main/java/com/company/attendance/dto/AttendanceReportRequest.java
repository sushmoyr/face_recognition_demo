package com.company.attendance.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for generating attendance reports.
 *
 * Contains report parameters and filters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceReportRequest {

	@NotNull(message = "Start date is required")
	private LocalDate startDate;

	@NotNull(message = "End date is required")
	private LocalDate endDate;

	private List<UUID> employeeIds;

	private List<String> departments;

	private String reportType; // SUMMARY, DETAILED, EXCEPTIONS

	private String format; // JSON, CSV, PDF

	private Boolean includeAbsences;

	private Boolean includeLateArrivals;

	private Boolean includeOvertimeHours;

}
