package com.company.attendance.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for updating existing devices.
 * 
 * All fields are optional for partial updates.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceUpdateRequest {
    
    @Size(max = 255, message = "Device name must not exceed 255 characters")
    private String name;
    
    @Size(max = 255, message = "Location must not exceed 255 characters")
    private String location;
    
    @Size(max = 45, message = "IP address must not exceed 45 characters")
    private String ipAddress;
    
    @Size(max = 17, message = "MAC address must not exceed 17 characters")
    private String macAddress;
    
    @Size(max = 50, message = "Version must not exceed 50 characters")
    private String version;
    
    private String capabilities;
    
    private String metadata;
    
    private Boolean active;
}
