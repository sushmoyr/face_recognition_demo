package com.company.attendance;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

/**
 * Main Spring Boot application class for the Face Recognition Attendance System.
 * 
 * This application provides REST APIs for managing employees, face templates,
 * devices, recognition events, and attendance records with timezone support
 * for Asia/Dhaka.
 * 
 * Features:
 * - Employee management with face enrollment
 * - Camera/device registration and management
 * - Real-time face recognition event processing
 * - Automatic attendance calculation with shift logic
 * - Comprehensive reporting and analytics
 * - JWT-based authentication with role-based access control
 * - MinIO integration for face snapshot storage
 * - PostgreSQL with pgvector for embedding storage and similarity search
 * 
 * @author Face Recognition Attendance System
 * @version 1.0.0
 */
@SpringBootApplication
@EnableJpaRepositories
@EnableTransactionManagement
@EnableAsync
@EnableScheduling
public class AttendanceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AttendanceApplication.class, args);
    }
}
