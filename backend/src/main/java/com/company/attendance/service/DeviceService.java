package com.company.attendance.service;

import com.company.attendance.dto.DeviceCreateRequest;
import com.company.attendance.dto.DeviceUpdateRequest;
import com.company.attendance.dto.HeartbeatRequest;
import com.company.attendance.entity.Device;
import com.company.attendance.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for device management operations.
 *
 * Handles CRUD operations and business logic for edge devices.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class DeviceService {

	private final DeviceRepository deviceRepository;

	/**
	 * Find all devices with optional filtering
	 */
	public Page<Device> findAll(String location, Boolean active, String search, Pageable pageable) {
		// TODO: Implement dynamic specification filtering
		return deviceRepository.findAll(pageable);
	}

	/**
	 * Find device by ID
	 */
	public Optional<Device> findById(UUID id) {
		return deviceRepository.findById(id);
	}

	/**
	 * Find device by device code
	 */
	public Optional<Device> findByDeviceCode(String deviceCode) {
		return deviceRepository.findByDeviceCode(deviceCode);
	}

	/**
	 * Create new device
	 */
	public Device create(DeviceCreateRequest request) {
		// Check if device code already exists
		if (deviceRepository.existsByDeviceCode(request.getDeviceCode())) {
			throw new RuntimeException("Device code already exists: " + request.getDeviceCode());
		}

		Device device = new Device();
		device.setDeviceCode(request.getDeviceCode());
		device.setName(request.getName());
		device.setLocation(request.getLocation());
		// Note: Other fields like ipAddress, macAddress etc. don't exist in the Device
		// entity
		device.setStatus(request.getActive() != null && request.getActive() ? Device.DeviceStatus.ACTIVE
				: Device.DeviceStatus.ACTIVE);

		Device savedDevice = deviceRepository.save(device);
		log.info("Created device: {} at {}", savedDevice.getName(), savedDevice.getLocation());

		return savedDevice;
	}

	/**
	 * Update existing device
	 */
	public Device update(UUID id, DeviceUpdateRequest request) {
		Device device = deviceRepository.findById(id)
			.orElseThrow(() -> new RuntimeException("Device not found with id: " + id));

		// Update only provided fields that exist in the Device entity
		if (StringUtils.hasText(request.getName())) {
			device.setName(request.getName());
		}

		if (request.getLocation() != null) {
			device.setLocation(request.getLocation());
		}

		// Note: Other fields like ipAddress, macAddress, version etc. don't exist in
		// current Device entity

		if (request.getActive() != null) {
			device.setStatus(request.getActive() ? Device.DeviceStatus.ACTIVE : Device.DeviceStatus.INACTIVE);
		}

		Device savedDevice = deviceRepository.save(device);
		log.info("Updated device: {} at {}", savedDevice.getName(), savedDevice.getLocation());

		return savedDevice;
	}

	/**
	 * Soft delete device
	 */
	public void delete(UUID id) {
		Device device = deviceRepository.findById(id)
			.orElseThrow(() -> new RuntimeException("Device not found with id: " + id));

		device.setStatus(Device.DeviceStatus.INACTIVE);
		deviceRepository.save(device);

		log.info("Soft deleted device: {} at {}", device.getName(), device.getLocation());
	}

	/**
	 * Update device status
	 */
	public Device updateStatus(UUID id, boolean active) {
		Device device = deviceRepository.findById(id)
			.orElseThrow(() -> new RuntimeException("Device not found with id: " + id));

		device.setStatus(active ? Device.DeviceStatus.ACTIVE : Device.DeviceStatus.INACTIVE);

		Device savedDevice = deviceRepository.save(device);
		log.info("Updated device status: {} at {} -> {}", savedDevice.getName(), savedDevice.getLocation(),
				active ? "ACTIVE" : "INACTIVE");

		return savedDevice;
	}

	/**
	 * Update device heartbeat
	 */
	public Device updateHeartbeat(HeartbeatRequest request) {
		Device device = deviceRepository.findByDeviceCode(request.getDeviceCode())
			.orElseThrow(() -> new RuntimeException("Device not found with code: " + request.getDeviceCode()));

		// Update last seen time using the entity method
		device.updateLastSeen();
		device.setStatus(Device.DeviceStatus.ACTIVE); // Set to ACTIVE instead of ONLINE

		// Note: Device entity doesn't have version or ipAddress fields

		Device savedDevice = deviceRepository.save(device);
		log.debug("Updated heartbeat for device: {} at {}", savedDevice.getName(), savedDevice.getLocation());

		return savedDevice;
	}

	/**
	 * Get device statistics
	 */
	public Map<String, Object> getDeviceStatistics() {
		Map<String, Object> stats = new HashMap<>();

		long totalDevices = deviceRepository.count();
		// TODO: Implement proper status counting
		long activeDevices = totalDevices; // Stub
		long onlineDevices = 0; // Stub

		stats.put("totalDevices", totalDevices);
		stats.put("activeDevices", activeDevices);
		stats.put("onlineDevices", onlineDevices);
		stats.put("offlineDevices", totalDevices - onlineDevices);

		return stats;
	}

}
