package com.company.attendance.repository;

import com.company.attendance.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for User entity operations.
 * 
 * Manages user authentication, authorization, and
 * user account administration.
 */
@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    
    /**
     * Find user by username for authentication.
     * 
     * @param username Username
     * @return Optional user
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Find user by email address.
     * 
     * @param email Email address
     * @return Optional user
     */
    Optional<User> findByEmail(String email);
    
    /**
     * Find active users by role.
     * 
     * @param role User role
     * @return List of active users with specified role
     */
    @Query("SELECT u FROM User u WHERE u.role = :role AND u.isActive = true")
    List<User> findActiveByRole(@Param("role") User.UserRole role);
    
    /**
     * Find all active users.
     * 
     * @return List of active users
     */
    @Query("SELECT u FROM User u WHERE u.isActive = true")
    List<User> findAllActive();
    
    /**
     * Find users who haven't logged in for a long time.
     * 
     * @param cutoffTime Time threshold
     * @return List of inactive users
     */
    @Query("SELECT u FROM User u WHERE " +
           "u.lastLoginAt < :cutoffTime OR u.lastLoginAt IS NULL")
    List<User> findInactiveUsers(@Param("cutoffTime") Instant cutoffTime);
    
    /**
     * Check if username exists.
     * 
     * @param username Username to check
     * @return true if exists
     */
    boolean existsByUsername(String username);
    
    /**
     * Check if email exists.
     * 
     * @param email Email to check
     * @return true if exists
     */
    boolean existsByEmail(String email);
    
    /**
     * Count users by role.
     * 
     * @return List of role counts
     */
    @Query("SELECT u.role, COUNT(u) FROM User u WHERE u.isActive = true GROUP BY u.role")
    List<Object[]> countActiveUsersByRole();
}
