package com.company.attendance.controller;

import com.company.attendance.dto.RecognitionEventRequest;
import com.company.attendance.dto.RecognitionEventResponse;
import com.company.attendance.entity.RecognitionEvent;
import com.company.attendance.service.RecognitionEventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST controller for recognition event operations.
 *
 * Handles face recognition events from edge devices and provides recognition history and
 * analytics.
 */
@RestController
@RequestMapping("/api/recognitions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Recognition Events", description = "Face recognition event management and history")
@SecurityRequirement(name = "bearerAuth")
public class RecognitionController {

	private final RecognitionEventService recognitionEventService;

	/**
	 * Ingest recognition event from edge device
	 */
	@PostMapping
	@Operation(summary = "Ingest recognition", description = "Submit face recognition event from edge device")
	@PreAuthorize("hasAnyRole('ADMIN', 'EDGE_NODE')")
	public ResponseEntity<?> ingestRecognition(@Valid @RequestBody RecognitionEventRequest request) {
		try {
			RecognitionEvent event = recognitionEventService.processRecognition(request);

			log.info("Recognition event processed: {} for employee {}", event.getId(),
					event.getEmployee() != null ? event.getEmployee().getEmployeeCode() : "UNKNOWN");

			return ResponseEntity.status(HttpStatus.CREATED).body(convertToResponse(event));

		}
		catch (Exception e) {
			log.error("Recognition processing failed: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body("Recognition processing failed: " + e.getMessage());
		}
	}

	/**
	 * Get recognition events with pagination and filtering
	 */
	@GetMapping
	@Operation(summary = "List recognition events",
			description = "Get paginated list of recognition events with filtering")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
	public ResponseEntity<Page<RecognitionEventResponse>> getRecognitionEvents(
			@RequestParam(required = false) UUID employeeId, @RequestParam(required = false) UUID deviceId,
			@RequestParam(required = false) LocalDate startDate, @RequestParam(required = false) LocalDate endDate,
			@RequestParam(required = false) Double minConfidence, Pageable pageable) {

		Page<RecognitionEvent> events = recognitionEventService.findAll(employeeId, deviceId, startDate, endDate,
				minConfidence, pageable);
		Page<RecognitionEventResponse> response = events.map(this::convertToResponse);

		log.debug("Retrieved {} recognition events", events.getTotalElements());
		return ResponseEntity.ok(response);
	}

	/**
	 * Get recognition event by ID
	 */
	@GetMapping("/{id}")
	@Operation(summary = "Get recognition event", description = "Retrieve recognition event by ID")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
	public ResponseEntity<RecognitionEventResponse> getRecognitionById(@PathVariable UUID id) {
		RecognitionEvent event = recognitionEventService.findById(id)
			.orElseThrow(() -> new RuntimeException("Recognition event not found with id: " + id));

		return ResponseEntity.ok(convertToResponse(event));
	}

	/**
	 * Get recognition statistics
	 */
	@GetMapping("/stats")
	@Operation(summary = "Get recognition statistics", description = "Get recognition event statistics and analytics")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
	public ResponseEntity<?> getRecognitionStats(@RequestParam(required = false) LocalDate startDate,
			@RequestParam(required = false) LocalDate endDate) {

		try {
			var stats = recognitionEventService.getRecognitionStatistics(startDate, endDate);
			return ResponseEntity.ok(stats);
		}
		catch (Exception e) {
			log.error("Error retrieving recognition statistics: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("Error retrieving recognition statistics");
		}
	}

	/**
	 * Get unmatched recognitions (unknown faces)
	 */
	@GetMapping("/unmatched")
	@Operation(summary = "Get unmatched recognitions", description = "List recognition events for unknown faces")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR')")
	public ResponseEntity<Page<RecognitionEventResponse>> getUnmatchedRecognitions(
			@RequestParam(required = false) UUID deviceId, @RequestParam(required = false) LocalDate startDate,
			@RequestParam(required = false) LocalDate endDate, Pageable pageable) {

		Page<RecognitionEvent> events = recognitionEventService.findUnmatched(deviceId, startDate, endDate, pageable);
		Page<RecognitionEventResponse> response = events.map(this::convertToResponse);

		log.debug("Retrieved {} unmatched recognition events", events.getTotalElements());
		return ResponseEntity.ok(response);
	}

	/**
	 * Convert RecognitionEvent entity to RecognitionEventResponse DTO
	 */
	private RecognitionEventResponse convertToResponse(RecognitionEvent event) {
		RecognitionEventResponse response = new RecognitionEventResponse();
		response.setId(event.getId());
		response.setEmployeeId(event.getEmployee() != null ? event.getEmployee().getId() : null);
		response.setEmployeeCode(event.getEmployee() != null ? event.getEmployee().getEmployeeCode() : null);
		response.setEmployeeName(event.getEmployee() != null ? event.getEmployee().getFullName() : null);
		response.setDeviceId(event.getDevice().getId());
		response.setDeviceCode(event.getDevice().getDeviceCode());
		response.setDeviceName(event.getDevice().getName());
		response.setConfidenceScore(event.getConfidenceScore());
		response.setSnapshotUrl(event.getSnapshotUrl());
		response.setIsMatched(event.getEmployee() != null);
		response.setEventTime(event.getEventTime());
		response.setCreatedAt(event.getCreatedAt());
		return response;
	}

}
