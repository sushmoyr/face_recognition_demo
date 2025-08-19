package com.company.attendance.service;

import com.company.attendance.entity.User;
import com.company.attendance.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service for user management and authentication operations.
 */
@Service
@RequiredArgsConstructor
public class UserService {

	private final UserRepository userRepository;

	private final PasswordEncoder passwordEncoder;

	/**
	 * Find user by username.
	 */
	public Optional<User> findByUsername(String username) {
		return userRepository.findByUsername(username);
	}

	/**
	 * Create a new user.
	 */
	@Transactional
	public User createUser(String username, String password, String email, String firstName, String lastName,
			User.UserRole role) {
		User user = new User();
		user.setUsername(username);
		user.setPasswordHash(passwordEncoder.encode(password));
		user.setEmail(email);
		user.setFirstName(firstName);
		user.setLastName(lastName);
		user.setRole(role);
		user.setIsActive(true);
		user.setPasswordChangedAt(Instant.now());

		return userRepository.save(user);
	}

	/**
	 * Update last login timestamp.
	 */
	@Transactional
	public void updateLastLogin(String username) {
		Optional<User> user = userRepository.findByUsername(username);
		if (user.isPresent()) {
			user.get().updateLastLogin();
			userRepository.save(user.get());
		}
	}

	/**
	 * Find all active users.
	 */
	public List<User> findAllActive() {
		return userRepository.findAllActive();
	}

	/**
	 * Check if username exists.
	 */
	public boolean existsByUsername(String username) {
		return userRepository.existsByUsername(username);
	}

	/**
	 * Check if email exists.
	 */
	public boolean existsByEmail(String email) {
		return userRepository.existsByEmail(email);
	}

}
