package com.company.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for creating new devices.
 *
 * Contains validation rules for device creation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeviceCreateRequest {

	@NotBlank(message = "Device code is required")
	@Size(max = 50, message = "Device code must not exceed 50 characters")
	private String deviceCode;

	@NotBlank(message = "Device name is required")
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

	@Builder.Default
	private Boolean active = true;

}
