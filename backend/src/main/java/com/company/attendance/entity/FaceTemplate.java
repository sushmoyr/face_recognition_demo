package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Face template entity storing face embeddings for employees.
 *
 * Each employee can have multiple face templates to handle variations in lighting,
 * angles, and facial expressions. Uses pgvector for efficient similarity search.
 */
@Entity
@Table(name = "face_templates")
@Data
@EqualsAndHashCode(exclude = { "employee" })
@ToString(exclude = { "employee" })
public class FaceTemplate {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "employee_id", nullable = false)
	private Employee employee;

	@Column(name = "embedding", nullable = false, columnDefinition = "vector(512)")
	private float[] embedding;

	@Column(name = "quality_score", nullable = false)
	private Double qualityScore;

	@Column(name = "source_image_url")
	private String sourceImageUrl;

	@Column(name = "extraction_model", length = 100)
	private String extractionModel;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * Check if the template is active and has good quality.
	 * @return true if active and quality score > 0.7
	 */
	public boolean isHighQuality() {
		return isActive && qualityScore != null && qualityScore > 0.7;
	}

	/**
	 * Get the embedding dimension.
	 * @return Length of the embedding array
	 */
	public int getEmbeddingDimension() {
		return embedding != null ? embedding.length : 0;
	}

}
