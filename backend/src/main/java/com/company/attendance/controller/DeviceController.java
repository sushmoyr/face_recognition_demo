package com.company.attendance.controller;

import com.company.attendance.dto.DeviceCreateRequest;
import com.company.attendance.dto.DeviceResponse;
import com.company.attendance.dto.DeviceUpdateRequest;
import com.company.attendance.dto.HeartbeatRequest;
import com.company.attendance.entity.Device;
import com.company.attendance.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

/**
 * REST controller for device management operations.
 * 
 * Provides CRUD operations for edge devices and heartbeat functionality.
 */
@RestController
@RequestMapping("/api/devices")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Device Management", description = "Edge device CRUD operations and status management")
@SecurityRequirement(name = "bearerAuth")
public class DeviceController {
    
    private final DeviceService deviceService;
    
    /**
     * Get all devices with pagination and filtering
     */
    @GetMapping
    @Operation(summary = "List all devices", description = "Get paginated list of devices with optional filtering")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    public ResponseEntity<Page<DeviceResponse>> getAllDevices(
            @RequestParam(required = false) String location,
            @RequestParam(required = false) Boolean active,
            @RequestParam(required = false) String search,
            Pageable pageable) {
        
        Page<Device> devices = deviceService.findAll(location, active, search, pageable);
        Page<DeviceResponse> response = devices.map(this::convertToResponse);
        
        log.debug("Retrieved {} devices", devices.getTotalElements());
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get device by ID
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get device by ID", description = "Retrieve device details by ID")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    public ResponseEntity<DeviceResponse> getDeviceById(@PathVariable UUID id) {
        Device device = deviceService.findById(id)
            .orElseThrow(() -> new RuntimeException("Device not found with id: " + id));
        
        return ResponseEntity.ok(convertToResponse(device));
    }
    
    /**
     * Get device by device code
     */
    @GetMapping("/code/{deviceCode}")
    @Operation(summary = "Get device by code", description = "Retrieve device details by device code")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER', 'EDGE_NODE')")
    public ResponseEntity<DeviceResponse> getDeviceByCode(@PathVariable String deviceCode) {
        Device device = deviceService.findByDeviceCode(deviceCode)
            .orElseThrow(() -> new RuntimeException("Device not found with code: " + deviceCode));
        
        return ResponseEntity.ok(convertToResponse(device));
    }
    
    /**
     * Create new device
     */
    @PostMapping
    @Operation(summary = "Create device", description = "Create a new edge device record")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceResponse> createDevice(@Valid @RequestBody DeviceCreateRequest request) {
        Device device = deviceService.create(request);
        
        log.info("Created new device: {} at {}", device.getName(), device.getLocation());
        return ResponseEntity.status(HttpStatus.CREATED).body(convertToResponse(device));
    }
    
    /**
     * Update device
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update device", description = "Update existing device record")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceResponse> updateDevice(
            @PathVariable UUID id, 
            @Valid @RequestBody DeviceUpdateRequest request) {
        
        Device device = deviceService.update(id, request);
        
        log.info("Updated device: {} at {}", device.getName(), device.getLocation());
        return ResponseEntity.ok(convertToResponse(device));
    }
    
    /**
     * Delete device (soft delete)
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Delete device", description = "Soft delete device record")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteDevice(@PathVariable UUID id) {
        deviceService.delete(id);
        
        log.info("Deleted device with id: {}", id);
        return ResponseEntity.noContent().build();
    }
    
    /**
     * Device heartbeat endpoint for edge nodes
     */
    @PostMapping("/heartbeat")
    @Operation(summary = "Device heartbeat", description = "Update device status and last seen time")
    @PreAuthorize("hasAnyRole('ADMIN', 'EDGE_NODE')")
    public ResponseEntity<?> heartbeat(@Valid @RequestBody HeartbeatRequest request) {
        try {
            Device device = deviceService.updateHeartbeat(request);
            
            log.debug("Heartbeat received from device: {} ({})", device.getName(), device.getDeviceCode());
            return ResponseEntity.ok().body("Heartbeat acknowledged");
            
        } catch (Exception e) {
            log.error("Heartbeat failed for device {}: {}", request.getDeviceCode(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body("Heartbeat failed: " + e.getMessage());
        }
    }
    
    /**
     * Get device statistics
     */
    @GetMapping("/stats")
    @Operation(summary = "Get device statistics", description = "Get device statistics and health summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
    public ResponseEntity<?> getDeviceStats() {
        try {
            var stats = deviceService.getDeviceStatistics();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error retrieving device statistics: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Error retrieving device statistics");
        }
    }
    
    /**
     * Activate/deactivate device
     */
    @PatchMapping("/{id}/status")
    @Operation(summary = "Update device status", description = "Activate or deactivate device")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<DeviceResponse> updateDeviceStatus(
            @PathVariable UUID id, 
            @RequestParam boolean active) {
        
        Device device = deviceService.updateStatus(id, active);
        
        log.info("Updated device status: {} ({}) -> {}", 
            device.getName(), device.getDeviceCode(), active ? "ACTIVE" : "INACTIVE");
        
        return ResponseEntity.ok(convertToResponse(device));
    }
    
    /**
     * Convert Device entity to DeviceResponse DTO
     */
    private DeviceResponse convertToResponse(Device device) {
        DeviceResponse response = new DeviceResponse();
        response.setId(device.getId());
        response.setDeviceCode(device.getDeviceCode());
        response.setName(device.getName());
        response.setLocation(device.getLocation());
        // Note: Device entity doesn't have ipAddress, macAddress, version etc.
        response.setIpAddress(null); // Not available in current entity
        response.setMacAddress(null); // Not available in current entity
        response.setStatus(device.getStatus().name());
        response.setVersion(null); // Not available in current entity
        response.setCapabilities(null); // Not available in current entity
        response.setLastHeartbeat(device.getLastSeenAt());
        response.setMetadata(null); // Not available in current entity
        response.setCreatedAt(device.getCreatedAt());
        response.setUpdatedAt(device.getUpdatedAt());
        return response;
    }
}
