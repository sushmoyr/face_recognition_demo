package com.company.attendance.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Request DTO for submitting face recognition events from edge devices.
 */
@Data
public class RecognitionEventRequest {

	@NotNull(message = "Device ID is required")
	private UUID deviceId;

	@NotNull(message = "Captured timestamp is required")
	private Instant capturedAt;

	@NotNull(message = "Face embedding is required")
	@Size(min = 512, max = 512, message = "Face embedding must be exactly 512 dimensions")
	private float[] embedding;

	private UUID topCandidateEmployeeId;

	@DecimalMin(value = "0.0", message = "Similarity score must be non-negative")
	@DecimalMax(value = "1.0", message = "Similarity score must not exceed 1.0")
	private Double similarityScore;

	@DecimalMin(value = "0.0", message = "Liveness score must be non-negative")
	@DecimalMax(value = "1.0", message = "Liveness score must not exceed 1.0")
	private Double livenessScore;

	private Boolean livenessPassed;

	@Min(value = 0, message = "Face box coordinates must be non-negative")
	private Integer faceBoxX;

	@Min(value = 0, message = "Face box coordinates must be non-negative")
	private Integer faceBoxY;

	@Min(value = 1, message = "Face box dimensions must be positive")
	private Integer faceBoxWidth;

	@Min(value = 1, message = "Face box dimensions must be positive")
	private Integer faceBoxHeight;

	@Pattern(regexp = "^https?://.*", message = "Snapshot URL must be a valid HTTP(S) URL")
	private String snapshotUrl;

	@Min(value = 0, message = "Processing duration must be non-negative")
	private Integer processingDurationMs;

}
