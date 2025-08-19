package com.company.attendance.service;

import com.company.attendance.entity.*;
import com.company.attendance.repository.AttendancePolicyRepository;
import com.company.attendance.util.TimezoneUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for AttendancePolicyService.
 *
 * Tests complex attendance business rules including: - Time window validation - Grace
 * period calculations - Cooldown period enforcement - Timezone handling for Asia/Dhaka -
 * Shift compliance evaluation
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AttendancePolicyService Tests")
class AttendancePolicyServiceTest {

	@Mock
	private AttendancePolicyRepository policyRepository;

	@Mock
	private TimezoneUtils timezoneUtils;

	@InjectMocks
	private AttendancePolicyService attendancePolicyService;

	private Employee employee;

	private Shift regularShift;

	private Shift overnightShift;

	private AttendancePolicy regularPolicy;

	private AttendancePolicy strictPolicy;

	private Instant eventTime;

	private LocalDate businessDate;

	private LocalTime businessTime;

	@BeforeEach
	void setUp() {
		// Setup test employee
		employee = new Employee();
		employee.setId(UUID.randomUUID());
		employee.setEmployeeCode("E001");
		employee.setFirstName("John");
		employee.setLastName("Doe");

		// Setup regular shift (9:00 AM - 5:00 PM)
		regularShift = new Shift();
		regularShift.setId(UUID.randomUUID());
		regularShift.setName("Regular Day Shift");
		regularShift.setStartTime(LocalTime.of(9, 0));
		regularShift.setEndTime(LocalTime.of(17, 0));
		regularShift.setIsOvernight(false);
		regularShift.setGracePeriodMinutes(15);

		// Setup overnight shift (22:00 - 06:00)
		overnightShift = new Shift();
		overnightShift.setId(UUID.randomUUID());
		overnightShift.setName("Night Shift");
		overnightShift.setStartTime(LocalTime.of(22, 0));
		overnightShift.setEndTime(LocalTime.of(6, 0));
		overnightShift.setIsOvernight(true);
		overnightShift.setGracePeriodMinutes(10);

		// Setup regular policy
		regularPolicy = new AttendancePolicy();
		regularPolicy.setId(UUID.randomUUID());
		regularPolicy.setName("Regular Policy");
		regularPolicy.setShift(regularShift);
		regularPolicy.setEntryWindowStartMinutes(30); // 30 min before shift
		regularPolicy.setEntryWindowEndMinutes(120); // 2 hours after shift start
		regularPolicy.setExitWindowStartMinutes(30); // 30 min before shift end
		regularPolicy.setExitWindowEndMinutes(120); // 2 hours after shift end
		regularPolicy.setLateArrivalGraceMinutes(10);
		regularPolicy.setEarlyArrivalGraceMinutes(15);
		regularPolicy.setEarlyDepartureGraceMinutes(15);
		regularPolicy.setOvertimeThresholdMinutes(30);
		regularPolicy.setInToOutCooldownMinutes(30);
		regularPolicy.setOutToInCooldownMinutes(15);
		regularPolicy.setIsActive(true);

		// Setup strict policy
		strictPolicy = new AttendancePolicy();
		strictPolicy.setId(UUID.randomUUID());
		strictPolicy.setName("Strict Policy");
		strictPolicy.setShift(regularShift);
		strictPolicy.setEntryWindowStartMinutes(15); // Only 15 min before shift
		strictPolicy.setEntryWindowEndMinutes(60); // Only 1 hour after shift start
		strictPolicy.setExitWindowStartMinutes(15); // Only 15 min before shift end
		strictPolicy.setExitWindowEndMinutes(60); // Only 1 hour after shift end
		strictPolicy.setLateArrivalGraceMinutes(5); // Strict 5 min grace
		strictPolicy.setEarlyArrivalGraceMinutes(5);
		strictPolicy.setEarlyDepartureGraceMinutes(5);
		strictPolicy.setOvertimeThresholdMinutes(15);
		strictPolicy.setInToOutCooldownMinutes(60); // Longer cooldown
		strictPolicy.setOutToInCooldownMinutes(30);
		strictPolicy.setIsActive(true);

		// Setup test time data
		eventTime = Instant.now();
		businessDate = LocalDate.of(2024, 1, 15); // Monday
		businessTime = LocalTime.of(9, 5); // 9:05 AM

		// Setup employee with shift
		employee.setShift(regularShift);
	}

	@Nested
	@DisplayName("Time Window Validation Tests")
	class TimeWindowValidationTests {

