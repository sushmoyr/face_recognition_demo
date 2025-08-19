package com.company.attendance.controller;

import com.company.attendance.dto.LoginRequest;
import com.company.attendance.dto.LoginResponse;
import com.company.attendance.security.JwtTokenProvider;
import com.company.attendance.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

/**
 * Authentication controller for user login and token management.
 * 
 * Provides JWT-based authentication endpoints for different user roles
 * including admin, HR, viewer, and edge node access.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Authentication", description = "User authentication and authorization")
public class AuthController {
    
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final UserService userService;
    
    /**
     * User login endpoint.
     * 
     * @param loginRequest Login credentials
     * @return JWT token and user information
     */
    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user and return JWT token")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        log.info("Login attempt for user: {}", loginRequest.getUsername());
        
        try {
            // Authenticate the user
            Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                    loginRequest.getUsername(),
                    loginRequest.getPassword()
                )
            );
            
            // Generate JWT token
            String token = jwtTokenProvider.createToken(authentication);
            
            // Update last login timestamp
            userService.updateLastLogin(loginRequest.getUsername());
            
            // Create response
            LoginResponse response = new LoginResponse();
            response.setToken(token);
            response.setUsername(loginRequest.getUsername());
            response.setExpiresIn(jwtTokenProvider.getTokenValidityInSeconds());
            
            log.info("Successful login for user: {}", loginRequest.getUsername());
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            log.warn("Failed login attempt for user: {}", loginRequest.getUsername());
            throw e;
        }
    }
    
    /**
     * Token validation endpoint.
     * 
     * @param token JWT token to validate
     * @return Token validation status
     */
    @PostMapping("/validate")
    @Operation(summary = "Validate token", description = "Check if JWT token is valid")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String token) {
        try {
            String jwt = token.replace("Bearer ", "");
            boolean isValid = jwtTokenProvider.validateToken(jwt);
            
            if (isValid) {
                return ResponseEntity.ok().build();
            } else {
                return ResponseEntity.status(401).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }
    
    /**
     * Token refresh endpoint.
     * 
     * @param token Current JWT token
     * @return New JWT token
     */
    @PostMapping("/refresh")
    @Operation(summary = "Refresh token", description = "Get a new JWT token using current token")
    public ResponseEntity<LoginResponse> refresh(@RequestHeader("Authorization") String token) {
        try {
            String jwt = token.replace("Bearer ", "");
            String username = jwtTokenProvider.getUsername(jwt);
            
            if (jwtTokenProvider.validateToken(jwt)) {
                // Create new token
                Authentication authentication = jwtTokenProvider.getAuthentication(jwt);
                String newToken = jwtTokenProvider.createToken(authentication);
                
                LoginResponse response = new LoginResponse();
                response.setToken(newToken);
                response.setUsername(username);
                response.setExpiresIn(jwtTokenProvider.getTokenValidityInSeconds());
                
                return ResponseEntity.ok(response);
            } else {
                return ResponseEntity.status(401).build();
            }
        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }
}
