package com.company.attendance.repository;

import com.company.attendance.entity.AttendanceRecord;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for AttendanceRecord entity operations.
 * 
 * Provides attendance reporting and analytics queries including
 * daily summaries, compliance tracking, and duration calculations.
 */
@Repository
public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {
    
    /**
     * Find attendance records by employee and date range.
     * 
     * @param employeeId Employee ID
     * @param startDate Start date
     * @param endDate End date
     * @param pageable Pagination parameters
     * @return Page of attendance records
     */
    Page<AttendanceRecord> findByEmployeeIdAndAttendanceDateBetween(
        UUID employeeId, LocalDate startDate, LocalDate endDate, Pageable pageable);
    
    /**
     * Find attendance records by date.
     * 
     * @param date Attendance date
     * @param pageable Pagination parameters
     * @return Page of attendance records
     */
    Page<AttendanceRecord> findByAttendanceDate(LocalDate date, Pageable pageable);
    
    /**
     * Find attendance records by employee and event type.
     * 
     * @param employeeId Employee ID
     * @param eventType Event type (IN/OUT)
     * @param pageable Pagination parameters
     * @return Page of attendance records
     */
    Page<AttendanceRecord> findByEmployeeIdAndEventType(
        UUID employeeId, AttendanceRecord.EventType eventType, Pageable pageable);
    
    /**
     * Find the last attendance record for an employee.
     * 
     * @param employeeId Employee ID
     * @return Optional last attendance record
     */
    @Query("SELECT ar FROM AttendanceRecord ar WHERE ar.employee.id = :employeeId " +
           "ORDER BY ar.eventTime DESC LIMIT 1")
    Optional<AttendanceRecord> findLastByEmployeeId(@Param("employeeId") UUID employeeId);
    
    /**
     * Find the last IN record for an employee on a specific date.
     * 
     * @param employeeId Employee ID
     * @param date Attendance date
     * @return Optional last IN record
     */
    @Query("SELECT ar FROM AttendanceRecord ar WHERE " +
           "ar.employee.id = :employeeId " +
           "AND ar.attendanceDate = :date " +
           "AND ar.eventType = 'IN' " +
           "ORDER BY ar.eventTime DESC LIMIT 1")
    Optional<AttendanceRecord> findLastInRecordForDate(@Param("employeeId") UUID employeeId,
                                                       @Param("date") LocalDate date);
    
    /**
     * Find attendance records for an employee on a specific date.
     * 
     * @param employeeId Employee ID
     * @param date Attendance date
     * @return List of attendance records
     */
    @Query("SELECT ar FROM AttendanceRecord ar WHERE " +
           "ar.employee.id = :employeeId AND ar.attendanceDate = :date " +
           "ORDER BY ar.eventTime")
    List<AttendanceRecord> findByEmployeeAndDate(@Param("employeeId") UUID employeeId,
                                                @Param("date") LocalDate date);
    
    /**
     * Find employees with late arrivals in date range.
     * 
     * @param startDate Start date
     * @param endDate End date
     * @return List of late arrival records
     */
    @Query("SELECT ar FROM AttendanceRecord ar WHERE " +
           "ar.attendanceDate BETWEEN :startDate AND :endDate " +
           "AND ar.isLate = true " +
           "AND ar.eventType = 'IN'")
    List<AttendanceRecord> findLateArrivals(@Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);
    
    /**
     * Find employees with early departures in date range.
     * 
     * @param startDate Start date
     * @param endDate End date
     * @return List of early departure records
     */
    @Query("SELECT ar FROM AttendanceRecord ar WHERE " +
           "ar.attendanceDate BETWEEN :startDate AND :endDate " +
           "AND ar.isEarlyLeave = true " +
           "AND ar.eventType = 'OUT'")
    List<AttendanceRecord> findEarlyDepartures(@Param("startDate") LocalDate startDate,
                                              @Param("endDate") LocalDate endDate);
    
    /**
     * Get daily attendance summary for a date.
     * 
     * @param date Date to summarize
     * @return List of attendance summary data
     */
    @Query("SELECT " +
           "COUNT(DISTINCT CASE WHEN ar.eventType = 'IN' THEN ar.employee.id END) as totalIn, " +
           "COUNT(DISTINCT CASE WHEN ar.eventType = 'OUT' THEN ar.employee.id END) as totalOut, " +
           "COUNT(DISTINCT CASE WHEN ar.eventType = 'IN' AND ar.isLate = true THEN ar.employee.id END) as lateCount, " +
           "COUNT(DISTINCT CASE WHEN ar.eventType = 'OUT' AND ar.isEarlyLeave = true THEN ar.employee.id END) as earlyLeaveCount " +
           "FROM AttendanceRecord ar WHERE ar.attendanceDate = :date")
    Object[] getDailySummary(@Param("date") LocalDate date);
    
    /**
     * Get employee attendance summary for date range.
     * 
     * @param employeeId Employee ID
     * @param startDate Start date
     * @param endDate End date
     * @return Employee attendance summary
     */
    @Query("SELECT " +
           "COUNT(DISTINCT ar.attendanceDate) as daysWorked, " +
           "COUNT(CASE WHEN ar.eventType = 'IN' AND ar.isLate = true THEN 1 END) as lateDays, " +
           "COUNT(CASE WHEN ar.eventType = 'OUT' AND ar.isEarlyLeave = true THEN 1 END) as earlyLeaveDays, " +
           "AVG(CASE WHEN ar.eventType = 'OUT' AND ar.durationMinutes IS NOT NULL THEN ar.durationMinutes END) as avgDurationMinutes " +
           "FROM AttendanceRecord ar WHERE " +
           "ar.employee.id = :employeeId " +
           "AND ar.attendanceDate BETWEEN :startDate AND :endDate")
    Object[] getEmployeeSummary(@Param("employeeId") UUID employeeId,
                               @Param("startDate") LocalDate startDate,
                               @Param("endDate") LocalDate endDate);
    
    /**
     * Find incomplete attendance records (IN without OUT).
     * 
     * @param date Date to check
     * @return List of incomplete records
     */
    @Query("SELECT ar FROM AttendanceRecord ar WHERE " +
           "ar.attendanceDate = :date " +
           "AND ar.eventType = 'IN' " +
           "AND ar.employee.id NOT IN (" +
           "  SELECT ar2.employee.id FROM AttendanceRecord ar2 " +
           "  WHERE ar2.attendanceDate = :date " +
           "  AND ar2.eventType = 'OUT' " +
           "  AND ar2.eventTime > ar.eventTime" +
           ")")
    List<AttendanceRecord> findIncompleteRecords(@Param("date") LocalDate date);
    
    /**
     * Count attendance records by status.
     * 
     * @param startDate Start date
     * @param endDate End date
     * @return List of status counts
     */
    @Query("SELECT ar.status, COUNT(ar) FROM AttendanceRecord ar " +
           "WHERE ar.attendanceDate BETWEEN :startDate AND :endDate " +
           "GROUP BY ar.status")
    List<Object[]> countByStatusInDateRange(@Param("startDate") LocalDate startDate,
                                           @Param("endDate") LocalDate endDate);
}
