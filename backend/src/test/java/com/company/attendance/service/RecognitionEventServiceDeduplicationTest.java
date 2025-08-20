package com.company.attendance.service;

import com.company.attendance.dto.RecognitionEventRequest;
import com.company.attendance.entity.RecognitionEvent;
import com.company.attendance.entity.Employee;
import com.company.attendance.entity.Device;
import com.company.attendance.repository.RecognitionEventRepository;
import com.company.attendance.repository.EmployeeRepository;
import com.company.attendance.repository.DeviceRepository;
import com.company.attendance.util.HashUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tests for RecognitionEventService deduplication functionality.
 */
@ExtendWith(MockitoExtension.class)
class RecognitionEventServiceDeduplicationTest {

	@Mock
	private RecognitionEventRepository recognitionEventRepository;

	@Mock
	private EmployeeRepository employeeRepository;

	@Mock
	private DeviceRepository deviceRepository;

	@Mock
	private AttendanceService attendanceService;

	@Mock
	private HashUtils hashUtils;

	@InjectMocks
	private RecognitionEventService recognitionEventService;

	private Employee testEmployee;

	private Device testDevice;

	private RecognitionEventRequest testRequest;

	@BeforeEach
	void setUp() {
		testEmployee = new Employee();
		testEmployee.setId(UUID.randomUUID());
		testEmployee.setEmployeeCode("EMP001");
		testEmployee.setFirstName("Test");
		testEmployee.setLastName("Employee");

		testDevice = new Device();
		testDevice.setId(UUID.randomUUID());
		testDevice.setDeviceCode("DEV001");

		testRequest = new RecognitionEventRequest();
		testRequest.setDeviceId(testDevice.getId());
		testRequest.setTopCandidateEmployeeId(testEmployee.getId());
		testRequest.setCapturedAt(Instant.now());
		testRequest.setEmbedding(new float[512]); // Empty embedding for test
		testRequest.setSimilarityScore(0.85);
		testRequest.setLivenessScore(0.95);
		testRequest.setLivenessPassed(true);
		testRequest.setSnapshotUrl("https://test.com/image.jpg");
		testRequest.setProcessingDurationMs(150);
	}

	@Test
	void testProcessRecognition_NewEvent_Success() {
		// Given
		String testHash = "test-hash-12345";

		when(employeeRepository.findById(testEmployee.getId())).thenReturn(Optional.of(testEmployee));
		when(deviceRepository.findById(testDevice.getId())).thenReturn(Optional.of(testDevice));
		when(hashUtils.generateRecognitionHash(anyString(), anyString(), anyString(), any(Instant.class)))
			.thenReturn(testHash);
		when(recognitionEventRepository.existsByDedupHash(testHash)).thenReturn(false);

		RecognitionEvent savedEvent = new RecognitionEvent();
		savedEvent.setId(UUID.randomUUID());
		savedEvent.setStatus(RecognitionEvent.RecognitionStatus.PROCESSED);
		when(recognitionEventRepository.save(any(RecognitionEvent.class))).thenReturn(savedEvent);

		// When
		RecognitionEvent result = recognitionEventService.processRecognition(testRequest);

		// Then
		assertNotNull(result);
		assertEquals(RecognitionEvent.RecognitionStatus.PROCESSED, result.getStatus());

		verify(hashUtils).generateRecognitionHash(eq("https://test.com/image.jpg"), eq("EMP001"),
				eq(testDevice.getId().toString()), eq(testRequest.getCapturedAt()));
		verify(recognitionEventRepository).existsByDedupHash(testHash);
		verify(recognitionEventRepository).save(any(RecognitionEvent.class));
		verify(attendanceService).processRecognitionEvent(any(RecognitionEvent.class));
	}

