package com.company.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for recognition event data.
 *
 * Contains recognition event information for API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecognitionEventResponse {

	private UUID id;

	private UUID employeeId;

	private String employeeCode;

	private String employeeName;

	private UUID deviceId;

	private String deviceCode;

	private String deviceName;

	private Double confidenceScore;

	private String snapshotUrl;

	private Boolean isMatched;

	private Instant eventTime;

	private Instant createdAt;

}
