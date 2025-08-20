package com.company.attendance.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * JWT token provider for generating and validating JWT tokens.
 *
 * Handles token creation, validation, and extraction of user information with
 * configurable expiration times and secure signing.
 */
@Component
@Slf4j
public class JwtTokenProvider {

	private final SecretKey secretKey;

	private final long jwtExpiration;

	private final long refreshExpiration;

	public JwtTokenProvider(@Value("${app.jwt.secret}") String secret,
			@Value("${app.jwt.expiration:86400}") long jwtExpiration,
			@Value("${app.jwt.refresh-expiration:604800}") long refreshExpiration) {
		this.secretKey = Keys.hmacShaKeyFor(secret.getBytes());
		this.jwtExpiration = jwtExpiration * 1000; // Convert to milliseconds
		this.refreshExpiration = refreshExpiration * 1000;
	}

	/**
	 * Generate JWT token from authentication.
	 */
	public String generateToken(Authentication authentication) {
		UserDetails userPrincipal = (UserDetails) authentication.getPrincipal();
		return generateTokenFromUsername(userPrincipal.getUsername());
	}

	/**
	 * Generate JWT token from username.
	 */
	public String generateTokenFromUsername(String username) {
		Instant now = Instant.now();
		Instant expiryDate = now.plus(jwtExpiration, ChronoUnit.MILLIS);

		return Jwts.builder()
			.subject(username)
			.issuedAt(Date.from(now))
			.expiration(Date.from(expiryDate))
			.signWith(secretKey)
			.compact();
	}

	/**
	 * Generate refresh token.
	 */
	public String generateRefreshToken(String username) {
		Instant now = Instant.now();
		Instant expiryDate = now.plus(refreshExpiration, ChronoUnit.MILLIS);

		return Jwts.builder()
			.subject(username)
			.issuedAt(Date.from(now))
			.expiration(Date.from(expiryDate))
			.claim("type", "refresh")
			.signWith(secretKey)
			.compact();
	}

	/**
	 * Get username from JWT token.
	 */
	public String getUsernameFromToken(String token) {
		Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();

		return claims.getSubject();
	}

	/**
	 * Alias for getUsernameFromToken - used by AuthController.
	 */
	public String getUsername(String token) {
		return getUsernameFromToken(token);
	}

	/**
	 * Validate JWT token.
	 */
	public boolean validateToken(String authToken) {
		try {
			Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(authToken);
			return true;
		}
		catch (MalformedJwtException e) {
			log.error("Invalid JWT token: {}", e.getMessage());
		}
		catch (ExpiredJwtException e) {
			log.error("JWT token is expired: {}", e.getMessage());
		}
		catch (UnsupportedJwtException e) {
			log.error("JWT token is unsupported: {}", e.getMessage());
		}
		catch (IllegalArgumentException e) {
			log.error("JWT claims string is empty: {}", e.getMessage());
		}
		catch (Exception e) {
			log.error("JWT token validation error: {}", e.getMessage());
		}

		return false;
	}

	/**
	 * Get token expiration time in seconds.
	 */
	public long getExpirationTime() {
		return jwtExpiration / 1000;
	}

	/**
	 * Check if token is expired.
	 */
	public boolean isTokenExpired(String token) {
		try {
			Claims claims = Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();

			return claims.getExpiration().before(new Date());
		}
		catch (Exception e) {
			return true;
		}
	}

	// Additional methods for controller support
	public String createToken(Authentication authentication) {
		return generateToken(authentication);
	}

	public long getTokenValidityInSeconds() {
		return jwtExpiration / 1000;
	}

	public org.springframework.security.core.Authentication getAuthentication(String token) {
		String username = getUsernameFromToken(token);
		// Return a simple authentication token
		return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(username, null,
				java.util.Collections.emptyList());
	}

}
