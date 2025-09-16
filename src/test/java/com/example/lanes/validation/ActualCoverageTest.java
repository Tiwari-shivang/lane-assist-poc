package com.example.lanes.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test that ensures actual code coverage by directly instantiating and calling validation classes
 */
@SpringBootTest
public class ActualCoverageTest {

    private EUMarkingValidator validator;
    private CVProcessor cvProcessor;
    private ValidationResult validationResult;

    @BeforeEach
    void setUp() {
        // Directly instantiate classes to ensure they are instrumented
        validator = new EUMarkingValidator();
        cvProcessor = new CVProcessor();
        validationResult = new ValidationResult();
    }

    @Test
    @DisplayName("AC_001: Direct EUMarkingValidator Coverage Test")
    void testEUMarkingValidatorCoverage() {
        Map<String, Object> params = new HashMap<>();

        // Test validateMinimumWidth
        params.put("marking_type", "solid_line");
        params.put("width_m", 0.12);
        ValidationResult result = validator.validateMinimumWidth(params);
        assertTrue(result.isValid());

        // Test validateColor
        params.put("color", "white");
        result = validator.validateColor(params);
        assertTrue(result.isValid());

        // Test validateRetroReflection
        params.put("performance_class", "R3");
        params.put("retro_reflection_value", 150);
        result = validator.validateRetroReflection(params);
        assertTrue(result.isValid());

        // Test validateStrokeLength
        params.put("stroke_length_m", 2.0);
        result = validator.validateStrokeLength(params);
        assertTrue(result.isValid());

        // Test validateGapToStrokeRatio
        params.put("gap_m", 6.0);
        result = validator.validateGapToStrokeRatio(params);
        assertTrue(result.isValid());

        // Test validation failure case
        params.put("width_m", 0.05); // Below minimum
        result = validator.validateMinimumWidth(params);
        assertFalse(result.isValid());
        assertEquals("Width below minimum", result.getFailureReason());
    }

    @Test
    @DisplayName("AC_002: Direct CVProcessor Coverage Test")
    void testCVProcessorCoverage() {
        Map<String, Object> params = new HashMap<>();

        // Test validateRoadOverlap
        params.put("road_overlap_ratio", 0.8);
        params.put("total_pixels", 1000000);
        params.put("road_pixels", 800000);
        ValidationResult result = cvProcessor.validateRoadOverlap(params);
        assertTrue(result.isValid());

        // Test validateAreaFraction
        params.put("area_fraction", 0.002);
        params.put("marking_pixels", 4147);
        params.put("total_pixels", 1920 * 1080);
        result = cvProcessor.validateAreaFraction(params);
        assertTrue(result.isValid());

        // Test validateKernelSize
        params.put("kernel_size_px", 21);
        params.put("operation", "tophat");
        result = cvProcessor.validateKernelSize(params);
        assertTrue(result.isValid());

        // Test validatePreprocessing
        params.put("input_format", "BGR");
        params.put("grayscale_conversion", true);
        result = cvProcessor.validatePreprocessing(params);
        assertTrue(result.isValid());

        // Test validation failure case
        params.put("road_overlap_ratio", 0.5); // Below minimum
        result = cvProcessor.validateRoadOverlap(params);
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("AC_003: ValidationResult Coverage Test")
    void testValidationResultCoverage() {
        // Test default constructor
        ValidationResult result1 = new ValidationResult();
        assertFalse(result1.isValid());
        assertEquals(0, result1.getErrors().size());
        assertNull(result1.getMarkingType());

        // Test constructor with boolean
        ValidationResult result2 = new ValidationResult(true);
        assertTrue(result2.isValid());

        // Test constructor with boolean and reason
        ValidationResult result3 = new ValidationResult(false, "Test failure");
        assertFalse(result3.isValid());
        assertEquals("Test failure", result3.getFailureReason());
        assertEquals(1, result3.getErrors().size());

        // Test setters and getters
        result1.setValid(true);
        assertTrue(result1.isValid());

        result1.setMarkingType("test_type");
        assertEquals("test_type", result1.getMarkingType());

        result1.setConfidence(0.95);
        assertEquals(0.95, result1.getConfidence());

        result1.addError("Test error");
        assertFalse(result1.isValid()); // Should become false when error added
        assertTrue(result1.getErrors().contains("Test error"));
    }

    @Test
    @DisplayName("AC_004: Complex Validation Scenarios")
    void testComplexValidationScenarios() {
        Map<String, Object> params = new HashMap<>();

        // Test complete longitudinal marking validation
        params.put("marking_type", "solid_line");
        params.put("width_m", 0.12);
        params.put("stroke_length_m", 2.0);
        params.put("gap_m", 6.0);

        ValidationResult result = validator.validateCompleteLongitudinalMarking(params);
        assertTrue(result.isValid());
        assertEquals("solid_line", result.getMarkingType());

        // Test transverse marking validation
        params.clear();
        params.put("marking_type", "zebra_crossing");
        params.put("crossing_width_m", 4.0);
        params.put("v85_kph", 50.0);

        result = validator.validateTransverseMarking(params);
        assertTrue(result.isValid());

        // Test morphological pipeline
        params.clear();
        params.put("operation_sequence", new String[]{"tophat", "threshold", "dilate", "erode"});

        result = cvProcessor.validateMorphPipeline(params);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("AC_005: Error Handling and Edge Cases")
    void testErrorHandlingAndEdgeCases() {
        Map<String, Object> params = new HashMap<>();

        // Test null parameter handling in CVProcessor
        params.put("road_overlap_ratio", null);
        try {
            cvProcessor.validateRoadOverlap(params); // Null parameter
            fail("Should have thrown NullPointerException");
        } catch (NullPointerException | IllegalArgumentException e) {
            // Expected
        }

        // Test invalid color validation
        params.put("color", "red");
        params.put("marking_type", "solid_line");
        ValidationResult result = validator.validateColor(params);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().size() > 0);

        // Test threshold validation edge cases
        params.clear();
        params.put("threshold_type", "binary");
        params.put("threshold_value", 0); // Invalid value
        result = cvProcessor.validateThreshold(params);
        assertFalse(result.isValid());
        assertEquals("Invalid threshold value", result.getFailureReason());

        // Test contour parameter validation
        params.clear();
        params.put("mode", "RETR_EXTERNAL");
        params.put("min_area", 1000);
        params.put("max_area", 100); // Invalid range
        result = cvProcessor.validateContourParams(params);
        assertFalse(result.isValid());
        assertEquals("Invalid area range", result.getFailureReason());
    }

    @Test
    @DisplayName("AC_006: Performance and Accuracy Validation")
    void testPerformanceAndAccuracyValidation() {
        Map<String, Object> params = new HashMap<>();

        // Test processing time validation
        params.put("frame_processing_ms", 30);
        params.put("max_allowed_ms", 33);
        ValidationResult result = cvProcessor.validateProcessingTime(params);
        assertTrue(result.isValid());

        // Test accuracy metrics validation
        params.put("precision", 0.95);
        params.put("recall", 0.95);
        params.put("f1_score", 0.95);
        result = cvProcessor.validateAccuracyMetrics(params);
        assertTrue(result.isValid());

        // Test worn marking detection
        params.put("visibility_percentage", 70);
        params.put("detection_confidence", 0.8);
        result = cvProcessor.validateWornMarkingDetection(params);
        assertTrue(result.isValid());

        // Test failure cases
        params.put("precision", 0.85); // Below threshold
        result = cvProcessor.validateAccuracyMetrics(params);
        assertFalse(result.isValid());
    }
}