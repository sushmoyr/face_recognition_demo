package com.company.attendance.controller;

import com.company.attendance.dto.AttendanceReportRequest;
import com.company.attendance.dto.AttendanceResponse;
import com.company.attendance.entity.AttendanceRecord;
import com.company.attendance.service.AttendanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for attendance management operations.
 *
 * Provides attendance records, reports, and analytics with timezone support.
 */
@RestController
@RequestMapping("/api/attendance")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Attendance Management", description = "Attendance records, reports, and analytics")
@SecurityRequirement(name = "bearerAuth")
public class AttendanceController {

	private final AttendanceService attendanceService;

	/**
	 * Get daily attendance records
	 */
	@GetMapping("/daily")
	@Operation(summary = "Get daily attendance", description = "Get attendance records for a specific date")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
	public ResponseEntity<Page<AttendanceResponse>> getDailyAttendance(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) String department, @RequestParam(required = false) UUID employeeId,
			Pageable pageable) {

		Page<AttendanceRecord> records = attendanceService.findDailyAttendance(date, department, employeeId, pageable);
		Page<AttendanceResponse> response = records.map(this::convertToResponse);

		log.debug("Retrieved {} attendance records for date {}", records.getTotalElements(), date);
		return ResponseEntity.ok(response);
	}

	/**
	 * Get attendance records with date range filtering
	 */
	@GetMapping
	@Operation(summary = "List attendance records", description = "Get paginated attendance records with filtering")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
	public ResponseEntity<Page<AttendanceResponse>> getAttendanceRecords(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(required = false) String department, @RequestParam(required = false) UUID employeeId,
			@RequestParam(required = false) String status, Pageable pageable) {

		Page<AttendanceRecord> records = attendanceService.findAll(startDate, endDate, department, employeeId, status,
				pageable);
		Page<AttendanceResponse> response = records.map(this::convertToResponse);

		log.debug("Retrieved {} attendance records", records.getTotalElements());
		return ResponseEntity.ok(response);
	}

	/**
	 * Get attendance record by ID
	 */
	@GetMapping("/{id}")
	@Operation(summary = "Get attendance record", description = "Retrieve attendance record by ID")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
	public ResponseEntity<AttendanceResponse> getAttendanceById(@PathVariable UUID id) {
		AttendanceRecord record = attendanceService.findById(id)
			.orElseThrow(() -> new RuntimeException("Attendance record not found with id: " + id));

		return ResponseEntity.ok(convertToResponse(record));
	}

	/**
	 * Get employee attendance summary
	 */
	@GetMapping("/employee/{employeeId}/summary")
	@Operation(summary = "Get employee summary", description = "Get attendance summary for specific employee")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
	public ResponseEntity<?> getEmployeeAttendanceSummary(@PathVariable UUID employeeId,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

		try {
			var summary = attendanceService.getEmployeeAttendanceSummary(employeeId, startDate, endDate);
			return ResponseEntity.ok(summary);
		}
		catch (Exception e) {
			log.error("Error retrieving attendance summary for employee {}: {}", employeeId, e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving attendance summary");
		}
	}

	/**
	 * Generate attendance report
	 */
	@PostMapping("/reports")
	@Operation(summary = "Generate attendance report", description = "Generate detailed attendance report with filters")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR')")
	public ResponseEntity<?> generateAttendanceReport(@Valid @RequestBody AttendanceReportRequest request) {
		try {
			var report = attendanceService.generateAttendanceReport(request);

			log.info("Generated attendance report for period {} to {}", request.getStartDate(), request.getEndDate());

			return ResponseEntity.ok(report);

		}
		catch (Exception e) {
			log.error("Report generation failed: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Report generation failed: " + e.getMessage());
		}
	}

	/**
	 * Get attendance statistics
	 */
	@GetMapping("/stats")
	@Operation(summary = "Get attendance statistics", description = "Get attendance statistics and metrics")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
	public ResponseEntity<?> getAttendanceStatistics(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(required = false) String department) {

		try {
			var stats = attendanceService.getAttendanceStatistics(startDate, endDate, department);
			return ResponseEntity.ok(stats);
		}
		catch (Exception e) {
			log.error("Error retrieving attendance statistics: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("Error retrieving attendance statistics");
		}
	}

	/**
	 * Get late arrivals report
	 */
	@GetMapping("/late-arrivals")
	@Operation(summary = "Get late arrivals", description = "Get employees who arrived late")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
	public ResponseEntity<Page<AttendanceResponse>> getLateArrivals(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) String department, Pageable pageable) {

		Page<AttendanceRecord> records = attendanceService.findLateArrivals(date, department, pageable);
		Page<AttendanceResponse> response = records.map(this::convertToResponse);

		log.debug("Retrieved {} late arrival records for date {}", records.getTotalElements(), date);
		return ResponseEntity.ok(response);
	}

	/**
	 * Get absent employees
	 */
	@GetMapping("/absences")
	@Operation(summary = "Get absences", description = "Get employees who were absent")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
	public ResponseEntity<?> getAbsentEmployees(
			@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@RequestParam(required = false) String department) {

		try {
			var absences = attendanceService.findAbsentEmployees(date, department);
			return ResponseEntity.ok(absences);
		}
		catch (Exception e) {
			log.error("Error retrieving absent employees for date {}: {}", date, e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving absent employees");
		}
	}

	/**
	 * Manual attendance correction
	 */
	@PatchMapping("/{id}/correct")
	@Operation(summary = "Correct attendance", description = "Manually correct attendance record")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<AttendanceResponse> correctAttendance(@PathVariable UUID id,
			@RequestParam String correctionReason, @RequestParam(required = false) String newStatus) {

		try {
			AttendanceRecord correctedRecord = attendanceService.correctAttendance(id, correctionReason, newStatus);

			log.info("Corrected attendance record {} with reason: {}", id, correctionReason);
			return ResponseEntity.ok(convertToResponse(correctedRecord));

		}
		catch (Exception e) {
			log.error("Attendance correction failed for record {}: {}", id, e.getMessage());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
		}
	}

	/**
	 * Convert AttendanceRecord entity to AttendanceResponse DTO
	 */
	private AttendanceResponse convertToResponse(AttendanceRecord record) {
		AttendanceResponse response = new AttendanceResponse();
		response.setId(record.getId());
		response.setEmployeeId(record.getEmployee().getId());
		response.setEmployeeCode(record.getEmployee().getEmployeeCode());
		response.setEmployeeName(record.getEmployee().getFullName());
		response.setDeviceCode(record.getDevice().getDeviceCode());
		response.setDeviceLocation(record.getDevice().getLocation());
		response.setAttendanceDate(record.getAttendanceDate());
		response.setEventTime(record.getEventTime());
		response.setEventType(record.getEventType().name());
		response.setShiftName(record.getShift() != null ? record.getShift().getName() : null);
		response.setIsLate(record.getIsLate());
		response.setIsEarlyLeave(record.getIsEarlyLeave());
		response.setIsOvertime(record.getIsOvertime());
		response.setDurationMinutes(record.getDurationMinutes());
		response.setFormattedDuration(record.getFormattedDuration());
		response.setNotes(record.getNotes());
		response.setStatus(record.getStatus().name());

		// Set recognition event related fields if available
		if (record.getRecognitionEvent() != null) {
			response.setSnapshotUrl(record.getRecognitionEvent().getSnapshotUrl());
			response.setSimilarityScore(record.getRecognitionEvent().getSimilarityScore());
			response.setLivenessPassed(record.getRecognitionEvent().getLivenessPassed());
		}

		return response;
	}

}