		@Test
		@DisplayName("Should approve clock-in within entry window")
		void shouldApproveClockInWithinEntryWindow() {
			// Given: Clock-in at 9:05 AM (5 minutes after shift start)
			businessTime = LocalTime.of(9, 5);
			when(timezoneUtils.getBusinessDate(eventTime)).thenReturn(businessDate);
			when(timezoneUtils.getBusinessTime(eventTime)).thenReturn(businessTime);
			when(policyRepository.findByShiftAndIsActiveTrue(regularShift)).thenReturn(Optional.of(regularPolicy));

			// When: Evaluate recognition event
			var evaluation = attendancePolicyService.evaluateRecognitionEvent(employee, eventTime, null);

			// Then: Should be approved
			assertThat(evaluation.isApproved()).isTrue();
			assertThat(evaluation.getEventType()).isEqualTo(EventType.IN);
			assertThat(evaluation.getStatus()).isEqualTo(AttendanceStatus.ON_TIME_IN);
		}

		@Test
		@DisplayName("Should reject clock-in outside entry window")
		void shouldRejectClockInOutsideEntryWindow() {
			// Given: Clock-in at 11:30 AM (2.5 hours after shift start, outside window)
			businessTime = LocalTime.of(11, 30);
			when(timezoneUtils.getBusinessDate(eventTime)).thenReturn(businessDate);
			when(timezoneUtils.getBusinessTime(eventTime)).thenReturn(businessTime);
			when(policyRepository.findByShiftAndIsActiveTrue(regularShift)).thenReturn(Optional.of(regularPolicy));

			// When: Evaluate recognition event
			var evaluation = attendancePolicyService.evaluateRecognitionEvent(employee, eventTime, null);

			// Then: Should be rejected
			assertThat(evaluation.isApproved()).isFalse();
			assertThat(evaluation.getRejectionReason()).contains("Outside IN window");
		}

		@Test
		@DisplayName("Should approve clock-out within exit window")
		void shouldApproveClockOutWithinExitWindow() {
			// Given: Clock-out at 5:30 PM (30 minutes after shift end)
			businessTime = LocalTime.of(17, 30);
			when(timezoneUtils.getBusinessDate(eventTime)).thenReturn(businessDate);
			when(timezoneUtils.getBusinessTime(eventTime)).thenReturn(businessTime);
			when(policyRepository.findByShiftAndIsActiveTrue(regularShift)).thenReturn(Optional.of(regularPolicy));

			// Create previous IN record
			AttendanceRecord lastRecord = createAttendanceRecord(EventType.IN, Instant.now().minusSeconds(8 * 3600));

			// When: Evaluate recognition event
			var evaluation = attendancePolicyService.evaluateRecognitionEvent(employee, eventTime, lastRecord);

			// Then: Should be approved
			assertThat(evaluation.isApproved()).isTrue();
			assertThat(evaluation.getEventType()).isEqualTo(EventType.OUT);
			assertThat(evaluation.getStatus()).isEqualTo(AttendanceStatus.OVERTIME_OUT);
		}

	}

	@Nested
	@DisplayName("Grace Period Tests")
	class GracePeriodTests {

		@Test
		@DisplayName("Should mark as ON_TIME within late arrival grace period")
		void shouldMarkOnTimeWithinGracePeriod() {
			// Given: Clock-in at 9:08 AM (8 minutes after shift start, within 10 min
			// grace)
			businessTime = LocalTime.of(9, 8);
			when(timezoneUtils.getBusinessDate(eventTime)).thenReturn(businessDate);
			when(timezoneUtils.getBusinessTime(eventTime)).thenReturn(businessTime);
			when(policyRepository.findByShiftAndIsActiveTrue(regularShift)).thenReturn(Optional.of(regularPolicy));

			// When: Evaluate recognition event
			var evaluation = attendancePolicyService.evaluateRecognitionEvent(employee, eventTime, null);

			// Then: Should be on-time
			assertThat(evaluation.isApproved()).isTrue();
			assertThat(evaluation.getStatus()).isEqualTo(AttendanceStatus.ON_TIME_IN);
		}

		@Test
		@DisplayName("Should mark as LATE outside grace period")
		void shouldMarkLateOutsideGracePeriod() {
			// Given: Clock-in at 9:15 AM (15 minutes after shift start, outside 10 min
			// grace)
			businessTime = LocalTime.of(9, 15);
			when(timezoneUtils.getBusinessDate(eventTime)).thenReturn(businessDate);
			when(timezoneUtils.getBusinessTime(eventTime)).thenReturn(businessTime);
			when(policyRepository.findByShiftAndIsActiveTrue(regularShift)).thenReturn(Optional.of(regularPolicy));

			// When: Evaluate recognition event
			var evaluation = attendancePolicyService.evaluateRecognitionEvent(employee, eventTime, null);

			// Then: Should be late
			assertThat(evaluation.isApproved()).isTrue();
			assertThat(evaluation.getStatus()).isEqualTo(AttendanceStatus.LATE_IN);
		}

