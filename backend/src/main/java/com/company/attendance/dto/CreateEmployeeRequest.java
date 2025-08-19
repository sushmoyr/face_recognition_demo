package com.company.attendance.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDate;

/**
 * Request DTO for creating a new employee.
 */
@Data
public class CreateEmployeeRequest {

	@NotBlank(message = "Employee code is required")
	@Size(max = 50, message = "Employee code must not exceed 50 characters")
	private String employeeCode;

	@NotBlank(message = "First name is required")
	@Size(max = 100, message = "First name must not exceed 100 characters")
	private String firstName;

	@NotBlank(message = "Last name is required")
	@Size(max = 100, message = "Last name must not exceed 100 characters")
	private String lastName;

	@Email(message = "Invalid email format")
	@Size(max = 255, message = "Email must not exceed 255 characters")
	private String email;

	@Size(max = 100, message = "Department must not exceed 100 characters")
	private String department;

	@Size(max = 100, message = "Position must not exceed 100 characters")
	private String position;

	private LocalDate hireDate;

}
