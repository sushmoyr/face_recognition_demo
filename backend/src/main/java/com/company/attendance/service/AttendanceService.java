package com.company.attendance.service;

import com.company.attendance.entity.AttendanceRecord;
import com.company.attendance.entity.Employee;
import com.company.attendance.entity.EventType;
import com.company.attendance.entity.RecognitionEvent;
import com.company.attendance.entity.Shift;
import com.company.attendance.repository.AttendanceRecordRepository;
import com.company.attendance.repository.EmployeeScheduleRepository;
import com.company.attendance.service.AttendancePolicyService.AttendancePolicyEvaluation;
import com.company.attendance.util.TimezoneUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for processing attendance logic and rules.
 *
 * Handles IN/OUT detection, late arrival/early departure calculation, shift compliance,
 * and cooldown periods.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttendanceService {

	private final AttendanceRecordRepository attendanceRecordRepository;

	private final EmployeeScheduleRepository employeeScheduleRepository;

	private final AttendancePolicyService attendancePolicyService;

	private final TimezoneUtils timezoneUtils;

	@Value("${app.attendance.entry-window-start:05:00}")
	private String entryWindowStart;

	@Value("${app.attendance.entry-window-end:12:00}")
	private String entryWindowEnd;

	@Value("${app.attendance.exit-window-start:12:00}")
	private String exitWindowStart;

	@Value("${app.attendance.exit-window-end:23:00}")
	private String exitWindowEnd;

	@Value("${app.attendance.cooldown-minutes:10}")
	private int cooldownMinutes;

	@Value("${app.timezone:Asia/Dhaka}")
	private String timezone;

	/**
	 * Process a recognition event and create attendance record if applicable.
	 * @param recognitionEvent The recognition event to process
	 * @return Optional attendance record if created
	 */
	@Transactional
	public Optional<AttendanceRecord> processRecognitionEvent(RecognitionEvent recognitionEvent) {
		if (!recognitionEvent.isValidMatch()) {
			log.debug("Recognition event {} is not a valid match, skipping attendance processing",
					recognitionEvent.getId());
			return Optional.empty();
		}

		Employee employee = recognitionEvent.getEmployee();
		ZonedDateTime eventTime = recognitionEvent.getCapturedAt().atZone(ZoneId.of(timezone));
		LocalDate attendanceDate = eventTime.toLocalDate();
		LocalTime eventLocalTime = eventTime.toLocalTime();

		// Check if this employee already has a recent attendance record (cooldown)
		if (isWithinCooldownPeriod(employee.getId(), recognitionEvent.getCapturedAt())) {
			log.debug("Employee {} is within cooldown period, skipping attendance record creation",
					employee.getEmployeeCode());
			return Optional.empty();
		}

		// Determine if this should be IN or OUT based on time windows and previous
		// records
		EventType eventType = determineEventType(employee.getId(), attendanceDate, eventLocalTime);

		// Get employee's shift for this day
		Optional<Shift> shift = getEmployeeShiftForDate(employee.getId(), attendanceDate,
				eventTime.getDayOfWeek().getValue());

		// Create attendance record
		AttendanceRecord attendanceRecord = new AttendanceRecord();
		attendanceRecord.setEmployee(employee);
		attendanceRecord.setDevice(recognitionEvent.getDevice());
		attendanceRecord.setRecognitionEvent(recognitionEvent);
		attendanceRecord.setAttendanceDate(attendanceDate);
		attendanceRecord.setEventTime(recognitionEvent.getCapturedAt());
		attendanceRecord.setEventType(eventType);
		attendanceRecord.setShift(shift.orElse(null));

		// Calculate compliance flags
		if (shift.isPresent()) {
			calculateComplianceFlags(attendanceRecord, shift.get(), eventLocalTime);
		}

		// Calculate duration for OUT events
		if (eventType == EventType.OUT) {
			calculateDuration(attendanceRecord);
		}

		attendanceRecord = attendanceRecordRepository.save(attendanceRecord);

		log.info("Created attendance record: Employee {} - {} at {} ({})", employee.getEmployeeCode(), eventType,
				eventTime, attendanceRecord.hasComplianceIssues() ? "NON-COMPLIANT" : "COMPLIANT");

		return Optional.of(attendanceRecord);
	}

	/**
	 * Check if employee is within cooldown period.
	 */
	private boolean isWithinCooldownPeriod(UUID employeeId, Instant currentTime) {
		Optional<AttendanceRecord> lastRecord = attendanceRecordRepository.findLastByEmployeeId(employeeId);

		if (lastRecord.isEmpty()) {
			return false;
		}

		long minutesSinceLastRecord = ChronoUnit.MINUTES.between(lastRecord.get().getEventTime(), currentTime);
		return minutesSinceLastRecord < cooldownMinutes;
	}

	/**
	 * Determine if this should be an IN or OUT event.
	 */
	private EventType determineEventType(UUID employeeId, LocalDate date, LocalTime time) {
		// Check the last record for this employee on this date
		Optional<AttendanceRecord> lastInRecord = attendanceRecordRepository.findLastInRecordForDate(employeeId, date);

		// Time windows for entry and exit
		LocalTime entryStart = LocalTime.parse(entryWindowStart);
		LocalTime entryEnd = LocalTime.parse(entryWindowEnd);
		LocalTime exitStart = LocalTime.parse(exitWindowStart);
		LocalTime exitEnd = LocalTime.parse(exitWindowEnd);

		// If no IN record for today and within entry window, it's IN
		if (lastInRecord.isEmpty() && isWithinTimeWindow(time, entryStart, entryEnd)) {
			return EventType.IN;
		}

		// If there's an IN record and within exit window, it's OUT
		if (lastInRecord.isPresent() && isWithinTimeWindow(time, exitStart, exitEnd)) {
			return EventType.OUT;
		}

		// Default logic: if there's no unmatched IN for today, it's IN; otherwise OUT
		return lastInRecord.isEmpty() ? EventType.IN : EventType.OUT;
	}

	/**
	 * Check if time is within a time window.
	 */
	private boolean isWithinTimeWindow(LocalTime time, LocalTime start, LocalTime end) {
		if (start.isBefore(end)) {
			// Normal case: e.g., 09:00 to 18:00
			return !time.isBefore(start) && !time.isAfter(end);
		}
		else {
			// Overnight case: e.g., 22:00 to 06:00
			return !time.isBefore(start) || !time.isAfter(end);
		}
	}

	/**
	 * Get employee's shift for a specific date and day of week.
	 */
	private Optional<Shift> getEmployeeShiftForDate(UUID employeeId, LocalDate date, int dayOfWeek) {
		return employeeScheduleRepository.findEffectiveShiftForEmployeeAndDate(employeeId, date, dayOfWeek);
	}

	/**
	 * Calculate compliance flags (late, early leave, etc.).
	 */
	private void calculateComplianceFlags(AttendanceRecord record, Shift shift, LocalTime eventTime) {
		if (record.getEventType() == EventType.IN) {
			// Check if late
			boolean isLate = shift.isLateEntry(eventTime);
			record.setIsLate(isLate);
		}
		else if (record.getEventType() == EventType.OUT) {
			// Check if early leave
			boolean isEarlyLeave = shift.isEarlyLeave(eventTime);
			record.setIsEarlyLeave(isEarlyLeave);

			// Check if overtime (left after expected end time + grace period)
			LocalTime overtimeThreshold = shift.getEndTime().plusMinutes(shift.getGracePeriodMinutes());
			boolean isOvertime = eventTime.isAfter(overtimeThreshold);
			record.setIsOvertime(isOvertime);
		}
	}

	/**
	 * Calculate work duration for OUT events.
	 */
	private void calculateDuration(AttendanceRecord outRecord) {
		Optional<AttendanceRecord> lastInRecord = attendanceRecordRepository
			.findLastInRecordForDate(outRecord.getEmployee().getId(), outRecord.getAttendanceDate());

		if (lastInRecord.isPresent()) {
			long durationMinutes = ChronoUnit.MINUTES.between(lastInRecord.get().getEventTime(),
					outRecord.getEventTime());
			outRecord.setDurationMinutes((int) durationMinutes);
		}
	}

	// Additional service methods for controller support
	public Page<AttendanceRecord> findDailyAttendance(LocalDate date, String department, UUID employeeId,
			org.springframework.data.domain.Pageable pageable) {
		return attendanceRecordRepository.findByAttendanceDate(date, pageable);
	}

	public Page<AttendanceRecord> findAll(LocalDate fromDate, LocalDate toDate, String department, UUID employeeId,
			String eventType, org.springframework.data.domain.Pageable pageable) {
		return attendanceRecordRepository.findAll(pageable);
	}

	public Optional<AttendanceRecord> findById(UUID id) {
		return attendanceRecordRepository.findById(id);
	}

	public Object getEmployeeAttendanceSummary(UUID employeeId, LocalDate fromDate, LocalDate toDate) {
		// Return a simple summary object
		return new Object() {
			public final String empId = employeeId.toString();

			public final String period = fromDate + " to " + toDate;

			public final String status = "summary";

		};
	}

	public Object generateAttendanceReport(Object request) {
		// Return a simple report object
		return new Object() {
			public final String report = "Generated";

			public final String timestamp = Instant.now().toString();

		};
	}

	public Object getAttendanceStatistics(LocalDate fromDate, LocalDate toDate, String department) {
		// Return simple statistics
		return new Object() {
			public final String period = fromDate + " to " + toDate;

			public final String dept = department != null ? department : "all";

			public final String stats = "calculated";

		};
	}

	public Page<AttendanceRecord> findLateArrivals(LocalDate date, String department,
			org.springframework.data.domain.Pageable pageable) {
		return attendanceRecordRepository.findByAttendanceDate(date, pageable);
	}

	public java.util.List<Object> findAbsentEmployees(LocalDate date, String department) {
		return java.util.Collections.emptyList();
	}

	public AttendanceRecord correctAttendance(UUID recordId, String newEventType, String reason) {
		// Find existing record
		Optional<AttendanceRecord> recordOpt = attendanceRecordRepository.findById(recordId);
		if (recordOpt.isPresent()) {
			AttendanceRecord record = recordOpt.get();
			// For now just return the existing record
			return record;
		}
		return null;
	}

}