	@Test
	void testProcessRecognition_DuplicateEvent_ReturnsDuplicate() {
		// Given
		String testHash = "duplicate-hash-12345";

		when(employeeRepository.findById(testEmployee.getId())).thenReturn(Optional.of(testEmployee));
		when(deviceRepository.findById(testDevice.getId())).thenReturn(Optional.of(testDevice));
		when(hashUtils.generateRecognitionHash(anyString(), anyString(), anyString(), any(Instant.class)))
			.thenReturn(testHash);
		when(recognitionEventRepository.existsByDedupHash(testHash)).thenReturn(true);

		RecognitionEvent duplicateEvent = new RecognitionEvent();
		duplicateEvent.setId(UUID.randomUUID());
		duplicateEvent.setStatus(RecognitionEvent.RecognitionStatus.DUPLICATE);
		when(recognitionEventRepository.save(any(RecognitionEvent.class))).thenReturn(duplicateEvent);

		// When
		RecognitionEvent result = recognitionEventService.processRecognition(testRequest);

		// Then
		assertNotNull(result);
		assertEquals(RecognitionEvent.RecognitionStatus.DUPLICATE, result.getStatus());

		verify(recognitionEventRepository).existsByDedupHash(testHash);
		verify(recognitionEventRepository)
			.save(argThat(event -> event.getStatus() == RecognitionEvent.RecognitionStatus.DUPLICATE
					&& event.getDedupHash().equals(testHash)));

		// Should not process attendance for duplicates
		verify(attendanceService, never()).processRecognitionEvent(any(RecognitionEvent.class));
	}

	@Test
	void testProcessRecognition_UnknownEmployee_UsesUnknown() {
		// Given
		String testHash = "unknown-employee-hash";
		UUID unknownEmployeeId = UUID.randomUUID();

		testRequest.setTopCandidateEmployeeId(unknownEmployeeId);

		when(employeeRepository.findById(unknownEmployeeId)).thenReturn(Optional.empty());
		when(deviceRepository.findById(testDevice.getId())).thenReturn(Optional.of(testDevice));
		when(hashUtils.generateRecognitionHash(anyString(), eq("unknown"), anyString(), any(Instant.class)))
			.thenReturn(testHash);
		when(recognitionEventRepository.existsByDedupHash(testHash)).thenReturn(false);

		RecognitionEvent savedEvent = new RecognitionEvent();
		savedEvent.setId(UUID.randomUUID());
		savedEvent.setStatus(RecognitionEvent.RecognitionStatus.PROCESSED);
		when(recognitionEventRepository.save(any(RecognitionEvent.class))).thenReturn(savedEvent);

		// When
		RecognitionEvent result = recognitionEventService.processRecognition(testRequest);

		// Then
		assertNotNull(result);
		verify(hashUtils).generateRecognitionHash(eq("https://test.com/image.jpg"), eq("unknown"),
				eq(testDevice.getId().toString()), eq(testRequest.getCapturedAt()));

		// Should not process attendance for unknown employee
		verify(attendanceService, never()).processRecognitionEvent(any(RecognitionEvent.class));
	}

	@Test
	void testProcessRecognition_NoTopCandidate_UsesUnknown() {
		// Given
		String testHash = "no-candidate-hash";

		testRequest.setTopCandidateEmployeeId(null);

		when(deviceRepository.findById(testDevice.getId())).thenReturn(Optional.of(testDevice));
		when(hashUtils.generateRecognitionHash(anyString(), eq("unknown"), anyString(), any(Instant.class)))
			.thenReturn(testHash);
		when(recognitionEventRepository.existsByDedupHash(testHash)).thenReturn(false);

		RecognitionEvent savedEvent = new RecognitionEvent();
		savedEvent.setId(UUID.randomUUID());
		savedEvent.setStatus(RecognitionEvent.RecognitionStatus.PROCESSED);
		when(recognitionEventRepository.save(any(RecognitionEvent.class))).thenReturn(savedEvent);

		// When
		RecognitionEvent result = recognitionEventService.processRecognition(testRequest);

		// Then
		assertNotNull(result);
		verify(hashUtils).generateRecognitionHash(eq("https://test.com/image.jpg"), eq("unknown"),
				eq(testDevice.getId().toString()), eq(testRequest.getCapturedAt()));
		verify(employeeRepository, never()).findById(any());
	}

	@Test
	void testProcessRecognition_InvalidMatch_NoAttendanceProcessing() {
		// Given
		String testHash = "invalid-match-hash";

		// Create employee that would result in invalid match (low similarity)
		testRequest.setSimilarityScore(0.3); // Below threshold

		when(employeeRepository.findById(testEmployee.getId())).thenReturn(Optional.of(testEmployee));
		when(deviceRepository.findById(testDevice.getId())).thenReturn(Optional.of(testDevice));
		when(hashUtils.generateRecognitionHash(anyString(), anyString(), anyString(), any(Instant.class)))
			.thenReturn(testHash);
		when(recognitionEventRepository.existsByDedupHash(testHash)).thenReturn(false);

		RecognitionEvent savedEvent = new RecognitionEvent();
		savedEvent.setId(UUID.randomUUID());
		savedEvent.setEmployee(testEmployee);
		savedEvent.setSimilarityScore(0.3);
		savedEvent.setStatus(RecognitionEvent.RecognitionStatus.PROCESSED);
		when(recognitionEventRepository.save(any(RecognitionEvent.class))).thenReturn(savedEvent);

		// When
		RecognitionEvent result = recognitionEventService.processRecognition(testRequest);

		// Then
		assertNotNull(result);
		verify(recognitionEventRepository).save(any(RecognitionEvent.class));

		// Should not process attendance for invalid match
		verify(attendanceService, never()).processRecognitionEvent(any(RecognitionEvent.class));
	}

