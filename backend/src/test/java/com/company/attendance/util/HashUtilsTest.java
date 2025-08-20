package com.company.attendance.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for HashUtils deduplication functionality.
 * 
 * Tests hash generation consistency, collision handling, and time-based deduplication.
 */
@SpringBootTest
@ActiveProfiles("test")
class HashUtilsTest {

    private HashUtils hashUtils;
    
    @BeforeEach
    void setUp() {
        hashUtils = new HashUtils();
    }
    
    @Test
    void testGenerateRecognitionHash_Consistency() {
        // Given
        String imagePath = "/test/image.jpg";
        String employeeCode = "EMP001";
        String deviceId = "device-123";
        Instant eventTime = Instant.parse("2024-01-15T10:30:00Z");
        
        // When
        String hash1 = hashUtils.generateRecognitionHash(imagePath, employeeCode, deviceId, eventTime);
        String hash2 = hashUtils.generateRecognitionHash(imagePath, employeeCode, deviceId, eventTime);
        
        // Then
        assertNotNull(hash1);
        assertEquals(hash1, hash2, "Same inputs should produce same hash");
        assertEquals(64, hash1.length(), "Hash should be 64 characters (SHA-256)");
        assertTrue(hash1.matches("^[a-f0-9]{64}$"), "Hash should be hex string");
    }
    
    @Test
    void testGenerateRecognitionHash_DifferentInputs() {
        // Given
        Instant eventTime = Instant.parse("2024-01-15T10:30:00Z");
        
        // When - different employee codes
        String hash1 = hashUtils.generateRecognitionHash("/test/image.jpg", "EMP001", "device-123", eventTime);
        String hash2 = hashUtils.generateRecognitionHash("/test/image.jpg", "EMP002", "device-123", eventTime);
        
        // Then
        assertNotEquals(hash1, hash2, "Different employee codes should produce different hashes");
        
        // When - different device IDs
        String hash3 = hashUtils.generateRecognitionHash("/test/image.jpg", "EMP001", "device-456", eventTime);
        
        // Then
        assertNotEquals(hash1, hash3, "Different device IDs should produce different hashes");
    }
    
    @Test
    void testGenerateRecognitionHash_TimeWindowing() {
        // Given
        String imagePath = "/test/image.jpg";
        String employeeCode = "EMP001";
        String deviceId = "device-123";
        Instant baseTime = Instant.parse("2024-01-15T10:30:00Z");
        
        // When - times within same 5-minute window
        Instant time1 = baseTime;
        Instant time2 = baseTime.plus(2, ChronoUnit.MINUTES);
        Instant time3 = baseTime.plus(4, ChronoUnit.MINUTES);
        
        String hash1 = hashUtils.generateRecognitionHash(imagePath, employeeCode, deviceId, time1);
        String hash2 = hashUtils.generateRecognitionHash(imagePath, employeeCode, deviceId, time2);
        String hash3 = hashUtils.generateRecognitionHash(imagePath, employeeCode, deviceId, time3);
        
        // Then - should be same hash due to 5-minute bucketing
        assertEquals(hash1, hash2, "Times within 5-minute window should produce same hash");
        assertEquals(hash1, hash3, "Times within 5-minute window should produce same hash");
        
        // When - time outside window
        Instant time4 = baseTime.plus(6, ChronoUnit.MINUTES);
        String hash4 = hashUtils.generateRecognitionHash(imagePath, employeeCode, deviceId, time4);
        
        // Then
        assertNotEquals(hash1, hash4, "Times in different 5-minute windows should produce different hashes");
    }
    
    @Test
    void testGenerateRecognitionHash_NullInputs() {
        // Given
        Instant eventTime = Instant.parse("2024-01-15T10:30:00Z");
        
        // When/Then - null inputs should not cause exceptions
        assertDoesNotThrow(() -> {
            String hash = hashUtils.generateRecognitionHash(null, "EMP001", "device-123", eventTime);
            assertNotNull(hash);
            assertTrue(hash.length() > 0);
        });
        
        assertDoesNotThrow(() -> {
            String hash = hashUtils.generateRecognitionHash("/test/image.jpg", null, "device-123", eventTime);
            assertNotNull(hash);
            assertTrue(hash.length() > 0);
        });
        
        assertDoesNotThrow(() -> {
            String hash = hashUtils.generateRecognitionHash("/test/image.jpg", "EMP001", null, eventTime);
            assertNotNull(hash);
            assertTrue(hash.length() > 0);
        });
    }
    
