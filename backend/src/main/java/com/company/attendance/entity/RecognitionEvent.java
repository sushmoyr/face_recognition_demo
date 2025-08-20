package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Recognition event entity storing face detection and recognition results.
 *
 * Each event represents a detected face with its embedding and matching information. Used
 * for attendance calculation and audit trail.
 */
@Entity
@Table(name = "recognition_events")
@Data
@EqualsAndHashCode(exclude = { "device", "employee", "attendanceRecord" })
@ToString(exclude = { "device", "employee", "attendanceRecord" })
public class RecognitionEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "device_id", nullable = false)
	private Device device;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "employee_id")
	private Employee employee; // NULL if no match found

	@Column(name = "captured_at", nullable = false)
	private Instant capturedAt;

	@Column(name = "embedding", columnDefinition = "vector(512)")
	private float[] embedding;

	@Column(name = "similarity_score")
	private Double similarityScore;

	@Column(name = "liveness_score")
	private Double livenessScore;

	@Column(name = "liveness_passed")
	private Boolean livenessPassed;

	@Column(name = "face_box_x")
	private Integer faceBoxX;

	@Column(name = "face_box_y")
	private Integer faceBoxY;

	@Column(name = "face_box_width")
	private Integer faceBoxWidth;

	@Column(name = "face_box_height")
	private Integer faceBoxHeight;

	@Column(name = "snapshot_url")
	private String snapshotUrl;

	@Column(name = "processing_duration_ms")
	private Integer processingDurationMs;

	@Column(name = "dedup_hash", length = 64)
	private String dedupHash;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false)
	private RecognitionStatus status = RecognitionStatus.PROCESSED;

	@OneToOne(mappedBy = "recognitionEvent", fetch = FetchType.LAZY)
	private AttendanceRecord attendanceRecord;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/**
	 * Check if the recognition has a valid match.
	 * @return true if employee is matched and similarity above threshold
	 */
	public boolean isValidMatch() {
		return employee != null && similarityScore != null && similarityScore >= 0.6
				&& (livenessPassed == null || livenessPassed);
	}

	/**
	 * Check if liveness detection passed.
	 * @return true if liveness check passed or was not performed
	 */
	public boolean isLivenessValid() {
		return livenessPassed == null || livenessPassed;
	}

	/**
	 * Get face bounding box as formatted string.
	 * @return Face box coordinates as "x,y,w,h" or null
	 */
	public String getFaceBoxString() {
		if (faceBoxX != null && faceBoxY != null && faceBoxWidth != null && faceBoxHeight != null) {
			return String.format("%d,%d,%d,%d", faceBoxX, faceBoxY, faceBoxWidth, faceBoxHeight);
		}
		return null;
	}

	// Additional getters for controller compatibility
	public Double getConfidenceScore() {
		return this.similarityScore;
	}

	public Instant getEventTime() {
		return this.capturedAt;
	}

	public enum RecognitionStatus {

		PENDING, PROCESSED, FAILED, DUPLICATE

	}

}
