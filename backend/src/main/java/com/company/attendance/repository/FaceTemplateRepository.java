package com.company.attendance.repository;

import com.company.attendance.entity.FaceTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Repository interface for FaceTemplate entity operations.
 *
 * Provides operations for face template management including vector similarity search
 * using pgvector.
 */
@Repository
public interface FaceTemplateRepository extends JpaRepository<FaceTemplate, UUID> {

	/**
	 * Find all active face templates for an employee.
	 * @param employeeId Employee ID
	 * @return List of active face templates
	 */
	@Query("SELECT ft FROM FaceTemplate ft WHERE ft.employee.id = :employeeId AND ft.isActive = true")
	List<FaceTemplate> findActiveByEmployeeId(@Param("employeeId") UUID employeeId);

	/**
	 * Find all active face templates.
	 * @return List of all active face templates
	 */
	@Query("SELECT ft FROM FaceTemplate ft WHERE ft.isActive = true")
	List<FaceTemplate> findAllActive();

	/**
	 * Find face templates by employee and quality threshold.
	 * @param employeeId Employee ID
	 * @param qualityThreshold Minimum quality score
	 * @return List of high-quality face templates
	 */
	@Query("SELECT ft FROM FaceTemplate ft " + "WHERE ft.employee.id = :employeeId " + "AND ft.isActive = true "
			+ "AND ft.qualityScore >= :qualityThreshold " + "ORDER BY ft.qualityScore DESC")
	List<FaceTemplate> findHighQualityByEmployeeId(@Param("employeeId") UUID employeeId,
			@Param("qualityThreshold") Double qualityThreshold);

	/**
	 * Find the best quality face templates for an employee.
	 * @param employeeId Employee ID
	 * @param limit Maximum number of templates
	 * @return List of best quality face templates
	 */
	@Query(value = "SELECT * FROM face_templates ft " + "WHERE ft.employee_id = :employeeId "
			+ "AND ft.is_active = true " + "ORDER BY ft.quality_score DESC " + "LIMIT :limit", nativeQuery = true)
	List<FaceTemplate> findTopByQuality(@Param("employeeId") UUID employeeId, @Param("limit") int limit);

	/**
	 * Find similar face templates using cosine similarity. This uses pgvector's cosine
	 * similarity operator.
	 * @param embedding Query embedding vector
	 * @param threshold Similarity threshold (0.0 to 1.0)
	 * @param limit Maximum results
	 * @return List of similar face templates with similarity scores
	 */
	@Query(value = "SELECT ft.*, (1 - (ft.embedding <=> CAST(:embedding AS vector))) as similarity "
			+ "FROM face_templates ft " + "WHERE ft.is_active = true "
			+ "AND (1 - (ft.embedding <=> CAST(:embedding AS vector))) >= :threshold " + "ORDER BY similarity DESC "
			+ "LIMIT :limit", nativeQuery = true)
	List<Object[]> findSimilarTemplates(@Param("embedding") String embedding, @Param("threshold") Double threshold,
			@Param("limit") int limit);

	/**
	 * Find the most similar face template.
	 * @param embedding Query embedding vector as string
	 * @param threshold Minimum similarity threshold
	 * @return Most similar face template or null
	 */
	@Query(value = "SELECT ft.*, (1 - (ft.embedding <=> CAST(:embedding AS vector))) as similarity "
			+ "FROM face_templates ft " + "WHERE ft.is_active = true "
			+ "AND (1 - (ft.embedding <=> CAST(:embedding AS vector))) >= :threshold " + "ORDER BY similarity DESC "
			+ "LIMIT 1", nativeQuery = true)
	Object[] findMostSimilar(@Param("embedding") String embedding, @Param("threshold") Double threshold);

	/**
	 * Count active face templates for an employee.
	 * @param employeeId Employee ID
	 * @return Number of active face templates
	 */
	@Query("SELECT COUNT(ft) FROM FaceTemplate ft " + "WHERE ft.employee.id = :employeeId AND ft.isActive = true")
	long countActiveByEmployeeId(@Param("employeeId") UUID employeeId);

	/**
	 * Delete old face templates keeping only the best ones.
	 * @param employeeId Employee ID
	 * @param keepCount Number of templates to keep
	 */
	@Query(value = "UPDATE face_templates SET is_active = false " + "WHERE employee_id = :employeeId "
			+ "AND id NOT IN (" + "  SELECT id FROM (" + "    SELECT id FROM face_templates "
			+ "    WHERE employee_id = :employeeId AND is_active = true " + "    ORDER BY quality_score DESC "
			+ "    LIMIT :keepCount" + "  ) top_templates" + ")", nativeQuery = true)
	void deactivateOldTemplates(@Param("employeeId") UUID employeeId, @Param("keepCount") int keepCount);

}
