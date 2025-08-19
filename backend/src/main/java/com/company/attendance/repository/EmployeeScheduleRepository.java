package com.company.attendance.repository;

import com.company.attendance.entity.EmployeeSchedule;
import com.company.attendance.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for EmployeeSchedule entity operations.
 * 
 * Manages employee shift schedules and effective date queries.
 */
@Repository
public interface EmployeeScheduleRepository extends JpaRepository<EmployeeSchedule, UUID> {
    
    /**
     * Find schedules for an employee.
     * 
     * @param employeeId Employee ID
     * @return List of employee schedules
     */
    List<EmployeeSchedule> findByEmployeeId(UUID employeeId);
    
    /**
     * Find active schedules for an employee.
     * 
     * @param employeeId Employee ID
     * @return List of active employee schedules
     */
    @Query("SELECT es FROM EmployeeSchedule es WHERE es.employee.id = :employeeId AND es.isActive = true")
    List<EmployeeSchedule> findActiveByEmployeeId(@Param("employeeId") UUID employeeId);
    
    /**
     * Find effective shift for employee on a specific date and day of week.
     * 
     * @param employeeId Employee ID
     * @param date Date to check
     * @param dayOfWeek Day of week (1=Monday, 7=Sunday)
     * @return Optional shift
     */
    @Query("SELECT es.shift FROM EmployeeSchedule es WHERE " +
           "es.employee.id = :employeeId " +
           "AND es.dayOfWeek = :dayOfWeek " +
           "AND es.isActive = true " +
           "AND es.effectiveFrom <= :date " +
           "AND (es.effectiveUntil IS NULL OR es.effectiveUntil >= :date) " +
           "ORDER BY es.effectiveFrom DESC LIMIT 1")
    Optional<Shift> findEffectiveShiftForEmployeeAndDate(@Param("employeeId") UUID employeeId,
                                                         @Param("date") LocalDate date,
                                                         @Param("dayOfWeek") Integer dayOfWeek);
    
    /**
     * Find schedules for a specific shift.
     * 
     * @param shiftId Shift ID
     * @return List of employee schedules for the shift
     */
    List<EmployeeSchedule> findByShiftId(UUID shiftId);
    
    /**
     * Find schedules effective for a date range.
     * 
     * @param startDate Start date
     * @param endDate End date
     * @return List of effective schedules
     */
    @Query("SELECT es FROM EmployeeSchedule es WHERE " +
           "es.isActive = true " +
           "AND es.effectiveFrom <= :endDate " +
           "AND (es.effectiveUntil IS NULL OR es.effectiveUntil >= :startDate)")
    List<EmployeeSchedule> findEffectiveInDateRange(@Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate);
}
