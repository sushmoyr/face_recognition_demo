package com.company.attendance.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Employee entity representing a company employee.
 * 
 * Contains personal information, employment details, and relationships
 * to face templates and attendance records.
 */
@Entity
@Table(name = "employees")
@Data
@EqualsAndHashCode(exclude = {"faceTemplates", "attendanceRecords", "schedules"})
@ToString(exclude = {"faceTemplates", "attendanceRecords", "schedules"})
public class Employee {
    
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Column(name = "employee_code", unique = true, nullable = false, length = 50)
    private String employeeCode;
    
    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;
    
    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;
    
    @Column(name = "email", length = 255)
    private String email;
    
    @Column(name = "department", length = 100)
    private String department;
    
    @Column(name = "position", length = 100)
    private String position;
    
    @Column(name = "hire_date")
    private LocalDate hireDate;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private EmployeeStatus status = EmployeeStatus.ACTIVE;
    
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<FaceTemplate> faceTemplates = new ArrayList<>();
    
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<AttendanceRecord> attendanceRecords = new ArrayList<>();
    
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<EmployeeSchedule> schedules = new ArrayList<>();
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shift_id")
    private Shift shift;
    
    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    
    /**
     * Get the employee's full name.
     * 
     * @return Concatenated first and last name
     */
    public String getFullName() {
        return firstName + " " + lastName;
    }
    
    /**
     * Check if the employee is active.
     * 
     * @return true if status is ACTIVE
     */
    public boolean isActive() {
        return status == EmployeeStatus.ACTIVE;
    }
    
    public enum EmployeeStatus {
        ACTIVE, INACTIVE, SUSPENDED
    }
}
