package com.company.attendance.repository;

import com.company.attendance.entity.AttendancePolicy;
import com.company.attendance.entity.Shift;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for AttendancePolicy entity.
 * 
 * Handles CRUD operations and custom queries for attendance policies,
 * including policy lookups by shift and default policy management.
 */
@Repository
public interface AttendancePolicyRepository extends JpaRepository<AttendancePolicy, UUID> {
    
    /**
     * Find attendance policy by shift.
     */
    Optional<AttendancePolicy> findByShiftIdAndIsActiveTrue(UUID shiftId);
    
    /**
     * Find the default attendance policy.
     */
    Optional<AttendancePolicy> findByIsDefaultTrueAndIsActiveTrue();
    
    /**
     * Find attendance policy by shift entity.
     */
    Optional<AttendancePolicy> findByShiftAndIsActiveTrue(Shift shift);
    
    /**
     * Check if a policy exists for a given shift.
     */
    boolean existsByShiftIdAndIsActiveTrue(UUID shiftId);
    
    /**
     * Find all active policies ordered by name.
     */
    @Query("SELECT ap FROM AttendancePolicy ap WHERE ap.isActive = true ORDER BY ap.name ASC")
    java.util.List<AttendancePolicy> findAllActiveOrderByName();
    
    /**
     * Count active policies.
     */
    long countByIsActiveTrue();
    
    /**
     * Find policy by name (case-insensitive).
     */
    @Query("SELECT ap FROM AttendancePolicy ap WHERE LOWER(ap.name) = LOWER(:name) AND ap.isActive = true")
    Optional<AttendancePolicy> findByNameIgnoreCaseAndIsActiveTrue(@Param("name") String name);
}
