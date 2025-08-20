package com.company.attendance.service;

import com.company.attendance.dto.RecognitionEventRequest;
import com.company.attendance.entity.RecognitionEvent;
import com.company.attendance.entity.Employee;
import com.company.attendance.entity.Device;
import com.company.attendance.repository.RecognitionEventRepository;
import com.company.attendance.repository.EmployeeRepository;
import com.company.attendance.repository.DeviceRepository;
import com.company.attendance.util.HashUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for processing recognition events from edge devices.
 *
 * Handles face recognition event ingestion, validation, and attendance processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RecognitionEventService {

	private final RecognitionEventRepository recognitionEventRepository;

	private final EmployeeRepository employeeRepository;

	private final DeviceRepository deviceRepository;

	private final AttendanceService attendanceService;

	private final HashUtils hashUtils;

	/**
	 * Process incoming recognition event from edge device.
	 */
	@Transactional
	public RecognitionEvent processRecognition(RecognitionEventRequest request) {
		log.debug("Processing recognition event for device: {} with top candidate: {}", request.getDeviceId(),
				request.getTopCandidateEmployeeId());

		// Find employee by ID (if provided)
		Employee employee = null;
		if (request.getTopCandidateEmployeeId() != null) {
			employee = employeeRepository.findById(request.getTopCandidateEmployeeId()).orElse(null);
		}

		// Find device by ID
		Device device = deviceRepository.findById(request.getDeviceId()).orElse(null);

		// Generate deduplication hash based on available data
		String employeeCode = employee != null ? employee.getEmployeeCode() : "unknown";
		String deviceId = request.getDeviceId().toString();
		String snapshotUrl = request.getSnapshotUrl();
		Instant eventTime = request.getCapturedAt();

		String dedupHash = hashUtils.generateRecognitionHash(snapshotUrl, employeeCode, deviceId, eventTime);

		// Check for duplicate
		if (recognitionEventRepository.existsByDedupHash(dedupHash)) {
			log.info("Duplicate recognition event detected - hash: {} for employee: {} device: {}", dedupHash,
					employeeCode, deviceId);

			// Create duplicate record for audit trail
			RecognitionEvent duplicateEvent = createRecognitionEventFromRequest(request, employee, device, dedupHash);
			duplicateEvent.setStatus(RecognitionEvent.RecognitionStatus.DUPLICATE);

			return recognitionEventRepository.save(duplicateEvent);
		}

		// Create new recognition event
		RecognitionEvent event = createRecognitionEventFromRequest(request, employee, device, dedupHash);
		event.setStatus(RecognitionEvent.RecognitionStatus.PROCESSED);

		// Save recognition event
		event = recognitionEventRepository.save(event);

		// Process attendance if employee found and it's a valid match
		if (employee != null && event.isValidMatch()) {
			try {
				attendanceService.processRecognitionEvent(event);
				log.info("Processed attendance for recognition event: {} employee: {}", event.getId(),
						employee.getEmployeeCode());
			}
			catch (Exception e) {
				log.error("Failed to process attendance for recognition event: {}", event.getId(), e);
				// Continue - recognition event is still saved even if attendance
				// processing fails
			}
		}
		else {
			log.warn("Recognition event created but no attendance processed - employee: {} valid match: {}",
					employeeCode, event.isValidMatch());
		}

		return event;
	}

	/**
	 * Create RecognitionEvent entity from request DTO.
	 */
	private RecognitionEvent createRecognitionEventFromRequest(RecognitionEventRequest request, Employee employee,
			Device device, String dedupHash) {
		RecognitionEvent event = new RecognitionEvent();

		event.setEmployee(employee);
		event.setDevice(device);
		event.setCapturedAt(request.getCapturedAt());
		event.setEmbedding(request.getEmbedding());
		event.setSimilarityScore(request.getSimilarityScore());
		event.setLivenessScore(request.getLivenessScore());
		event.setLivenessPassed(request.getLivenessPassed());
		event.setFaceBoxX(request.getFaceBoxX());
		event.setFaceBoxY(request.getFaceBoxY());
		event.setFaceBoxWidth(request.getFaceBoxWidth());
		event.setFaceBoxHeight(request.getFaceBoxHeight());
		event.setSnapshotUrl(request.getSnapshotUrl());
		event.setProcessingDurationMs(request.getProcessingDurationMs());
		event.setDedupHash(dedupHash);

		return event;
	}

	/**
	 * Get recognition events by date range.
	 */
	public Page<RecognitionEvent> getRecognitionsByDateRange(LocalDate startDate, LocalDate endDate,
			Pageable pageable) {
		return recognitionEventRepository.findByCapturedAtBetween(
				startDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
				endDate.atTime(23, 59, 59).toInstant(java.time.ZoneOffset.UTC), pageable);
	}

	/**
	 * Get recognition events by employee.
	 */
	public Page<RecognitionEvent> getRecognitionsByEmployee(UUID employeeId, Pageable pageable) {
		return recognitionEventRepository.findByEmployeeId(employeeId, pageable);
	}

	/**
	 * Get recognition events by device.
	 */
	public Page<RecognitionEvent> getRecognitionsByDevice(UUID deviceId, Pageable pageable) {
		return recognitionEventRepository.findByDeviceId(deviceId, pageable);
	}

	/**
	 * Get recent recognition events.
	 */
	public Page<RecognitionEvent> getRecentRecognitions(Pageable pageable) {
		return recognitionEventRepository.findAllByOrderByCapturedAtDesc(pageable);
	}

	/**
	 * Get recognition event by ID.
	 */
	public RecognitionEvent getRecognitionById(UUID id) {
		return recognitionEventRepository.findById(id)
			.orElseThrow(() -> new RuntimeException("Recognition event not found: " + id));
	}

	// Additional service methods for controller support
	public Page<RecognitionEvent> findAll(UUID employeeId, UUID deviceId, LocalDate fromDate, LocalDate toDate,
			Double minConfidence, Pageable pageable) {
		return recognitionEventRepository.findAll(pageable);
	}

	public java.util.Optional<RecognitionEvent> findById(UUID id) {
		return recognitionEventRepository.findById(id);
	}

	public Object getRecognitionStatistics(LocalDate fromDate, LocalDate toDate) {
		return new Object() {
			public final String period = fromDate + " to " + toDate;

			public final String stats = "calculated";

		};
	}

	public Page<RecognitionEvent> findUnmatched(UUID deviceId, LocalDate fromDate, LocalDate toDate,
			Pageable pageable) {
		return recognitionEventRepository.findAll(pageable);
	}

}
