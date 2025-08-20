package com.company.attendance.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

/**
 * Utility class for generating content hashes for recognition events.
 * 
 * Provides deduplication capabilities by creating unique fingerprints
 * from recognition event data including image content, employee, device, and timing.
 */
@Component
@Slf4j
public class HashUtils {
    
    private static final String HASH_ALGORITHM = "SHA-256";
    private static final int DEDUPLICATION_WINDOW_SECONDS = 300; // 5 minutes
    
    /**
     * Generate a unique hash for recognition event deduplication.
     * 
     * The hash is based on:
     * - Image file content (if available)
     * - Employee code
     * - Device ID  
     * - Time window (5-minute buckets for near-duplicate detection)
     * 
     * @param imagePath Path to the recognition image
     * @param employeeCode Employee code from recognition
     * @param deviceId Device ID that captured the recognition
     * @param eventTime When the recognition occurred
     * @return SHA-256 hash string for deduplication
     */
    public String generateRecognitionHash(String imagePath, String employeeCode, String deviceId, Instant eventTime) {
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            
            // Add image content hash if available
            String imageHash = generateImageHash(imagePath);
            if (imageHash != null) {
                digest.update(imageHash.getBytes());
            }
            
            // Add employee code
            if (employeeCode != null) {
                digest.update(employeeCode.getBytes());
            }
            
            // Add device ID
            if (deviceId != null) {
                digest.update(deviceId.getBytes());
            }
            
            // Add time window (5-minute buckets to catch near-duplicates)
            long timeWindow = eventTime.getEpochSecond() / DEDUPLICATION_WINDOW_SECONDS;
            digest.update(Long.toString(timeWindow).getBytes());
            
            byte[] hashBytes = digest.digest();
            return HexFormat.of().formatHex(hashBytes);
            
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate recognition hash - algorithm not available", e);
            // Fallback to simple concatenation hash
            return generateFallbackHash(imagePath, employeeCode, deviceId, eventTime);
        }
    }
    
    /**
     * Generate hash of image file content for duplicate detection.
     */
    private String generateImageHash(String imagePath) {
        if (imagePath == null || imagePath.trim().isEmpty()) {
            return null;
        }
        
        try {
            Path path = Paths.get(imagePath);
            if (!Files.exists(path)) {
                log.warn("Image file not found for hashing: {}", imagePath);
                return imagePath; // Use path as fallback
            }
            
            byte[] imageBytes = Files.readAllBytes(path);
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(imageBytes);
            return HexFormat.of().formatHex(hashBytes);
            
        } catch (Exception e) {
            log.warn("Failed to hash image file: {}", imagePath, e);
            return imagePath; // Use path as fallback
        }
    }
    
    /**
     * Fallback hash generation when SHA-256 is not available.
     */
    private String generateFallbackHash(String imagePath, String employeeCode, String deviceId, Instant eventTime) {
        StringBuilder sb = new StringBuilder();
        
        if (imagePath != null) {
            sb.append(imagePath);
        }
        if (employeeCode != null) {
            sb.append(employeeCode);
        }
        if (deviceId != null) {
            sb.append(deviceId);
        }
        
        // Use 5-minute window for deduplication
        long timeWindow = eventTime.getEpochSecond() / DEDUPLICATION_WINDOW_SECONDS;
        sb.append(timeWindow);
        
        return Integer.toHexString(sb.toString().hashCode());
    }
    
    /**
     * Generate a simple content hash for any string content.
     */
    public String generateContentHash(String content) {
        if (content == null || content.trim().isEmpty()) {
            return "";
        }
        
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            byte[] hashBytes = digest.digest(content.getBytes());
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            log.error("Failed to generate content hash", e);
            return Integer.toHexString(content.hashCode());
        }
    }
    
    /**
     * Check if two recognition events are likely duplicates based on timing.
     */
    public boolean isWithinDeduplicationWindow(Instant time1, Instant time2) {
        long timeDiff = Math.abs(time1.getEpochSecond() - time2.getEpochSecond());
        return timeDiff <= DEDUPLICATION_WINDOW_SECONDS;
    }
    
    /**
     * Get the current deduplication window (for testing).
     */
    public int getDeduplicationWindowSeconds() {
        return DEDUPLICATION_WINDOW_SECONDS;
    }
}
