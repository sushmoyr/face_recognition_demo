package com.company.attendance.repository;

import com.company.attendance.entity.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository interface for Employee entity operations.
 * 
 * Provides CRUD operations and custom queries for employee management
 * including search, filtering, and face template related queries.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, UUID>, JpaSpecificationExecutor<Employee> {
    
    /**
     * Find employee by unique employee code.
     * 
     * @param employeeCode The employee code
     * @return Optional employee
     */
    Optional<Employee> findByEmployeeCode(String employeeCode);
    
    /**
     * Find employee by email address.
     * 
     * @param email The email address
     * @return Optional employee
     */
    Optional<Employee> findByEmail(String email);
    
    /**
     * Find employees by status.
     * 
     * @param status Employee status
     * @param pageable Pagination parameters
     * @return Page of employees
     */
    Page<Employee> findByStatus(Employee.EmployeeStatus status, Pageable pageable);
    
    /**
     * Find employees by department.
     * 
     * @param department Department name
     * @param pageable Pagination parameters
     * @return Page of employees
     */
    Page<Employee> findByDepartmentIgnoreCase(String department, Pageable pageable);
    
    /**
     * Find employees by name (first or last name).
     * 
     * @param name Name to search for
     * @param pageable Pagination parameters
     * @return Page of employees
     */
    @Query("SELECT e FROM Employee e WHERE " +
           "LOWER(e.firstName) LIKE LOWER(CONCAT('%', :name, '%')) OR " +
           "LOWER(e.lastName) LIKE LOWER(CONCAT('%', :name, '%'))")
    Page<Employee> findByNameContaining(@Param("name") String name, Pageable pageable);
    
    /**
     * Find all active employees.
     * 
     * @return List of active employees
     */
    @Query("SELECT e FROM Employee e WHERE e.status = 'ACTIVE'")
    List<Employee> findAllActive();
    
    /**
     * Find employees with face templates.
     * 
     * @param pageable Pagination parameters
     * @return Page of employees with face templates
     */
    @Query("SELECT DISTINCT e FROM Employee e " +
           "LEFT JOIN FETCH e.faceTemplates ft " +
           "WHERE ft.isActive = true")
    Page<Employee> findEmployeesWithFaceTemplates(Pageable pageable);
    
    /**
     * Find employees without face templates.
     * 
     * @param pageable Pagination parameters
     * @return Page of employees without face templates
     */
    @Query("SELECT e FROM Employee e WHERE e.id NOT IN " +
           "(SELECT DISTINCT ft.employee.id FROM FaceTemplate ft WHERE ft.isActive = true)")
    Page<Employee> findEmployeesWithoutFaceTemplates(Pageable pageable);
    
    /**
     * Count employees by department.
     * 
     * @return List of department counts
     */
    @Query("SELECT e.department, COUNT(e) FROM Employee e " +
           "WHERE e.status = 'ACTIVE' " +
           "GROUP BY e.department")
    List<Object[]> countEmployeesByDepartment();
    
    /**
     * Check if employee code exists.
     * 
     * @param employeeCode Employee code to check
     * @return true if exists
     */
    boolean existsByEmployeeCode(String employeeCode);
    
    /**
     * Check if email exists.
     * 
     * @param email Email to check
     * @return true if exists
     */
    boolean existsByEmail(String email);
}
