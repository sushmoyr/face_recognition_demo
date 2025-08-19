package com.company.attendance.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.TimeZone;

/**
 * Timezone utilities for attendance system.
 * 
 * Handles conversions between UTC (database storage) and Asia/Dhaka (business time),
 * including date boundary calculations and shift time handling.
 */
@Component
@Slf4j
public class TimezoneUtils {
    
    public static final ZoneId BUSINESS_TIMEZONE = ZoneId.of("Asia/Dhaka");
    public static final ZoneId UTC_TIMEZONE = ZoneId.of("UTC");
    
    public static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    public static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    /**
     * Get current time in business timezone (Asia/Dhaka).
     */
    public ZonedDateTime nowInBusinessTime() {
        return ZonedDateTime.now(BUSINESS_TIMEZONE);
    }
    
    /**
     * Get current time in UTC.
     */
    public ZonedDateTime nowInUtc() {
        return ZonedDateTime.now(UTC_TIMEZONE);
    }
    
    /**
     * Convert UTC Instant to business timezone.
     */
    public ZonedDateTime utcToBusinessTime(Instant utcInstant) {
        return utcInstant.atZone(BUSINESS_TIMEZONE);
    }
    
    /**
     * Convert business time to UTC Instant.
     */
    public Instant businessTimeToUtc(ZonedDateTime businessTime) {
        return businessTime.withZoneSameInstant(UTC_TIMEZONE).toInstant();
    }
    
    /**
     * Get current business date (Asia/Dhaka date).
     */
    public LocalDate getCurrentBusinessDate() {
        return nowInBusinessTime().toLocalDate();
    }
    
    /**
     * Get business date from UTC instant.
     */
    public LocalDate getBusinessDate(Instant utcInstant) {
        return utcToBusinessTime(utcInstant).toLocalDate();
    }
    
    /**
     * Get business time (LocalTime) from UTC instant.
     */
    public LocalTime getBusinessTime(Instant utcInstant) {
        return utcToBusinessTime(utcInstant).toLocalTime();
    }
    
    /**
     * Convert LocalDateTime in business timezone to UTC Instant.
     */
    public Instant businessDateTimeToUtc(LocalDateTime businessDateTime) {
        return businessDateTime.atZone(BUSINESS_TIMEZONE)
                              .withZoneSameInstant(UTC_TIMEZONE)
                              .toInstant();
    }
    
    /**
     * Create a business time instant for a specific date and time.
     */
    public Instant createBusinessTimeInstant(LocalDate date, LocalTime time) {
        LocalDateTime dateTime = LocalDateTime.of(date, time);
        return businessDateTimeToUtc(dateTime);
    }
    
    /**
     * Get the start of business day (00:00:00 in Asia/Dhaka) as UTC instant.
     */
    public Instant getBusinessDayStart(LocalDate businessDate) {
        return createBusinessTimeInstant(businessDate, LocalTime.MIN);
    }
    
    /**
     * Get the end of business day (23:59:59.999 in Asia/Dhaka) as UTC instant.
     */
    public Instant getBusinessDayEnd(LocalDate businessDate) {
        return createBusinessTimeInstant(businessDate, LocalTime.MAX);
    }
    
    /**
     * Check if two UTC instants are on the same business date.
     */
    public boolean isSameBusinessDate(Instant instant1, Instant instant2) {
        LocalDate date1 = getBusinessDate(instant1);
        LocalDate date2 = getBusinessDate(instant2);
        return date1.equals(date2);
    }
    
    /**
     * Calculate duration between two UTC instants in minutes.
     */
    public long getDurationMinutes(Instant start, Instant end) {
        return Duration.between(start, end).toMinutes();
    }
    
    /**
     * Calculate duration between two times on the same date.
     * Handles overnight shifts by adjusting end time if it's before start time.
     */
    public long getDurationMinutes(LocalTime startTime, LocalTime endTime, boolean isOvernightShift) {
        if (!isOvernightShift) {
            return Duration.between(startTime, endTime).toMinutes();
        } else {
            // For overnight shifts, if end time is before start time,
            // it means the end time is on the next day
            if (endTime.isBefore(startTime)) {
                return Duration.between(startTime, LocalTime.MAX).toMinutes() + 1 +
                       Duration.between(LocalTime.MIN, endTime).toMinutes();
            } else {
                return Duration.between(startTime, endTime).toMinutes();
            }
        }
    }
    
    /**
     * Check if a time falls within a range, handling overnight shifts.
     */
    public boolean isTimeInRange(LocalTime time, LocalTime rangeStart, LocalTime rangeEnd, boolean isOvernightRange) {
        if (!isOvernightRange) {
            return !time.isBefore(rangeStart) && !time.isAfter(rangeEnd);
        } else {
            // For overnight range, time is in range if it's after start OR before end
            return !time.isBefore(rangeStart) || !time.isAfter(rangeEnd);
        }
    }
    
    /**
     * Adjust time for overnight calculation.
     * If the time is before noon and it's an overnight shift, assume it's next day.
     */
    public LocalTime adjustTimeForOvernightShift(LocalTime time, LocalTime shiftStart) {
        // If time is significantly before shift start (e.g., shift starts at 22:00, time is 02:00),
        // it's likely the next day
        if (time.isBefore(LocalTime.NOON) && shiftStart.isAfter(LocalTime.NOON)) {
            return time; // Time is already in next day context
        }
        return time;
    }
    
    /**
     * Format business time for display.
     */
    public String formatBusinessTime(Instant utcInstant) {
        return utcToBusinessTime(utcInstant).format(DATETIME_FORMATTER);
    }
    
    /**
     * Format business date for display.
     */
    public String formatBusinessDate(LocalDate date) {
        return date.format(DATE_FORMATTER);
    }
    
    /**
     * Format time for display.
     */
    public String formatTime(LocalTime time) {
        return time.format(TIME_FORMATTER);
    }
    
    /**
     * Parse business date string.
     */
    public LocalDate parseBusinessDate(String dateString) {
        try {
            return LocalDate.parse(dateString, DATE_FORMATTER);
        } catch (Exception e) {
            log.warn("Failed to parse date string: {}", dateString, e);
            throw new IllegalArgumentException("Invalid date format. Expected: yyyy-MM-dd");
        }
    }
    
    /**
     * Parse time string.
     */
    public LocalTime parseTime(String timeString) {
        try {
            return LocalTime.parse(timeString, TIME_FORMATTER);
        } catch (Exception e) {
            log.warn("Failed to parse time string: {}", timeString, e);
            throw new IllegalArgumentException("Invalid time format. Expected: HH:mm:ss");
        }
    }
    
    /**
     * Get timezone offset for business timezone.
     */
    public ZoneOffset getBusinessTimezoneOffset() {
        return nowInBusinessTime().getOffset();
    }
    
    /**
     * Check if daylight saving time is in effect (Bangladesh doesn't use DST, but keeping for completeness).
     */
    public boolean isDaylightSavingTime() {
        return TimeZone.getTimeZone(BUSINESS_TIMEZONE).inDaylightTime(new java.util.Date());
    }
}
