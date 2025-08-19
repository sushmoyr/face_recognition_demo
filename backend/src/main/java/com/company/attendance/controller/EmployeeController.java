package com.company.attendance.controller;

import com.company.attendance.dto.EmployeeCreateRequest;
import com.company.attendance.dto.EmployeeResponse;
import com.company.attendance.dto.EmployeeUpdateRequest;
import com.company.attendance.dto.FaceEnrollmentRequest;
import com.company.attendance.entity.Employee;
import com.company.attendance.service.EmployeeService;
import com.company.attendance.service.FaceTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for employee management operations.
 *
 * Provides CRUD operations for employees and face enrollment functionality. Access
 * control is enforced based on user roles.
 */
@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Employee Management", description = "Employee CRUD operations and face enrollment")
@SecurityRequirement(name = "bearerAuth")
public class EmployeeController {

	private final EmployeeService employeeService;

	private final FaceTemplateService faceTemplateService;

	/**
	 * Get all employees with pagination and filtering
	 */
	@GetMapping
	@Operation(summary = "List all employees", description = "Get paginated list of employees with optional filtering")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
	public ResponseEntity<Page<EmployeeResponse>> getAllEmployees(@RequestParam(required = false) String department,
			@RequestParam(required = false) Boolean active, @RequestParam(required = false) String search,
			Pageable pageable) {

		Page<Employee> employees = employeeService.findAll(department, active, search, pageable);
		Page<EmployeeResponse> response = employees.map(this::convertToResponse);

		log.debug("Retrieved {} employees", employees.getTotalElements());
		return ResponseEntity.ok(response);
	}