		@Test
		@DisplayName("Should mark as EARLY arrival before grace period")
		void shouldMarkEarlyArrival() {
			// Given: Clock-in at 8:30 AM (30 minutes before shift start, outside 15 min
			// grace)
			businessTime = LocalTime.of(8, 30);
			when(timezoneUtils.getBusinessDate(eventTime)).thenReturn(businessDate);
			when(timezoneUtils.getBusinessTime(eventTime)).thenReturn(businessTime);
			when(policyRepository.findByShiftAndIsActiveTrue(regularShift)).thenReturn(Optional.of(regularPolicy));

			// When: Evaluate recognition event
			var evaluation = attendancePolicyService.evaluateRecognitionEvent(employee, eventTime, null);

			// Then: Should be early
			assertThat(evaluation.isApproved()).isTrue();
			assertThat(evaluation.getStatus()).isEqualTo(AttendanceStatus.EARLY_IN);
		}

	}

	@Nested
	@DisplayName("Cooldown Period Tests")
	class CooldownPeriodTests {

		@Test
		@DisplayName("Should reject IN to OUT within cooldown period")
		void shouldRejectInToOutWithinCooldown() {
			// Given: Previous IN record 20 minutes ago, policy requires 30 min cooldown
			Instant previousTime = eventTime.minusSeconds(20 * 60);
			AttendanceRecord lastRecord = createAttendanceRecord(EventType.IN, previousTime);

			when(timezoneUtils.getBusinessDate(eventTime)).thenReturn(businessDate);
			when(timezoneUtils.getBusinessTime(eventTime)).thenReturn(businessTime);
			when(timezoneUtils.getDurationMinutes(previousTime, eventTime)).thenReturn(20L);
			when(policyRepository.findByShiftAndIsActiveTrue(regularShift)).thenReturn(Optional.of(regularPolicy));

			// When: Evaluate clock-out event
			var evaluation = attendancePolicyService.evaluateRecognitionEvent(employee, eventTime, lastRecord);

			// Then: Should be rejected due to cooldown
			assertThat(evaluation.isApproved()).isFalse();
			assertThat(evaluation.getRejectionReason()).contains("IN to OUT cooldown violation");
			assertThat(evaluation.getRejectionReason()).contains("Required: 30 minutes, Actual: 20 minutes");
		}

		@Test
		@DisplayName("Should approve OUT to IN after cooldown period")
		void shouldApproveOutToInAfterCooldown() {
			// Given: Previous OUT record 20 minutes ago, policy requires 15 min cooldown
			Instant previousTime = eventTime.minusSeconds(20 * 60);
			AttendanceRecord lastRecord = createAttendanceRecord(EventType.OUT, previousTime);

			businessTime = LocalTime.of(9, 5); // Within entry window
			when(timezoneUtils.getBusinessDate(eventTime)).thenReturn(businessDate);
			when(timezoneUtils.getBusinessTime(eventTime)).thenReturn(businessTime);
			when(timezoneUtils.getDurationMinutes(previousTime, eventTime)).thenReturn(20L);
			when(policyRepository.findByShiftAndIsActiveTrue(regularShift)).thenReturn(Optional.of(regularPolicy));

			// When: Evaluate clock-in event
			var evaluation = attendancePolicyService.evaluateRecognitionEvent(employee, eventTime, lastRecord);

			// Then: Should be approved
			assertThat(evaluation.isApproved()).isTrue();
			assertThat(evaluation.getEventType()).isEqualTo(EventType.IN);
		}

	}

	@Nested
	@DisplayName("Overtime and Compliance Tests")
	class OvertimeComplianceTests {

