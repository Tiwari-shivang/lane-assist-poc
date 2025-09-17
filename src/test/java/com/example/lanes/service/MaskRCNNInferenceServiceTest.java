package com.example.lanes.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(properties = {
    "inference.service.url=http://localhost:8000"
})
public class MaskRCNNInferenceServiceTest {

    @Test
    @DisplayName("MaskRCNN service initialization")
    void testServiceInitialization() {
        // Test that the service can be instantiated
        MaskRCNNInferenceService service = new MaskRCNNInferenceService("http://localhost:8000");
        assertNotNull(service);
    }
    
    @Test
    @DisplayName("Health check returns false when service unavailable")
    void testHealthCheckWhenServiceUnavailable() {
        MaskRCNNInferenceService service = new MaskRCNNInferenceService("http://localhost:9999");
        boolean healthy = service.isServiceHealthy();
        assertFalse(healthy, "Health check should return false when service is unavailable");
    }
    
    @Test
    @DisplayName("Exception handling for unavailable service")
    void testInferenceExceptionHandling() {
        MaskRCNNInferenceService service = new MaskRCNNInferenceService("http://localhost:9999");
        
        // Create a temporary test file
        java.io.File testFile = null;
        try {
            testFile = java.io.File.createTempFile("test", ".png");
            testFile.deleteOnExit();
            
            // Write a minimal PNG header to make it a valid file
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(testFile)) {
                // Minimal PNG signature
                fos.write(new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A});
            }
            
            final java.io.File finalTestFile = testFile;
            
            assertThrows(MaskRCNNInferenceService.InferenceServiceException.class, () -> {
                service.inferPolygons(finalTestFile, 5.0);
            }, "Should throw InferenceServiceException when service is unavailable");
            
        } catch (Exception e) {
            fail("Test setup failed: " + e.getMessage());
        } finally {
            if (testFile != null && testFile.exists()) {
                testFile.delete();
            }
        }
    }
}