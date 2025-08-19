package com.company.attendance.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Response DTO for authentication operations.
 * 
 * Contains JWT token and user information for successful authentication.
 */
@Data
@Builder
public class AuthResponse {
    
    private String token;
    private String type;
    private String username;
    private String role;
}
