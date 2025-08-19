package com.company.attendance.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request DTO for updating existing employees.
 * 
 * All fields are optional for partial updates.
 */
@Data
@Builder
public class EmployeeUpdateRequest {
    
    @Size(max = 255, message = "Full name must not exceed 255 characters")
    private String fullName;
    
    @Email(message = "Invalid email format")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;
    
    @Size(max = 20, message = "Phone number must not exceed 20 characters")
    private String phone;
    
    @Size(max = 100, message = "Department must not exceed 100 characters")
    private String department;
    
    @Size(max = 100, message = "Position must not exceed 100 characters")
    private String position;
    
    private LocalDate hireDate;
    
    private Boolean active;
}
