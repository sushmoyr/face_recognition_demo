package com.company.attendance.repository;

import com.company.attendance.entity.RecognitionEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for RecognitionEvent entity operations.
 *
 * Manages face recognition events including deduplication queries and processing status
 * tracking.
 */
@Repository
public interface RecognitionEventRepository extends JpaRepository<RecognitionEvent, UUID> {

	/**
	 * Find recognition events by device.
	 * @param deviceId Device ID
	 * @param pageable Pagination parameters
	 * @return Page of recognition events
	 */
	Page<RecognitionEvent> findByDeviceId(UUID deviceId, Pageable pageable);

	/**
	 * Find recognition events by employee.
	 * @param employeeId Employee ID
	 * @param pageable Pagination parameters
	 * @return Page of recognition events
	 */
	Page<RecognitionEvent> findByEmployeeId(UUID employeeId, Pageable pageable);

	/**
	 * Find recognition events by time range.
	 * @param startTime Start time
	 * @param endTime End time
	 * @param pageable Pagination parameters
	 * @return Page of recognition events
	 */
	Page<RecognitionEvent> findByCapturedAtBetween(Instant startTime, Instant endTime, Pageable pageable);

	/**
	 * Find recognition events by status.
	 * @param status Recognition status
	 * @param pageable Pagination parameters
	 * @return Page of recognition events
	 */
	Page<RecognitionEvent> findByStatus(RecognitionEvent.RecognitionStatus status, Pageable pageable);

	/**
	 * Find recognition event by deduplication hash.
	 * @param dedupHash Deduplication hash
	 * @return Optional recognition event
	 */
	Optional<RecognitionEvent> findByDedupHash(String dedupHash);

	/**
	 * Find recent recognition events for deduplication.
	 * @param employeeId Employee ID
	 * @param deviceId Device ID
	 * @param timeWindow Time window to check
	 * @return List of recent recognition events
	 */
	@Query("SELECT re FROM RecognitionEvent re WHERE " + "re.employee.id = :employeeId "
			+ "AND re.device.id = :deviceId " + "AND re.capturedAt > :timeWindow " + "AND re.status != 'DUPLICATE'")
	List<RecognitionEvent> findRecentForDeduplication(@Param("employeeId") UUID employeeId,
			@Param("deviceId") UUID deviceId, @Param("timeWindow") Instant timeWindow);

	/**
	 * Find unprocessed recognition events.
	 * @return List of pending recognition events
	 */
	@Query("SELECT re FROM RecognitionEvent re WHERE re.status = 'PENDING' ORDER BY re.capturedAt")
	List<RecognitionEvent> findUnprocessed();

	/**
	 * Find recognition events without attendance records.
	 * @return List of recognition events needing attendance processing
	 */
	@Query("SELECT re FROM RecognitionEvent re WHERE " + "re.status = 'PROCESSED' " + "AND re.attendanceRecord IS NULL "
			+ "AND re.employee IS NOT NULL " + "ORDER BY re.capturedAt")
	List<RecognitionEvent> findWithoutAttendanceRecords();

	/**
	 * Count recognition events by status.
	 * @return List of status counts
	 */
	@Query("SELECT re.status, COUNT(re) FROM RecognitionEvent re GROUP BY re.status")
	List<Object[]> countByStatus();

	/**
	 * Count recognition events by device in time range.
	 * @param startTime Start time
	 * @param endTime End time
	 * @return List of device counts
	 */
	@Query("SELECT re.device.deviceCode, COUNT(re) FROM RecognitionEvent re "
			+ "WHERE re.capturedAt BETWEEN :startTime AND :endTime " + "GROUP BY re.device.deviceCode")
	List<Object[]> countByDeviceInTimeRange(@Param("startTime") Instant startTime, @Param("endTime") Instant endTime);

	/**
	 * Count successful matches by employee in time range.
	 * @param startTime Start time
	 * @param endTime End time
	 * @return List of employee match counts
	 */
	@Query("SELECT re.employee.employeeCode, COUNT(re) FROM RecognitionEvent re "
			+ "WHERE re.capturedAt BETWEEN :startTime AND :endTime " + "AND re.employee IS NOT NULL "
			+ "AND re.status = 'PROCESSED' " + "GROUP BY re.employee.employeeCode")
	List<Object[]> countMatchesByEmployeeInTimeRange(@Param("startTime") Instant startTime,
			@Param("endTime") Instant endTime);

	/**
	 * Find duplicate recognition events.
	 * @param pageable Pagination parameters
	 * @return Page of duplicate events
	 */
	@Query("SELECT re FROM RecognitionEvent re WHERE re.status = 'DUPLICATE'")
	Page<RecognitionEvent> findDuplicates(Pageable pageable);

	/**
	 * Check if dedup hash exists.
	 * @param dedupHash Deduplication hash
	 * @return true if exists
	 */
	boolean existsByDedupHash(String dedupHash);

	/**
	 * Delete old recognition events.
	 * @param cutoffTime Events older than this will be deleted
	 * @return Number of deleted events
	 */
	@Query("DELETE FROM RecognitionEvent re WHERE re.createdAt < :cutoffTime")
	int deleteOldEvents(@Param("cutoffTime") Instant cutoffTime);

}