		@Test
		@DisplayName("Should calculate overtime minutes correctly")
		void shouldCalculateOvertimeCorrectly() {
			// Given: Clock-out at 6:00 PM (1 hour after shift end)
			businessTime = LocalTime.of(18, 0);
			when(timezoneUtils.getBusinessDate(eventTime)).thenReturn(businessDate);
			when(timezoneUtils.getBusinessTime(eventTime)).thenReturn(businessTime);
			when(timezoneUtils.getDurationMinutes(regularShift.getEndTime(), businessTime, false)).thenReturn(60L);
			when(policyRepository.findByShiftAndIsActiveTrue(regularShift)).thenReturn(Optional.of(regularPolicy));

			// Create previous IN record
			AttendanceRecord lastRecord = createAttendanceRecord(EventType.IN, eventTime.minusSeconds(9 * 3600));
			when(timezoneUtils.getDurationMinutes(lastRecord.getEventTime(), eventTime)).thenReturn(9 * 60L);

			// When: Evaluate clock-out event
			var evaluation = attendancePolicyService.evaluateRecognitionEvent(employee, eventTime, lastRecord);

			// Then: Should be overtime with correct minutes
			assertThat(evaluation.isApproved()).isTrue();
			assertThat(evaluation.getStatus()).isEqualTo(AttendanceStatus.OVERTIME_OUT);
			assertThat(evaluation.getCompliance().getIsOvertime()).isTrue();
			assertThat(evaluation.getCompliance().getOvertimeMinutes()).isEqualTo(30); // 60
																						// min
																						// -
																						// 30
																						// min
																						// threshold
		}

		@Test
		@DisplayName("Should detect early departure correctly")
		void shouldDetectEarlyDeparture() {
			// Given: Clock-out at 4:30 PM (30 minutes before shift end, outside 15 min
			// grace)
			businessTime = LocalTime.of(16, 30);
			when(timezoneUtils.getBusinessDate(eventTime)).thenReturn(businessDate);
			when(timezoneUtils.getBusinessTime(eventTime)).thenReturn(businessTime);
			when(timezoneUtils.getDurationMinutes(regularShift.getEndTime(), businessTime, false)).thenReturn(-30L);
			when(policyRepository.findByShiftAndIsActiveTrue(regularShift)).thenReturn(Optional.of(regularPolicy));

			// Create previous IN record
			AttendanceRecord lastRecord = createAttendanceRecord(EventType.IN, eventTime.minusSeconds(7 * 3600));
			when(timezoneUtils.getDurationMinutes(lastRecord.getEventTime(), eventTime)).thenReturn(7 * 60L);

			// When: Evaluate clock-out event
			var evaluation = attendancePolicyService.evaluateRecognitionEvent(employee, eventTime, lastRecord);

			// Then: Should be early departure
			assertThat(evaluation.isApproved()).isTrue();
			assertThat(evaluation.getStatus()).isEqualTo(AttendanceStatus.EARLY_OUT);
			assertThat(evaluation.getCompliance().getIsEarlyDeparture()).isTrue();
			assertThat(evaluation.getCompliance().getEarlyDepartureMinutes()).isEqualTo(30);
		}

	}

	@Nested
	@DisplayName("Overnight Shift Tests")
	class OvernightShiftTests {

		@BeforeEach
		void setUpOvernightShift() {
			employee.setShift(overnightShift);

			// Setup overnight policy
			AttendancePolicy overnightPolicy = new AttendancePolicy();
			overnightPolicy.setId(UUID.randomUUID());
			overnightPolicy.setName("Overnight Policy");
			overnightPolicy.setShift(overnightShift);
			overnightPolicy.setEntryWindowStartMinutes(30);
			overnightPolicy.setEntryWindowEndMinutes(120);
			overnightPolicy.setExitWindowStartMinutes(30);
			overnightPolicy.setExitWindowEndMinutes(120);
			overnightPolicy.setLateArrivalGraceMinutes(10);
			overnightPolicy.setEarlyArrivalGraceMinutes(15);
			overnightPolicy.setIsActive(true);

			when(policyRepository.findByShiftAndIsActiveTrue(overnightShift)).thenReturn(Optional.of(overnightPolicy));
		}

		@Test
		@DisplayName("Should handle overnight shift clock-in correctly")
		void shouldHandleOvernightClockIn() {
			// Given: Clock-in at 10:05 PM (5 minutes after shift start)
			businessTime = LocalTime.of(22, 5);
			when(timezoneUtils.getBusinessDate(eventTime)).thenReturn(businessDate);
			when(timezoneUtils.getBusinessTime(eventTime)).thenReturn(businessTime);

			// When: Evaluate recognition event
			var evaluation = attendancePolicyService.evaluateRecognitionEvent(employee, eventTime, null);

			// Then: Should be approved
			assertThat(evaluation.isApproved()).isTrue();
			assertThat(evaluation.getEventType()).isEqualTo(EventType.IN);
			assertThat(evaluation.getStatus()).isEqualTo(AttendanceStatus.ON_TIME_IN);
		}