    @Test
    void testGenerateContentHash() {
        // Given
        String content = "test content";
        
        // When
        String hash1 = hashUtils.generateContentHash(content);
        String hash2 = hashUtils.generateContentHash(content);
        String hash3 = hashUtils.generateContentHash("different content");
        
        // Then
        assertNotNull(hash1);
        assertEquals(hash1, hash2, "Same content should produce same hash");
        assertNotEquals(hash1, hash3, "Different content should produce different hashes");
    }
    
    @Test
    void testIsWithinDeduplicationWindow() {
        // Given
        Instant baseTime = Instant.parse("2024-01-15T10:30:00Z");
        
        // When/Then - within window
        Instant time1 = baseTime;
        Instant time2 = baseTime.plus(2, ChronoUnit.MINUTES);
        assertTrue(hashUtils.isWithinDeduplicationWindow(time1, time2), 
                  "2 minutes apart should be within deduplication window");
        
        Instant time3 = baseTime.plus(5, ChronoUnit.MINUTES);
        assertTrue(hashUtils.isWithinDeduplicationWindow(time1, time3),
                  "5 minutes apart should be within deduplication window");
        
        // When/Then - outside window
        Instant time4 = baseTime.plus(6, ChronoUnit.MINUTES);
        assertFalse(hashUtils.isWithinDeduplicationWindow(time1, time4),
                   "6 minutes apart should be outside deduplication window");
        
        // When/Then - reverse order
        assertTrue(hashUtils.isWithinDeduplicationWindow(time2, time1),
                  "Should work regardless of time order");
    }
    
    @Test
    void testGenerateImageHash_WithRealFile() throws IOException {
        // Given
        File tempFile = File.createTempFile("test-image", ".jpg");
        tempFile.deleteOnExit();
        
        // Write some content to the file
        String content = "fake image content for testing";
        Files.write(tempFile.toPath(), content.getBytes());
        
        // When
        String hash1 = hashUtils.generateRecognitionHash(tempFile.getAbsolutePath(), "EMP001", "device-123", Instant.now());
        String hash2 = hashUtils.generateRecognitionHash(tempFile.getAbsolutePath(), "EMP001", "device-123", Instant.now());
        
        // Then
        assertNotNull(hash1);
        assertNotNull(hash2);
        // Note: These may not be equal due to time bucketing, but both should be valid hashes
        assertTrue(hash1.length() > 0);
        assertTrue(hash2.length() > 0);
    }
    
    @Test
    void testGenerateImageHash_WithNonExistentFile() {
        // Given
        String nonExistentPath = "/path/that/does/not/exist.jpg";
        
        // When/Then - should not throw exception and should use path as fallback
        assertDoesNotThrow(() -> {
            String hash = hashUtils.generateRecognitionHash(nonExistentPath, "EMP001", "device-123", Instant.now());
            assertNotNull(hash);
            assertTrue(hash.length() > 0);
        });
    }
    
    @Test
    void testHashStability_AcrossMultipleCalls() {
        // Given
        String imagePath = "/test/image.jpg";
        String employeeCode = "EMP001";
        String deviceId = "device-123";
        Instant eventTime = Instant.parse("2024-01-15T10:30:00Z");
        
        // When - generate hash multiple times
        String[] hashes = new String[10];
        for (int i = 0; i < 10; i++) {
            hashes[i] = hashUtils.generateRecognitionHash(imagePath, employeeCode, deviceId, eventTime);
        }
        
        // Then - all hashes should be identical
        for (int i = 1; i < 10; i++) {
            assertEquals(hashes[0], hashes[i], 
                        "Hash should be stable across multiple calls with same input");
        }
    }
    
    @Test
    void testDeduplicationWindowValue() {
        // When
        int windowSeconds = hashUtils.getDeduplicationWindowSeconds();
        
        // Then
        assertEquals(300, windowSeconds, "Deduplication window should be 5 minutes (300 seconds)");
    }
}