	/**
	 * Get employee by ID
	 */
	@GetMapping("/{id}")
	@Operation(summary = "Get employee by ID", description = "Retrieve employee details by ID")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
	public ResponseEntity<EmployeeResponse> getEmployeeById(@PathVariable UUID id) {
		Employee employee = employeeService.findById(id)
			.orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));

		return ResponseEntity.ok(convertToResponse(employee));
	}

	/**
	 * Get employee by employee code
	 */
	@GetMapping("/code/{employeeCode}")
	@Operation(summary = "Get employee by code", description = "Retrieve employee details by employee code")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
	public ResponseEntity<EmployeeResponse> getEmployeeByCode(@PathVariable String employeeCode) {
		Employee employee = employeeService.findByEmployeeCode(employeeCode)
			.orElseThrow(() -> new RuntimeException("Employee not found with code: " + employeeCode));

		return ResponseEntity.ok(convertToResponse(employee));
	}

	/**
	 * Create new employee
	 */
	@PostMapping
	@Operation(summary = "Create employee", description = "Create a new employee record")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<EmployeeResponse> createEmployee(@Valid @RequestBody EmployeeCreateRequest request) {
		Employee employee = employeeService.create(request);

		log.info("Created new employee: {} ({})", employee.getFullName(), employee.getEmployeeCode());
		return ResponseEntity.status(HttpStatus.CREATED).body(convertToResponse(employee));
	}

	/**
	 * Update employee
	 */
	@PutMapping("/{id}")
	@Operation(summary = "Update employee", description = "Update existing employee record")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<EmployeeResponse> updateEmployee(@PathVariable UUID id,
			@Valid @RequestBody EmployeeUpdateRequest request) {

		Employee employee = employeeService.update(id, request);

		log.info("Updated employee: {} ({})", employee.getFullName(), employee.getEmployeeCode());
		return ResponseEntity.ok(convertToResponse(employee));
	}

	/**
	 * Delete employee (soft delete)
	 */
	@DeleteMapping("/{id}")
	@Operation(summary = "Delete employee", description = "Soft delete employee record")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<Void> deleteEmployee(@PathVariable UUID id) {
		employeeService.delete(id);

		log.info("Deleted employee with id: {}", id);
		return ResponseEntity.noContent().build();
	}

	/**
	 * Enroll face for employee using uploaded images
	 */
	@PostMapping("/{id}/faces")
	@Operation(summary = "Enroll face", description = "Upload and process face images for employee recognition")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR')")
	public ResponseEntity<?> enrollFace(@PathVariable UUID id, @RequestParam("images") List<MultipartFile> images,
			@RequestParam(value = "description", required = false) String description) {

		try {
			// Validate employee exists
			Employee employee = employeeService.findById(id)
				.orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));

			// Validate images
			if (images.isEmpty()) {
				return ResponseEntity.badRequest().body("No images provided");
			}

			for (MultipartFile image : images) {
				if (!isValidImageFile(image)) {
					return ResponseEntity.badRequest().body("Invalid image file: " + image.getOriginalFilename());
				}
			}

			// Process face enrollment
			int templatesCreated = faceTemplateService.enrollFromImages(employee, images, description);

			log.info("Enrolled {} face templates for employee: {} ({})", templatesCreated, employee.getFullName(),
					employee.getEmployeeCode());

			return ResponseEntity.ok().body("Successfully enrolled " + templatesCreated + " face templates");

		}
		catch (Exception e) {
			log.error("Face enrollment failed for employee {}: {}", id, e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("Face enrollment failed: " + e.getMessage());
		}
	}

	/**
	 * Enroll face using embedding vector (for edge nodes)
	 */
	@PostMapping("/{id}/faces/embedding")
	@Operation(summary = "Enroll face embedding", description = "Create face template from pre-computed embedding")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR')")
	public ResponseEntity<?> enrollFaceEmbedding(@PathVariable UUID id,
			@Valid @RequestBody FaceEnrollmentRequest request) {

		try {
			Employee employee = employeeService.findById(id)
				.orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));

			// Enroll face embedding
			faceTemplateService.enrollFromEmbedding(employee, request);

			log.info("Enrolled face embedding for employee: {} ({})", employee.getFullName(),
					employee.getEmployeeCode());

			return ResponseEntity.ok().body("Face embedding enrolled successfully");

		}
		catch (Exception e) {
			log.error("Face embedding enrollment failed for employee {}: {}", id, e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body("Face embedding enrollment failed: " + e.getMessage());
		}
	}

	/**
	 * Get face templates for employee
	 */
	@GetMapping("/{id}/faces")
	@Operation(summary = "Get face templates", description = "List all face templates for an employee")
	@PreAuthorize("hasAnyRole('ADMIN', 'HR', 'VIEWER')")
	public ResponseEntity<?> getFaceTemplates(@PathVariable UUID id) {
		try {
			var templates = faceTemplateService.findByEmployeeId(id);
			return ResponseEntity.ok(templates);
		}
		catch (Exception e) {
			log.error("Error retrieving face templates for employee {}: {}", id, e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving face templates");
		}
	}

	/**
	 * Get all employee face templates for edge node synchronization
	 */
	@GetMapping("/templates")
	@Operation(summary = "Get all templates", description = "Get all face templates for edge node sync")
	@PreAuthorize("hasAnyRole('ADMIN', 'EDGE_NODE')")
	public ResponseEntity<?> getAllTemplates() {
		try {
			var templates = faceTemplateService.findAllForSync();

			log.debug("Retrieved {} face templates for sync", templates.size());
			return ResponseEntity.ok(templates);
		}
		catch (Exception e) {
			log.error("Error retrieving templates for sync: {}", e.getMessage());
			return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error retrieving templates");
		}
	}

	/**
	 * Activate/deactivate employee
	 */
	@PatchMapping("/{id}/status")
	@Operation(summary = "Update employee status", description = "Activate or deactivate employee")
	@PreAuthorize("hasRole('ADMIN')")
	public ResponseEntity<EmployeeResponse> updateEmployeeStatus(@PathVariable UUID id, @RequestParam boolean active) {

		Employee employee = employeeService.updateStatus(id, active);

		log.info("Updated employee status: {} ({}) -> {}", employee.getFullName(), employee.getEmployeeCode(),
				active ? "ACTIVE" : "INACTIVE");

		return ResponseEntity.ok(convertToResponse(employee));
	}

	/**
	 * Convert Employee entity to EmployeeResponse DTO
	 */
	private EmployeeResponse convertToResponse(Employee employee) {
		EmployeeResponse response = new EmployeeResponse();
		response.setId(employee.getId());
		response.setEmployeeCode(employee.getEmployeeCode());
		response.setFirstName(employee.getFirstName());
		response.setLastName(employee.getLastName());
		response.setFullName(employee.getFullName());
		response.setEmail(employee.getEmail());
		response.setDepartment(employee.getDepartment());
		response.setPosition(employee.getPosition());
		response.setHireDate(employee.getHireDate());
		response.setStatus(employee.getStatus().name());
		response.setFaceTemplateCount(employee.getFaceTemplates() != null ? employee.getFaceTemplates().size() : 0);
		response.setCreatedAt(employee.getCreatedAt());
		response.setUpdatedAt(employee.getUpdatedAt());
		return response;
	}

	/**
	 * Validate uploaded image file
	 */
	private boolean isValidImageFile(MultipartFile file) {
		if (file.isEmpty()) {
			return false;
		}

		String contentType = file.getContentType();
		if (contentType == null) {
			return false;
		}

		return contentType.startsWith("image/") && (contentType.equals("image/jpeg") || contentType.equals("image/jpg")
				|| contentType.equals("image/png"));
	}

}