	@Test
	void testProcessRecognition_AttendanceProcessingFailure_ContinuesExecution() {
		// Given
		String testHash = "attendance-failure-hash";

		when(employeeRepository.findById(testEmployee.getId())).thenReturn(Optional.of(testEmployee));
		when(deviceRepository.findById(testDevice.getId())).thenReturn(Optional.of(testDevice));
		when(hashUtils.generateRecognitionHash(anyString(), anyString(), anyString(), any(Instant.class)))
			.thenReturn(testHash);
		when(recognitionEventRepository.existsByDedupHash(testHash)).thenReturn(false);

		RecognitionEvent savedEvent = new RecognitionEvent();
		savedEvent.setId(UUID.randomUUID());
		savedEvent.setEmployee(testEmployee);
		savedEvent.setSimilarityScore(0.85);
		savedEvent.setLivenessPassed(true);
		savedEvent.setStatus(RecognitionEvent.RecognitionStatus.PROCESSED);
		when(recognitionEventRepository.save(any(RecognitionEvent.class))).thenReturn(savedEvent);

		// Mock attendance service to throw exception
		doThrow(new RuntimeException("Attendance processing failed")).when(attendanceService)
			.processRecognitionEvent(any(RecognitionEvent.class));

		// When/Then - should not throw exception
		assertDoesNotThrow(() -> {
			RecognitionEvent result = recognitionEventService.processRecognition(testRequest);
			assertNotNull(result);
		});

		verify(attendanceService).processRecognitionEvent(any(RecognitionEvent.class));
	}

	@Test
    void testCreateRecognitionEventFromRequest_AllFields() {
        // Given
        when(employeeRepository.findById(testEmployee.getId())).thenReturn(Optional.of(testEmployee));
        when(deviceRepository.findById(testDevice.getId())).thenReturn(Optional.of(testDevice));
        when(hashUtils.generateRecognitionHash(anyString(), anyString(), anyString(), any(Instant.class)))
            .thenReturn("test-hash");
        when(recognitionEventRepository.existsByDedupHash(anyString())).thenReturn(false);

        RecognitionEvent mockSavedEvent = new RecognitionEvent();
        when(recognitionEventRepository.save(any(RecognitionEvent.class))).thenAnswer(invocation -> {
            RecognitionEvent event = invocation.getArgument(0);
            // Verify all fields are mapped correctly
            assertEquals(testEmployee, event.getEmployee());
            assertEquals(testDevice, event.getDevice());
            assertEquals(testRequest.getCapturedAt(), event.getCapturedAt());
            assertArrayEquals(testRequest.getEmbedding(), event.getEmbedding());
            assertEquals(testRequest.getSimilarityScore(), event.getSimilarityScore());
            assertEquals(testRequest.getLivenessScore(), event.getLivenessScore());
            assertEquals(testRequest.getLivenessPassed(), event.getLivenessPassed());
            assertEquals(testRequest.getFaceBoxX(), event.getFaceBoxX());
            assertEquals(testRequest.getFaceBoxY(), event.getFaceBoxY());
            assertEquals(testRequest.getFaceBoxWidth(), event.getFaceBoxWidth());
            assertEquals(testRequest.getFaceBoxHeight(), event.getFaceBoxHeight());
            assertEquals(testRequest.getSnapshotUrl(), event.getSnapshotUrl());
            assertEquals(testRequest.getProcessingDurationMs(), event.getProcessingDurationMs());
            assertEquals("test-hash", event.getDedupHash());

            return event;
        });

        // When
        RecognitionEvent result = recognitionEventService.processRecognition(testRequest);

        // Then
        verify(recognitionEventRepository).save(any(RecognitionEvent.class));
    }

}
