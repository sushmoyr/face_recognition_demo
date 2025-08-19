package com.company.attendance.repository;

import com.company.attendance.entity.Device;
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
 * Repository interface for Device entity operations.
 * 
 * Manages camera and edge device records including connectivity
 * status and location-based queries.
 */
@Repository
public interface DeviceRepository extends JpaRepository<Device, UUID> {
    
    /**
     * Find device by unique device code.
     * 
     * @param deviceCode Device code
     * @return Optional device
     */
    Optional<Device> findByDeviceCode(String deviceCode);
    
    /**
     * Find devices by status.
     * 
     * @param status Device status
     * @param pageable Pagination parameters
     * @return Page of devices
     */
    Page<Device> findByStatus(Device.DeviceStatus status, Pageable pageable);
    
    /**
     * Find devices by type.
     * 
     * @param deviceType Device type
     * @param pageable Pagination parameters
     * @return Page of devices
     */
    Page<Device> findByDeviceType(Device.DeviceType deviceType, Pageable pageable);
    
    /**
     * Find devices by location (case-insensitive contains).
     * 
     * @param location Location to search for
     * @param pageable Pagination parameters
     * @return Page of devices
     */
    @Query("SELECT d FROM Device d WHERE LOWER(d.location) LIKE LOWER(CONCAT('%', :location, '%'))")
    Page<Device> findByLocationContaining(@Param("location") String location, Pageable pageable);
    
    /**
     * Find all active devices.
     * 
     * @return List of active devices
     */
    @Query("SELECT d FROM Device d WHERE d.status = 'ACTIVE'")
    List<Device> findAllActive();
    
    /**
     * Find online devices (seen within last 5 minutes).
     * 
     * @param cutoffTime Time threshold for considering device online
     * @return List of online devices
     */
    @Query("SELECT d FROM Device d WHERE d.lastSeenAt > :cutoffTime AND d.status = 'ACTIVE'")
    List<Device> findOnlineDevices(@Param("cutoffTime") Instant cutoffTime);
    
    /**
     * Find offline devices (not seen within last 5 minutes).
     * 
     * @param cutoffTime Time threshold for considering device offline
     * @return List of offline devices
     */
    @Query("SELECT d FROM Device d WHERE " +
           "(d.lastSeenAt IS NULL OR d.lastSeenAt <= :cutoffTime) " +
           "AND d.status = 'ACTIVE'")
    List<Device> findOfflineDevices(@Param("cutoffTime") Instant cutoffTime);
    
    /**
     * Find devices that haven't been seen for a long time.
     * 
     * @param cutoffTime Time threshold
     * @return List of stale devices
     */
    @Query("SELECT d FROM Device d WHERE d.lastSeenAt < :cutoffTime OR d.lastSeenAt IS NULL")
    List<Device> findStaleDevices(@Param("cutoffTime") Instant cutoffTime);
    
    /**
     * Count devices by status.
     * 
     * @return List of status counts
     */
    @Query("SELECT d.status, COUNT(d) FROM Device d GROUP BY d.status")
    List<Object[]> countDevicesByStatus();
    
    /**
     * Count devices by type.
     * 
     * @return List of type counts
     */
    @Query("SELECT d.deviceType, COUNT(d) FROM Device d GROUP BY d.deviceType")
    List<Object[]> countDevicesByType();
    
    /**
     * Check if device code exists.
     * 
     * @param deviceCode Device code to check
     * @return true if exists
     */
    boolean existsByDeviceCode(String deviceCode);
    
    /**
     * Update last seen timestamp for a device.
     * 
     * @param deviceId Device ID
     * @param timestamp New timestamp
     */
    @Query("UPDATE Device d SET d.lastSeenAt = :timestamp WHERE d.id = :deviceId")
    void updateLastSeen(@Param("deviceId") UUID deviceId, @Param("timestamp") Instant timestamp);
}
