package com.company.attendance.dto;

import lombok.Data;

/**
 * Response DTO for successful login.
 */
@Data
public class LoginResponse {
    
    private String token;
    private String username;
    private String role;
    private Long expiresIn; // seconds
}
