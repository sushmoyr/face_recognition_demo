package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * User entity for authentication and authorization.
 *
 * Supports role-based access control with different permission levels for system
 * administration, HR operations, and edge node access.
 */
@Entity
@Table(name = "users")
@Data
@EqualsAndHashCode(exclude = { "passwordHash" })
public class User {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	private UUID id;

	@Column(name = "username", unique = true, nullable = false, length = 50)
	private String username;

	@Column(name = "password_hash", nullable = false, length = 255)
	private String passwordHash;

	@Column(name = "email", unique = true, length = 255)
	private String email;

	@Column(name = "first_name", length = 100)
	private String firstName;

	@Column(name = "last_name", length = 100)
	private String lastName;

	@Enumerated(EnumType.STRING)
	@Column(name = "role", nullable = false)
	private UserRole role;

	@Column(name = "is_active", nullable = false)
	private Boolean isActive = true;

	@Column(name = "last_login_at")
	private Instant lastLoginAt;

	@Column(name = "password_changed_at")
	private Instant passwordChangedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * Get the user's full name.
	 * @return Concatenated first and last name
	 */
	public String getFullName() {
		if (firstName == null && lastName == null) {
			return username;
		}
		return (firstName != null ? firstName : "") + " " + (lastName != null ? lastName : "");
	}

	/**
	 * Check if user has administrative privileges.
	 * @return true if role is ADMIN
	 */
	public boolean isAdmin() {
		return role == UserRole.ADMIN;
	}

	/**
	 * Check if user can manage employees.
	 * @return true if role is ADMIN or HR
	 */
	public boolean canManageEmployees() {
		return role == UserRole.ADMIN || role == UserRole.HR;
	}

	/**
	 * Check if user can submit recognition events.
	 * @return true if role is EDGE_NODE
	 */
	public boolean canSubmitRecognitions() {
		return role == UserRole.EDGE_NODE || role == UserRole.ADMIN;
	}

	/**
	 * Update last login timestamp.
	 */
	public void updateLastLogin() {
		this.lastLoginAt = Instant.now();
	}

	public enum UserRole {

		ADMIN, HR, VIEWER, EDGE_NODE

	}

}
