package com.company.attendance.service;

import com.company.attendance.dto.RecognitionEventRequest;
import com.company.attendance.entity.RecognitionEvent;
import com.company.attendance.entity.Employee;
import com.company.attendance.entity.Device;
import com.company.attendance.repository.RecognitionEventRepository;
import com.company.attendance.repository.EmployeeRepository;
import com.company.attendance.repository.DeviceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Service for processing recognition events from edge devices.
 * 
 * Handles face recognition event ingestion, validation, and attendance processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class RecognitionEventService {
    
    private final RecognitionEventRepository recognitionEventRepository;
    private final EmployeeRepository employeeRepository;
    private final DeviceRepository deviceRepository;
    private final AttendanceService attendanceService;
    
    /**
     * Process incoming recognition event from edge device.
     */
    @Transactional
    public RecognitionEvent processRecognition(RecognitionEventRequest request) {
        log.debug("Processing recognition event for employee: {}", request.getEmployeeCode());
        
        // Find employee by code
        Employee employee = employeeRepository.findByEmployeeCode(request.getEmployeeCode())
            .orElse(null);
        
        // Find device by ID
        Device device = deviceRepository.findById(UUID.fromString(request.getDeviceId()))
            .orElse(null);
        
        // Create recognition event
        RecognitionEvent event = new RecognitionEvent();
        event.setEmployee(employee);
        event.setDevice(device);
        event.setConfidenceScore(request.getConfidence());
        event.setImagePath(request.getImagePath());
        event.setEventTime(Instant.now());
        
        // Save recognition event
        event = recognitionEventRepository.save(event);
        
        // Process attendance if employee found
        if (employee != null) {
            try {
                attendanceService.processRecognitionEvent(event);
            } catch (Exception e) {
                log.error("Failed to process attendance for recognition event: {}", event.getId(), e);
                // Continue - recognition event is still saved even if attendance processing fails
            }
        } else {
            log.warn("Recognition event created for unknown employee: {}", request.getEmployeeCode());
        }
        
        return event;
    }
    
    /**
     * Get recognition events by date range.
     */
    public Page<RecognitionEvent> getRecognitionsByDateRange(LocalDate startDate, LocalDate endDate, Pageable pageable) {
        return recognitionEventRepository.findByEventTimeBetween(
            startDate.atStartOfDay().toInstant(java.time.ZoneOffset.UTC),
            endDate.atTime(23, 59, 59).toInstant(java.time.ZoneOffset.UTC),
            pageable
        );
    }
    
    /**
     * Get recognition events by employee.
     */
    public Page<RecognitionEvent> getRecognitionsByEmployee(UUID employeeId, Pageable pageable) {
        return recognitionEventRepository.findByEmployeeId(employeeId, pageable);
    }
    
    /**
     * Get recognition events by device.
     */
    public Page<RecognitionEvent> getRecognitionsByDevice(UUID deviceId, Pageable pageable) {
        return recognitionEventRepository.findByDeviceId(deviceId, pageable);
    }
    
    /**
     * Get recent recognition events.
     */
    public Page<RecognitionEvent> getRecentRecognitions(Pageable pageable) {
        return recognitionEventRepository.findAllByOrderByEventTimeDesc(pageable);
    }
    
    /**
     * Get recognition event by ID.
     */
    public RecognitionEvent getRecognitionById(UUID id) {
        return recognitionEventRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Recognition event not found: " + id));
    }
}