		@Test
		@DisplayName("Should handle overnight shift clock-out correctly")
		void shouldHandleOvernightClockOut() {
			// Given: Clock-out at 6:30 AM (30 minutes after shift end)
			businessTime = LocalTime.of(6, 30);
			businessDate = LocalDate.of(2024, 1, 16); // Next day
			when(timezoneUtils.getBusinessDate(eventTime)).thenReturn(businessDate);
			when(timezoneUtils.getBusinessTime(eventTime)).thenReturn(businessTime);

			// Create previous IN record from night before
			AttendanceRecord lastRecord = createAttendanceRecord(EventType.IN, eventTime.minusSeconds(8 * 3600));
			when(timezoneUtils.getDurationMinutes(lastRecord.getEventTime(), eventTime)).thenReturn(8 * 60L);

			// When: Evaluate recognition event
			var evaluation = attendancePolicyService.evaluateRecognitionEvent(employee, eventTime, lastRecord);

			// Then: Should be approved with overtime
			assertThat(evaluation.isApproved()).isTrue();
			assertThat(evaluation.getEventType()).isEqualTo(EventType.OUT);
			assertThat(evaluation.getStatus()).isEqualTo(AttendanceStatus.OVERTIME_OUT);
		}

	}

	@Nested
	@DisplayName("Policy Fallback Tests")
	class PolicyFallbackTests {

		@Test
		@DisplayName("Should use default policy when employee has no shift")
		void shouldUseDefaultPolicyWhenNoShift() {
			// Given: Employee without shift
			employee.setShift(null);
			when(policyRepository.findByIsDefaultTrueAndIsActiveTrue()).thenReturn(Optional.of(regularPolicy));
			when(timezoneUtils.getBusinessDate(eventTime)).thenReturn(businessDate);
			when(timezoneUtils.getBusinessTime(eventTime)).thenReturn(businessTime);

			// When: Evaluate recognition event
			var evaluation = attendancePolicyService.evaluateRecognitionEvent(employee, eventTime, null);

			// Then: Should use default policy
			assertThat(evaluation.isApproved()).isTrue();
			verify(policyRepository).findByIsDefaultTrueAndIsActiveTrue();
		}

		@Test
        @DisplayName("Should reject when no policy available")
        void shouldRejectWhenNoPolicyAvailable() {
            // Given: No policy available
            when(policyRepository.findByShiftAndIsActiveTrue(regularShift)).thenReturn(Optional.empty());
            when(policyRepository.findByIsDefaultTrueAndIsActiveTrue()).thenReturn(Optional.empty());

            // When: Evaluate recognition event
            var evaluation = attendancePolicyService.evaluateRecognitionEvent(employee, eventTime, null);

            // Then: Should be rejected
            assertThat(evaluation.isApproved()).isFalse();
            assertThat(evaluation.getRejectionReason()).isEqualTo("No attendance policy configured");
        }

	}

	@Nested
	@DisplayName("Weekend and Holiday Tests")
	class WeekendHolidayTests {

		@Test
		@DisplayName("Should allow attendance when weekend attendance is enabled")
		void shouldAllowWeekendAttendanceWhenEnabled() {
			// Given: Saturday and weekend attendance enabled
			LocalDate saturday = LocalDate.of(2024, 1, 20); // Saturday
			regularPolicy.setAllowWeekendAttendance(true);
			when(policyRepository.findByShiftAndIsActiveTrue(regularShift)).thenReturn(Optional.of(regularPolicy));

			// When: Check if attendance is allowed
			boolean isAllowed = attendancePolicyService.isAttendanceAllowed(employee, saturday);

			// Then: Should be allowed
			assertThat(isAllowed).isTrue();
		}

		@Test
		@DisplayName("Should deny attendance when weekend attendance is disabled")
		void shouldDenyWeekendAttendanceWhenDisabled() {
			// Given: Saturday and weekend attendance disabled
			LocalDate saturday = LocalDate.of(2024, 1, 20); // Saturday
			regularPolicy.setAllowWeekendAttendance(false);
			when(policyRepository.findByShiftAndIsActiveTrue(regularShift)).thenReturn(Optional.of(regularPolicy));

			// When: Check if attendance is allowed
			boolean isAllowed = attendancePolicyService.isAttendanceAllowed(employee, saturday);

			// Then: Should be denied
			assertThat(isAllowed).isFalse();
		}

	}

	// Helper method to create test attendance records
	private AttendanceRecord createAttendanceRecord(EventType eventType, Instant eventTime) {
		AttendanceRecord record = new AttendanceRecord();
		record.setId(UUID.randomUUID());
		record.setEmployee(employee);
		record.setEventType(eventType);
		record.setEventTime(eventTime);
		record.setAttendanceDate(LocalDate.now());
		return record;
	}

}
