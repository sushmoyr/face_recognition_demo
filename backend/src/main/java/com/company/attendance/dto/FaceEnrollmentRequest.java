package com.company.attendance.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;
import lombok.Data;

/**
 * Request DTO for face enrollment using pre-computed embedding.
 * 
 * Used when enrolling face templates from edge nodes or external systems.
 */
@Data
@Builder
public class FaceEnrollmentRequest {
    
    @NotNull(message = "Embedding vector is required")
    @Size(min = 512, max = 512, message = "Embedding must be exactly 512 dimensions")
    private float[] embedding;
    
    @Size(max = 255, message = "Description must not exceed 255 characters")
    private String description;
    
    private String sourceImageUrl;
    
    private Double confidence;
}
