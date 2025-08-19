package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Device entity representing cameras or edge nodes for face recognition.
 *
 * Stores device information including encrypted RTSP URLs and tracks device health and
 * connectivity.
 */
@Entity
@Table(name = "devices")
@Data
@EqualsAndHashCode(exclude = { "recognitionEvents", "attendanceRecords" })
@ToString(exclude = { "recognitionEvents", "attendanceRecords" })
public class Device {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "device_code", unique = true, nullable = false, length = 50)
	private String deviceCode;

	@Column(name = "name", nullable = false, length = 100)
	private String name;

	@Column(name = "location", length = 255)
	private String location;

	@Column(name = "rtsp_url_encrypted", columnDefinition = "TEXT")
	private String rtspUrlEncrypted;

	@Enumerated(EnumType.STRING)
	@Column(name = "device_type", nullable = false)
	private DeviceType deviceType = DeviceType.CAMERA;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private DeviceStatus status = DeviceStatus.ACTIVE;

	@Column(name = "last_seen_at")
	private Instant lastSeenAt;

	@OneToMany(mappedBy = "device", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<RecognitionEvent> recognitionEvents = new ArrayList<>();

	@OneToMany(mappedBy = "device", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
	private List<AttendanceRecord> attendanceRecords = new ArrayList<>();

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * Check if device is currently online.
	 * @return true if last seen within 5 minutes
	 */
	public boolean isOnline() {
		if (lastSeenAt == null) {
			return false;
		}
		return Instant.now().minusSeconds(300).isBefore(lastSeenAt);
	}

	/**
	 * Update the last seen timestamp to current time.
	 */
	public void updateLastSeen() {
		this.lastSeenAt = Instant.now();
	}

	public enum DeviceType {

		CAMERA, TERMINAL, MOBILE

	}

	public enum DeviceStatus {

		ACTIVE, INACTIVE, MAINTENANCE

	}

}
