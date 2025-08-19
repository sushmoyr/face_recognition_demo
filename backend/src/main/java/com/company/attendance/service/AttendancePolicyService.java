package com.company.attendance.service;

import com.company.attendance.entity.*;
import com.company.attendance.repository.AttendancePolicyRepository;
import com.company.attendance.util.TimezoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

/**
 * Service for attendance policy evaluation and enforcement.
 *
 * Handles complex attendance business rules including: - Time window validation - Grace
 * period calculations - Cooldown period enforcement - Status determination (ON_TIME,
 * LATE, EARLY, etc.) - Timezone-aware processing for Asia/Dhaka
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class AttendancePolicyService {

	private final AttendancePolicyRepository policyRepository;

	private final TimezoneUtils timezoneUtils;

	/**
	 * Evaluate if a recognition event should be processed based on policy rules.
	 */
	public AttendancePolicyEvaluation evaluateRecognitionEvent(Employee employee, Instant eventTime,
			AttendanceRecord lastRecord) {

		log.debug("Evaluating recognition event for employee {} at {}", employee.getEmployeeCode(),
				timezoneUtils.formatBusinessTime(eventTime));

		// Get applicable policy
		AttendancePolicy policy = getApplicablePolicy(employee);
		if (policy == null) {
			log.warn("No attendance policy found for employee {}", employee.getEmployeeCode());
			return AttendancePolicyEvaluation.rejected("No attendance policy configured");
		}

		LocalDate businessDate = timezoneUtils.getBusinessDate(eventTime);
		LocalTime businessTime = timezoneUtils.getBusinessTime(eventTime);

		// Determine expected event type (IN or OUT)
		EventType expectedEventType = determineExpectedEventType(lastRecord);

		// Check if within appropriate time window
		boolean withinTimeWindow = isWithinTimeWindow(policy, businessTime, expectedEventType);
		if (!withinTimeWindow) {
			return AttendancePolicyEvaluation.rejected(String.format("Outside %s window. Expected window: %s",
					expectedEventType, getTimeWindowDescription(policy, expectedEventType)));
		}

		// Check cooldown period
		if (lastRecord != null) {
			long minutesSinceLastEvent = timezoneUtils.getDurationMinutes(lastRecord.getEventTime(), eventTime);
			String cooldownViolation = checkCooldownViolation(policy, lastRecord.getEventType(), expectedEventType,
					minutesSinceLastEvent);
			if (cooldownViolation != null) {
				return AttendancePolicyEvaluation.rejected(cooldownViolation);
			}
		}

		// Determine attendance status based on timing
		AttendanceStatus status = determineAttendanceStatus(policy, businessTime, expectedEventType);

		// Calculate compliance flags
		AttendanceCompliance compliance = calculateCompliance(policy, businessTime, expectedEventType, businessDate);

		return AttendancePolicyEvaluation.approved(expectedEventType, status, compliance);
	}

	/**
	 * Get the applicable attendance policy for an employee.
	 */
	private AttendancePolicy getApplicablePolicy(Employee employee) {
		// Try to get policy from employee's shift
		if (employee.getShift() != null) {
			Optional<AttendancePolicy> shiftPolicy = policyRepository.findByShiftAndIsActiveTrue(employee.getShift());
			if (shiftPolicy.isPresent()) {
				return shiftPolicy.get();
			}
		}

		// Fall back to default policy
		Optional<AttendancePolicy> defaultPolicy = policyRepository.findByIsDefaultTrueAndIsActiveTrue();
		return defaultPolicy.orElse(null);
	}

	/**
	 * Determine if the event is within the appropriate time window.
	 */
	private boolean isWithinTimeWindow(AttendancePolicy policy, LocalTime businessTime, EventType eventType) {
		switch (eventType) {
			case IN:
				return policy.isWithinEntryWindow(businessTime);
			case OUT:
				return policy.isWithinExitWindow(businessTime);
			default:
				return false;
		}
	}

	/**
	 * Get time window description for error messages.
	 */
	private String getTimeWindowDescription(AttendancePolicy policy, EventType eventType) {
		switch (eventType) {
			case IN:
				return String.format("%s to %s", timezoneUtils.formatTime(policy.getEntryWindowStart()),
						timezoneUtils.formatTime(policy.getEntryWindowEnd()));
			case OUT:
				return String.format("%s to %s", timezoneUtils.formatTime(policy.getExitWindowStart()),
						timezoneUtils.formatTime(policy.getExitWindowEnd()));
			default:
				return "Unknown";
		}
	}

	/**
	 * Check if the event violates cooldown periods.
	 */
	private String checkCooldownViolation(AttendancePolicy policy, EventType lastEventType, EventType currentEventType,
			long minutesSinceLastEvent) {

		int requiredCooldown;
		String cooldownType;

		if (lastEventType == EventType.IN && currentEventType == EventType.OUT) {
			requiredCooldown = policy.getInToOutCooldownMinutes();
			cooldownType = "IN to OUT";
		}
		else if (lastEventType == EventType.OUT && currentEventType == EventType.IN) {
			requiredCooldown = policy.getOutToInCooldownMinutes();
			cooldownType = "OUT to IN";
		}
		else {
			// Same event type twice - always requires cooldown
			requiredCooldown = Math.max(policy.getInToOutCooldownMinutes(), policy.getOutToInCooldownMinutes());
			cooldownType = "duplicate " + currentEventType;
		}

		if (minutesSinceLastEvent < requiredCooldown) {
			return String.format("%s cooldown violation. Required: %d minutes, Actual: %d minutes", cooldownType,
					requiredCooldown, minutesSinceLastEvent);
		}

		return null; // No violation
	}

	/**
	 * Determine the attendance status based on timing and policy rules.
	 */
	private AttendanceStatus determineAttendanceStatus(AttendancePolicy policy, LocalTime businessTime,
			EventType eventType) {
		switch (eventType) {
			case IN:
				return policy.determineArrivalStatus(businessTime);
			case OUT:
				return policy.determineDepartureStatus(businessTime);
			default:
				return AttendanceStatus.MANUAL_ADJUSTMENT;
		}
	}

	/**
	 * Calculate compliance flags and metrics.
	 */
	private AttendanceCompliance calculateCompliance(AttendancePolicy policy, LocalTime businessTime,
			EventType eventType, LocalDate businessDate) {

		AttendanceCompliance compliance = new AttendanceCompliance();
		Shift shift = policy.getShift();

		if (eventType == EventType.IN) {
			// Calculate arrival compliance
			LocalTime shiftStart = shift.getStartTime();
			long minutesFromShiftStart = timezoneUtils.getDurationMinutes(shiftStart, businessTime,
					shift.getIsOvernight());

			compliance.setIsEarlyArrival(minutesFromShiftStart < -policy.getEarlyArrivalGraceMinutes());
			compliance.setIsLateArrival(minutesFromShiftStart > policy.getLateArrivalGraceMinutes());
			compliance.setIsOnTime(!compliance.getIsEarlyArrival() && !compliance.getIsLateArrival());

			if (compliance.getIsLateArrival()) {
				compliance.setLateMinutes((int) Math.max(0, minutesFromShiftStart));
			}

		}
		else if (eventType == EventType.OUT) {
			// Calculate departure compliance
			LocalTime shiftEnd = shift.getEndTime();
			long minutesFromShiftEnd = timezoneUtils.getDurationMinutes(shiftEnd, businessTime, shift.getIsOvernight());

			compliance.setIsEarlyDeparture(minutesFromShiftEnd < -policy.getEarlyDepartureGraceMinutes());
			compliance.setIsOvertime(minutesFromShiftEnd > policy.getOvertimeThresholdMinutes());

			if (compliance.getIsOvertime()) {
				compliance.setOvertimeMinutes((int) Math.max(0, minutesFromShiftEnd));
			}

			if (compliance.getIsEarlyDeparture()) {
				compliance.setEarlyDepartureMinutes((int) Math.abs(Math.min(0, minutesFromShiftEnd)));
			}
		}

		// Check if within break window
		compliance.setWithinBreakWindow(policy.isWithinBreakWindow(businessTime));

		return compliance;
	}

	/**
	 * Determine the expected event type based on the last attendance record.
	 */
	private EventType determineExpectedEventType(AttendanceRecord lastRecord) {
		if (lastRecord == null || lastRecord.getEventType() == EventType.OUT) {
			return EventType.IN;
		}
		else {
			return EventType.OUT;
		}
	}

	/**
	 * Evaluate auto clock-out eligibility based on policy.
	 */
	public boolean isEligibleForAutoClockOut(Employee employee, LocalDate businessDate) {
		AttendancePolicy policy = getApplicablePolicy(employee);
		if (policy == null || !policy.getAutoClockOutEnabled() || policy.getAutoClockOutTime() == null) {
			return false;
		}

		LocalTime currentTime = timezoneUtils.nowInBusinessTime().toLocalTime();
		return !currentTime.isBefore(policy.getAutoClockOutTime());
	}

	/**
	 * Get auto clock-out time for an employee.
	 */
	public Optional<LocalTime> getAutoClockOutTime(Employee employee) {
		AttendancePolicy policy = getApplicablePolicy(employee);
		if (policy == null || !policy.getAutoClockOutEnabled()) {
			return Optional.empty();
		}
		return Optional.ofNullable(policy.getAutoClockOutTime());
	}

	/**
	 * Check if attendance is allowed on weekends/holidays.
	 */
	public boolean isAttendanceAllowed(Employee employee, LocalDate businessDate) {
		AttendancePolicy policy = getApplicablePolicy(employee);
		if (policy == null) {
			return false;
		}

		// Check weekend
		if (businessDate.getDayOfWeek().getValue() >= 6) { // Saturday or Sunday
			if (!policy.getAllowWeekendAttendance()) {
				return false;
			}
		}

		// TODO: Add holiday checking logic when holiday service is implemented

		return true;
	}

	/**
	 * Validation result for attendance policy evaluation.
	 */
	public static class AttendancePolicyEvaluation {

		private final boolean approved;

		private final String rejectionReason;

		private final EventType eventType;

		private final AttendanceStatus status;

		private final AttendanceCompliance compliance;

		private AttendancePolicyEvaluation(boolean approved, String rejectionReason, EventType eventType,
				AttendanceStatus status, AttendanceCompliance compliance) {
			this.approved = approved;
			this.rejectionReason = rejectionReason;
			this.eventType = eventType;
			this.status = status;
			this.compliance = compliance;
		}

		public static AttendancePolicyEvaluation approved(EventType eventType, AttendanceStatus status,
				AttendanceCompliance compliance) {
			return new AttendancePolicyEvaluation(true, null, eventType, status, compliance);
		}

		public static AttendancePolicyEvaluation rejected(String reason) {
			return new AttendancePolicyEvaluation(false, reason, null, null, null);
		}

		// Getters
		public boolean isApproved() {
			return approved;
		}

		public String getRejectionReason() {
			return rejectionReason;
		}

		public EventType getEventType() {
			return eventType;
		}

		public AttendanceStatus getStatus() {
			return status;
		}

		public AttendanceCompliance getCompliance() {
			return compliance;
		}

	}

	/**
	 * Compliance metrics for an attendance event.
	 */
	public static class AttendanceCompliance {

		private Boolean isOnTime = false;

		private Boolean isEarlyArrival = false;

		private Boolean isLateArrival = false;

		private Boolean isEarlyDeparture = false;

		private Boolean isOvertime = false;

		private Boolean withinBreakWindow = false;

		private Integer lateMinutes = 0;

		private Integer overtimeMinutes = 0;

		private Integer earlyDepartureMinutes = 0;

		// Getters and setters
		public Boolean getIsOnTime() {
			return isOnTime;
		}

		public void setIsOnTime(Boolean isOnTime) {
			this.isOnTime = isOnTime;
		}

		public Boolean getIsEarlyArrival() {
			return isEarlyArrival;
		}

		public void setIsEarlyArrival(Boolean isEarlyArrival) {
			this.isEarlyArrival = isEarlyArrival;
		}

		public Boolean getIsLateArrival() {
			return isLateArrival;
		}

		public void setIsLateArrival(Boolean isLateArrival) {
			this.isLateArrival = isLateArrival;
		}

		public Boolean getIsEarlyDeparture() {
			return isEarlyDeparture;
		}

		public void setIsEarlyDeparture(Boolean isEarlyDeparture) {
			this.isEarlyDeparture = isEarlyDeparture;
		}

		public Boolean getIsOvertime() {
			return isOvertime;
		}

		public void setIsOvertime(Boolean isOvertime) {
			this.isOvertime = isOvertime;
		}

		public Boolean getWithinBreakWindow() {
			return withinBreakWindow;
		}

		public void setWithinBreakWindow(Boolean withinBreakWindow) {
			this.withinBreakWindow = withinBreakWindow;
		}

		public Integer getLateMinutes() {
			return lateMinutes;
		}

		public void setLateMinutes(Integer lateMinutes) {
			this.lateMinutes = lateMinutes;
		}

		public Integer getOvertimeMinutes() {
			return overtimeMinutes;
		}

		public void setOvertimeMinutes(Integer overtimeMinutes) {
			this.overtimeMinutes = overtimeMinutes;
		}

		public Integer getEarlyDepartureMinutes() {
			return earlyDepartureMinutes;
		}

		public void setEarlyDepartureMinutes(Integer earlyDepartureMinutes) {
			this.earlyDepartureMinutes = earlyDepartureMinutes;
		}

	}

}
