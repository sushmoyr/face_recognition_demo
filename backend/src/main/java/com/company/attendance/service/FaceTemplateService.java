package com.company.attendance.service;

import com.company.attendance.dto.FaceEnrollmentRequest;
import com.company.attendance.entity.Employee;
import com.company.attendance.entity.FaceTemplate;
import com.company.attendance.repository.FaceTemplateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Service for managing face templates and enrollment.
 * 
 * Handles face template creation, retrieval, and enrollment operations
 * by coordinating with the edge service for face processing.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FaceTemplateService {
    
    private final FaceTemplateRepository faceTemplateRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    @Value("${app.edge-service.url:http://localhost:8081}")
    private String edgeServiceUrl;
    
    /**
     * Enroll employee from uploaded images.
     * 
     * @param employee Employee to enroll
     * @param images List of face images
     * @param description Optional description
     * @return Number of face templates created
     */
    public int enrollFromImages(Employee employee, List<MultipartFile> images, String description) {
        log.info("Starting face enrollment for employee: {}", employee.getEmployeeCode());
        
        int templatesCreated = 0;
        List<String> errors = new ArrayList<>();
        
        for (int i = 0; i < images.size(); i++) {
            MultipartFile image = images.get(i);
            try {
                log.debug("Processing image {} of {} for employee {}", i + 1, images.size(), employee.getEmployeeCode());
                
                // Call edge service to process image
                List<FaceTemplate> templates = processImageWithEdgeService(employee, image, description);
                
                if (!templates.isEmpty()) {
                    // Save templates to database
                    faceTemplateRepository.saveAll(templates);
                    templatesCreated += templates.size();
                    log.debug("Created {} templates from image {}", templates.size(), i + 1);
                } else {
                    errors.add(String.format("No faces detected in image %d", i + 1));
                }
                
            } catch (Exception e) {
                log.error("Failed to process image {} for employee {}", i + 1, employee.getEmployeeCode(), e);
                errors.add(String.format("Error processing image %d: %s", i + 1, e.getMessage()));
            }
        }
        
        log.info("Enrollment completed for employee {}: {} templates created, {} errors", 
                employee.getEmployeeCode(), templatesCreated, errors.size());
        
        if (!errors.isEmpty()) {
            log.warn("Enrollment errors for employee {}: {}", employee.getEmployeeCode(), errors);
        }
        
        return templatesCreated;
    }
    
    /**
     * Enroll employee from pre-computed embedding.
     * 
     * @param employee Employee to enroll
     * @param request Face enrollment request with embedding
     */
    public void enrollFromEmbedding(Employee employee, FaceEnrollmentRequest request) {
        log.info("Creating face template from embedding for employee: {}", employee.getEmployeeCode());
        
        FaceTemplate template = new FaceTemplate();
        template.setEmployee(employee);
        template.setEmbedding(request.getEmbedding());
        template.setQualityScore(request.getConfidence() != null ? request.getConfidence() : 1.0);
        template.setSourceImageUrl(request.getSourceImageUrl());
        template.setExtractionModel("external-service");
        template.setIsActive(true);
        
        faceTemplateRepository.save(template);
        log.info("Face template created for employee: {}", employee.getEmployeeCode());
    }
    
    /**
     * Find all active face templates for an employee.
     * 
     * @param employeeId Employee ID
     * @return List of face templates
     */
    public List<FaceTemplate> findByEmployeeId(UUID employeeId) {
        return faceTemplateRepository.findActiveByEmployeeId(employeeId);
    }
    
    /**
     * Find all active face templates for synchronization.
     * 
     * @return List of all active face templates
     */
    public List<FaceTemplate> findAllForSync() {
        return faceTemplateRepository.findAllActive();
    }
    
    /**
     * Process image using edge service and create face templates.
     */
    private List<FaceTemplate> processImageWithEdgeService(Employee employee, MultipartFile image, String description) 
            throws IOException {
        
        // Prepare multipart request for edge service
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("image", new ByteArrayResource(image.getBytes()) {
            @Override
            public String getFilename() {
                return image.getOriginalFilename();
            }
        });
        
        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        
        // Call edge service
        String url = edgeServiceUrl + "/process-image";
        log.debug("Calling edge service at: {}", url);
        
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                requestEntity,
                String.class
        );
        
        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException("Edge service returned error: " + response.getStatusCode());
        }
        
        // Parse response
        JsonNode responseJson = objectMapper.readTree(response.getBody());
        
        if (!responseJson.get("success").asBoolean()) {
            log.warn("No faces detected in image for employee: {}", employee.getEmployeeCode());
            return new ArrayList<>();
        }
        
        // Create face templates from embeddings
        List<FaceTemplate> templates = new ArrayList<>();
        JsonNode embeddings = responseJson.get("embeddings");
        
        for (JsonNode embedding : embeddings) {
            // Only accept live faces with good confidence
            if (embedding.get("is_live").asBoolean() && 
                embedding.get("liveness_score").asDouble() >= 0.5 &&
                embedding.get("confidence").asDouble() >= 0.5) {
                
                FaceTemplate template = new FaceTemplate();
                template.setEmployee(employee);
                
                // Convert embedding array to float array
                JsonNode embeddingVector = embedding.get("embedding");
                float[] embeddingArray = new float[embeddingVector.size()];
                for (int i = 0; i < embeddingVector.size(); i++) {
                    embeddingArray[i] = (float) embeddingVector.get(i).asDouble();
                }
                template.setEmbedding(embeddingArray);
                
                template.setQualityScore(embedding.get("embedding_confidence").asDouble());
                template.setExtractionModel("edge-service-v1");
                template.setIsActive(true);
                
                // Set source image URL if needed (could be MinIO URL)
                if (description != null) {
                    template.setSourceImageUrl(description);
                }
                
                templates.add(template);
                log.debug("Created template with quality score: {}", template.getQualityScore());
            } else {
                log.debug("Skipped low quality face: confidence={}, liveness={}, live={}", 
                        embedding.get("confidence").asDouble(),
                        embedding.get("liveness_score").asDouble(),
                        embedding.get("is_live").asBoolean());
            }
        }
        
        return templates;
    }
    
    /**
     * Delete all face templates for an employee.
     * 
     * @param employeeId Employee ID
     */
    public void deleteAllByEmployeeId(UUID employeeId) {
        List<FaceTemplate> templates = faceTemplateRepository.findActiveByEmployeeId(employeeId);
        for (FaceTemplate template : templates) {
            template.setIsActive(false);
        }
        faceTemplateRepository.saveAll(templates);
        log.info("Deactivated {} face templates for employee: {}", templates.size(), employeeId);
    }
}
