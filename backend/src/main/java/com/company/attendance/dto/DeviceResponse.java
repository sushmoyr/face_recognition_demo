package com.company.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for device data.
 * 
 * Contains public device information for API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceResponse {
    
    private UUID id;
    private String deviceCode;
    private String name;
    private String location;
    private String ipAddress;
    private String macAddress;
    private String status;
    private String version;
    private String capabilities;
    private Instant lastHeartbeat;
    private String metadata;
    private Instant createdAt;
    private Instant updatedAt;
}
