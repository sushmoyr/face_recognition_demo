package com.company.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Request DTO for device heartbeat.
 * 
 * Contains device status and health information.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HeartbeatRequest {
    
    @NotBlank(message = "Device code is required")
    @Size(max = 50, message = "Device code must not exceed 50 characters")
    private String deviceCode;
    
    @Size(max = 50, message = "Version must not exceed 50 characters")
    private String version;
    
    @Size(max = 45, message = "IP address must not exceed 45 characters")
    private String ipAddress;
    
    private Double cpuUsage;
    
    private Double memoryUsage;
    
    private Double diskUsage;
    
    private Integer temperature;
    
    private Map<String, Object> systemInfo;
    
    private String statusMessage;
}
