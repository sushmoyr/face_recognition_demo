package com.company.attendance.dto;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Response DTO for employee information.
 */
@Data
public class EmployeeResponse {
    
    private UUID id;
    private String employeeCode;
    private String firstName;
    private String lastName;
    private String fullName;
    private String email;
    private String department;
    private String position;
    private LocalDate hireDate;
    private String status;
    private Integer faceTemplateCount;
    private Instant createdAt;
    private Instant updatedAt;
}
