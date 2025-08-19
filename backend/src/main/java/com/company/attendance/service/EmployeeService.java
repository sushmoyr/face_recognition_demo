package com.company.attendance.service;

import com.company.attendance.dto.EmployeeCreateRequest;
import com.company.attendance.dto.EmployeeUpdateRequest;
import com.company.attendance.entity.Employee;
import com.company.attendance.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import jakarta.persistence.criteria.Predicate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for employee management operations.
 *
 * Handles CRUD operations and business logic for employees.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class EmployeeService {

	private final EmployeeRepository employeeRepository;

	/**
	 * Find all employees with optional filtering
	 */
	public Page<Employee> findAll(String department, Boolean active, String search, Pageable pageable) {
		Specification<Employee> spec = createEmployeeSpecification(department, active, search);
		return employeeRepository.findAll(spec, pageable);
	}

	/**
	 * Find employee by ID
	 */
	public Optional<Employee> findById(UUID id) {
		return employeeRepository.findById(id);
	}

	/**
	 * Find employee by employee code
	 */
	public Optional<Employee> findByEmployeeCode(String employeeCode) {
		return employeeRepository.findByEmployeeCode(employeeCode);
	}

	/**
	 * Create new employee
	 */
	public Employee create(EmployeeCreateRequest request) {
		// Check if employee code already exists
		if (employeeRepository.existsByEmployeeCode(request.getEmployeeCode())) {
			throw new RuntimeException("Employee code already exists: " + request.getEmployeeCode());
		}

		// Check if email already exists (if provided)
		if (StringUtils.hasText(request.getEmail()) && employeeRepository.existsByEmail(request.getEmail())) {
			throw new RuntimeException("Email already exists: " + request.getEmail());
		}

		// Parse full name into first and last name
		String[] nameParts = parseFullName(request.getFullName());

		Employee employee = new Employee();
		employee.setEmployeeCode(request.getEmployeeCode());
		employee.setFirstName(nameParts[0]);
		employee.setLastName(nameParts[1]);
		employee.setEmail(request.getEmail());
		employee.setDepartment(request.getDepartment());
		employee.setPosition(request.getPosition());
		employee.setHireDate(request.getHireDate());
		employee.setStatus(request.getActive() != null && request.getActive() ? Employee.EmployeeStatus.ACTIVE
				: Employee.EmployeeStatus.ACTIVE);

		Employee savedEmployee = employeeRepository.save(employee);
		log.info("Created employee: {} ({})", savedEmployee.getFullName(), savedEmployee.getEmployeeCode());

		return savedEmployee;
	}

	/**
	 * Update existing employee
	 */
	public Employee update(UUID id, EmployeeUpdateRequest request) {
		Employee employee = employeeRepository.findById(id)
			.orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));

		// Update only provided fields
		if (StringUtils.hasText(request.getFullName())) {
			String[] nameParts = parseFullName(request.getFullName());
			employee.setFirstName(nameParts[0]);
			employee.setLastName(nameParts[1]);
		}

		if (request.getEmail() != null) {
			// Check if new email conflicts with existing employee
			if (StringUtils.hasText(request.getEmail()) && !request.getEmail().equals(employee.getEmail())
					&& employeeRepository.existsByEmail(request.getEmail())) {
				throw new RuntimeException("Email already exists: " + request.getEmail());
			}
			employee.setEmail(request.getEmail());
		}

		if (request.getDepartment() != null) {
			employee.setDepartment(request.getDepartment());
		}

		if (request.getPosition() != null) {
			employee.setPosition(request.getPosition());
		}

		if (request.getHireDate() != null) {
			employee.setHireDate(request.getHireDate());
		}

		if (request.getActive() != null) {
			employee.setStatus(request.getActive() ? Employee.EmployeeStatus.ACTIVE : Employee.EmployeeStatus.INACTIVE);
		}

		Employee savedEmployee = employeeRepository.save(employee);
		log.info("Updated employee: {} ({})", savedEmployee.getFullName(), savedEmployee.getEmployeeCode());

		return savedEmployee;
	}

	/**
	 * Soft delete employee
	 */
	public void delete(UUID id) {
		Employee employee = employeeRepository.findById(id)
			.orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));

		employee.setStatus(Employee.EmployeeStatus.INACTIVE);
		employeeRepository.save(employee);

		log.info("Soft deleted employee: {} ({})", employee.getFullName(), employee.getEmployeeCode());
	}

	/**
	 * Update employee status
	 */
	public Employee updateStatus(UUID id, boolean active) {
		Employee employee = employeeRepository.findById(id)
			.orElseThrow(() -> new RuntimeException("Employee not found with id: " + id));

		employee.setStatus(active ? Employee.EmployeeStatus.ACTIVE : Employee.EmployeeStatus.INACTIVE);

		Employee savedEmployee = employeeRepository.save(employee);
		log.info("Updated employee status: {} ({}) -> {}", savedEmployee.getFullName(), savedEmployee.getEmployeeCode(),
				active ? "ACTIVE" : "INACTIVE");

		return savedEmployee;
	}

	/**
	 * Check if employee exists
	 */
	public boolean existsById(UUID id) {
		return employeeRepository.existsById(id);
	}

	/**
	 * Check if employee code exists
	 */
	public boolean existsByEmployeeCode(String employeeCode) {
		return employeeRepository.existsByEmployeeCode(employeeCode);
	}

	/**
	 * Get active employees count
	 */
	public long getActiveEmployeeCount() {
		return employeeRepository.findAllActive().size();
	}

	/**
	 * Get employees by department
	 */
	public List<Employee> findByDepartment(String department) {
		return employeeRepository.findByDepartmentIgnoreCase(department, PageRequest.of(0, Integer.MAX_VALUE))
			.getContent();
	}

	/**
	 * Parse full name into first and last name
	 */
	private String[] parseFullName(String fullName) {
		if (!StringUtils.hasText(fullName)) {
			return new String[] { "", "" };
		}

		String[] parts = fullName.trim().split("\\s+", 2);
		if (parts.length == 1) {
			return new String[] { parts[0], "" };
		}
		else {
			return new String[] { parts[0], parts[1] };
		}
	}

	/**
	 * Create dynamic specification for employee filtering
	 */
	private Specification<Employee> createEmployeeSpecification(String department, Boolean active, String search) {
		return (root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();

			// Filter by department
			if (StringUtils.hasText(department)) {
				predicates.add(
						criteriaBuilder.equal(criteriaBuilder.lower(root.get("department")), department.toLowerCase()));
			}

			// Filter by active status
			if (active != null) {
				Employee.EmployeeStatus status = active ? Employee.EmployeeStatus.ACTIVE
						: Employee.EmployeeStatus.INACTIVE;
				predicates.add(criteriaBuilder.equal(root.get("status"), status));
			}

			// Search in multiple fields
			if (StringUtils.hasText(search)) {
				String searchPattern = "%" + search.toLowerCase() + "%";
				Predicate searchPredicate = criteriaBuilder.or(
						criteriaBuilder.like(criteriaBuilder.lower(root.get("employeeCode")), searchPattern),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("firstName")), searchPattern),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("lastName")), searchPattern),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("email")), searchPattern),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("department")), searchPattern),
						criteriaBuilder.like(criteriaBuilder.lower(root.get("position")), searchPattern));
				predicates.add(searchPredicate);
			}

			return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
		};
	}

}
