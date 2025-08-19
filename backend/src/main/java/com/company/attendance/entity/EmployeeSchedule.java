package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Employee schedule entity linking employees to shifts by day of week.
 *
 * Supports flexible scheduling with effective date ranges and multiple shifts per
 * employee.
 */
@Entity
@Table(name = "employee_schedules")
@Data
@EqualsAndHashCode(exclude = { "employee", "shift" })
@ToString(exclude = { "employee", "shift" })
public class EmployeeSchedule {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "employee_id", nullable = false)
	private Employee employee;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "shift_id", nullable = false)
	private Shift shift;

	@Column(name = "day_of_week", nullable = false)
	private Integer dayOfWeek; // 1=Monday, 7=Sunday

	@Column(name = "effective_from", nullable = false)
	private LocalDate effectiveFrom;

	@Column(name = "effective_until")
	private LocalDate effectiveUntil;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * Check if this schedule is effective for a given date.
	 * @param date Date to check
	 * @return true if schedule is active and within effective date range
	 */
	public boolean isEffectiveForDate(LocalDate date) {
		if (!isActive) {
			return false;
		}

		// Check if date is within effective range
		boolean afterFrom = !date.isBefore(effectiveFrom);
		boolean beforeUntil = effectiveUntil == null || !date.isAfter(effectiveUntil);

		return afterFrom && beforeUntil;
	}

	/**
	 * Get day of week as string.
	 * @return Day name (Monday, Tuesday, etc.)
	 */
	public String getDayOfWeekString() {
		return switch (dayOfWeek) {
			case 1 -> "Monday";
			case 2 -> "Tuesday";
			case 3 -> "Wednesday";
			case 4 -> "Thursday";
			case 5 -> "Friday";
			case 6 -> "Saturday";
			case 7 -> "Sunday";
			default -> "Unknown";
		};
	}

}
