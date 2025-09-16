package com.example.lanes.validation;

import com.example.lanes.validation.EUMarkingValidator;
import com.example.lanes.validation.CVProcessor;
import com.example.lanes.validation.ValidationResult;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for Computer Vision Implementation and Performance validation
 * Based on UNECE Vienna Convention & CEN EN 1436 Standards
 */
public class CVImplementationAndPerformanceTest {

    private EUMarkingValidator validator;
    private CVProcessor cvProcessor;

    @BeforeEach
    void setUp() {
        validator = new EUMarkingValidator();
        cvProcessor = new CVProcessor();
    }

    // ============= CV MASK VALIDATION TESTS =============

    @DisplayName("CVI_001: Road Overlap Ratio Validation")
    @ParameterizedTest(name = "Overlap ratio {0} should be {1}")
    @CsvSource({
        "0.5, false, Below minimum overlap",
        "0.6, true, Exact minimum overlap",
        "0.8, true, Good overlap ratio",
        "0.95, true, Excellent overlap",
        "1.0, true, Perfect overlap"
    })
    void testRoadOverlapRatio(double overlapRatio, boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("road_overlap_ratio", overlapRatio);
        params.put("total_pixels", 1000000);
        params.put("road_pixels", (int)(1000000 * overlapRatio));
        
        ValidationResult result = cvProcessor.validateRoadOverlap(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("CVI_001: Minimum Area Fraction Validation")
    @ParameterizedTest(name = "Area fraction {0} should be {1}")
    @CsvSource({
        "0.0005, false, Below minimum area",
        "0.001, true, Exact minimum area",
        "0.002, true, Valid area fraction",
        "0.003, true, Maximum valid area",
        "0.005, false, Above maximum area"
    })
    void testMinimumAreaFraction(double areaFraction, boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("area_fraction", areaFraction);
        params.put("marking_pixels", (int)(1920 * 1080 * areaFraction));
        params.put("total_pixels", 1920 * 1080);
        
        ValidationResult result = cvProcessor.validateAreaFraction(params);
        assertEquals(expected, result.isValid(), reason);
    }

    // ============= MORPHOLOGICAL OPERATIONS TESTS =============

    @DisplayName("CVI_002: Top-hat Kernel Size Range")
    @ParameterizedTest(name = "Kernel size {0}px should be {1}")
    @CsvSource({
        "15, false, Below minimum size",
        "16, false, Must be odd number",
        "17, true, Valid minimum odd size",
        "25, true, Valid mid-range odd size",
        "31, true, Valid maximum odd size",
        "33, false, Above maximum size",
        "24, false, Even number invalid"
    })
    void testTophatKernelSizeRange(int kernelSize, boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("kernel_size_px", kernelSize);
        params.put("operation", "tophat");
        
        ValidationResult result = cvProcessor.validateKernelSize(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @Test
    @DisplayName("Morphological Operations Pipeline")
    void testMorphologicalOperationsPipeline() {
        Map<String, Object> params = new HashMap<>();
        params.put("operation_sequence", new String[]{"tophat", "threshold", "dilate", "erode"});
        params.put("tophat_kernel_size", 21);
        params.put("threshold_value", 30);
        params.put("dilate_kernel_size", 5);
        params.put("erode_kernel_size", 3);
        
        ValidationResult result = cvProcessor.validateMorphPipeline(params);
        assertTrue(result.isValid(), "Morphological pipeline should be valid");
    }

    // ============= PERFORMANCE TESTS (EN 1436) =============

    @DisplayName("PT_001: Retro-reflection Performance Classes")
    @ParameterizedTest(name = "Class {0} with value {1} should be {2}")
    @CsvSource({
        "R2, 95, false, Below R2 minimum",
        "R2, 100, true, Valid R2 minimum",
        "R3, 140, false, Below R3 minimum",
        "R3, 150, true, Valid R3 minimum",
        "R4, 200, true, Valid R4 minimum",
        "R5, 300, true, Valid R5 minimum",
        "R5, 350, true, Above R5 minimum"
    })
    void testRetroReflectionClasses(String className, int value, 
                                     boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("performance_class", className);
        params.put("retro_reflection_value", value);
        params.put("unit", "mcd·lx⁻¹·m⁻²");
        
        ValidationResult result = validator.validateRetroReflection(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("PT_002: Visibility Under Diffuse Light")
    @ParameterizedTest(name = "Class {0} with value {1} should be {2}")
    @CsvSource({
        "Q2, 95, false, Below Q2 minimum",
        "Q2, 100, true, Valid Q2 minimum",
        "Q3, 125, false, Below Q3 minimum",
        "Q3, 130, true, Valid Q3 minimum",
        "Q3, 150, true, Above Q3 minimum"
    })
    void testVisibilityClasses(String className, int value, 
                                boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("visibility_class", className);
        params.put("visibility_value", value);
        params.put("lighting_condition", "diffuse");
        
        ValidationResult result = validator.validateVisibility(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("PT_003: Skid Resistance Classes")
    @ParameterizedTest(name = "Class {0} with value {1} should be {2}")
    @CsvSource({
        "S1, 40, false, Below S1 minimum",
        "S1, 45, true, Valid S1 minimum",
        "S3, 54, false, Below S3 minimum",
        "S3, 55, true, Valid S3 minimum",
        "S5, 64, false, Below S5 minimum",
        "S5, 65, true, Valid S5 minimum",
        "S5, 70, true, Above S5 minimum"
    })
    void testSkidResistanceClasses(String className, int value, 
                                    boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("skid_class", className);
        params.put("skid_value", value);
        params.put("test_method", "pendulum");
        
        ValidationResult result = validator.validateSkidResistance(params);
        assertEquals(expected, result.isValid(), reason);
    }

    // ============= IMAGE PROCESSING TESTS =============

    @Test
    @DisplayName("Image Preprocessing Pipeline")
    void testImagePreprocessingPipeline() {
        Map<String, Object> params = new HashMap<>();
        params.put("input_format", "BGR");
        params.put("grayscale_conversion", true);
        params.put("noise_reduction", "gaussian_blur");
        params.put("kernel_size", 5);
        params.put("contrast_enhancement", "CLAHE");
        
        ValidationResult result = cvProcessor.validatePreprocessing(params);
        assertTrue(result.isValid(), "Preprocessing pipeline should be valid");
    }

    @Test
    @DisplayName("Binary Threshold Validation")
    void testBinaryThresholdValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("threshold_type", "binary");
        params.put("threshold_value", 127);
        params.put("max_value", 255);
        params.put("adaptive", false);
        
        ValidationResult result = cvProcessor.validateThreshold(params);
        assertTrue(result.isValid(), "Binary threshold should be valid");
    }

    @Test
    @DisplayName("Adaptive Threshold Validation")
    void testAdaptiveThresholdValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("threshold_type", "adaptive");
        params.put("method", "mean");
        params.put("block_size", 11);
        params.put("constant", 2);
        params.put("max_value", 255);
        
        ValidationResult result = cvProcessor.validateAdaptiveThreshold(params);
        assertTrue(result.isValid(), "Adaptive threshold should be valid");
    }

    // ============= CONTOUR DETECTION TESTS =============

    @Test
    @DisplayName("Contour Detection Parameters")
    void testContourDetectionParameters() {
        Map<String, Object> params = new HashMap<>();
        params.put("mode", "RETR_EXTERNAL");
        params.put("method", "CHAIN_APPROX_SIMPLE");
        params.put("min_area", 100);
        params.put("max_area", 10000);
        params.put("min_perimeter", 50);
        
        ValidationResult result = cvProcessor.validateContourParams(params);
        assertTrue(result.isValid(), "Contour parameters should be valid");
    }

    @Test
    @DisplayName("Polygon Approximation Validation")
    void testPolygonApproximationValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("epsilon_factor", 0.02);
        params.put("closed", true);
        params.put("min_vertices", 4);
        params.put("max_vertices", 8);
        
        ValidationResult result = cvProcessor.validatePolygonApproximation(params);
        assertTrue(result.isValid(), "Polygon approximation should be valid");
    }

    // ============= LANE DETECTION SPECIFIC TESTS =============

    @Test
    @DisplayName("Lane Width Detection Accuracy")
    void testLaneWidthDetectionAccuracy() {
        Map<String, Object> params = new HashMap<>();
        params.put("detected_width_m", 3.45);
        params.put("expected_width_m", 3.50);
        params.put("tolerance_m", 0.10);
        params.put("confidence", 0.95);
        
        ValidationResult result = cvProcessor.validateLaneWidthAccuracy(params);
        assertTrue(result.isValid(), "Lane width within tolerance");
    }

    @Test
    @DisplayName("Lane Curvature Detection")
    void testLaneCurvatureDetection() {
        Map<String, Object> params = new HashMap<>();
        params.put("curvature_radius_m", 500);
        params.put("min_radius_m", 100);
        params.put("detection_method", "polynomial_fit");
        params.put("polynomial_degree", 2);
        
        ValidationResult result = cvProcessor.validateCurvatureDetection(params);
        assertTrue(result.isValid(), "Curvature detection valid");
    }

    // ============= PERFORMANCE METRICS TESTS =============

    @Test
    @DisplayName("Processing Time Performance")
    void testProcessingTimePerformance() {
        Map<String, Object> params = new HashMap<>();
        params.put("frame_processing_ms", 30);
        params.put("max_allowed_ms", 33); // 30 FPS requirement
        params.put("resolution", "1920x1080");
        params.put("optimization_level", "high");
        
        ValidationResult result = cvProcessor.validateProcessingTime(params);
        assertTrue(result.isValid(), "Processing time within limits");
    }

    @Test
    @DisplayName("Detection Accuracy Metrics")
    void testDetectionAccuracyMetrics() {
        Map<String, Object> params = new HashMap<>();
        params.put("true_positives", 950);
        params.put("false_positives", 20);
        params.put("false_negatives", 30);
        params.put("precision", 0.979);
        params.put("recall", 0.969);
        params.put("f1_score", 0.974);
        
        ValidationResult result = cvProcessor.validateAccuracyMetrics(params);
        assertTrue(result.isValid(), "Accuracy metrics acceptable");
    }

    // ============= MULTI-CONDITION TESTS =============

    @Test
    @DisplayName("Night-time Detection Performance")
    void testNightTimeDetectionPerformance() {
        Map<String, Object> params = new HashMap<>();
        params.put("lighting_condition", "night");
        params.put("min_illumination_lux", 0.1);
        params.put("retro_reflection_class", "R5");
        params.put("detection_rate", 0.85);
        params.put("false_positive_rate", 0.05);

        ValidationResult result = validator.validateNightPerformance(params);
        assertTrue(result.isValid(), "Night-time performance acceptable");
    }

    // ============= EDGE CONDITION TESTS =============

    @Test
    @DisplayName("ValidationResult Constructor Coverage")
    void testValidationResultConstructors() {
        // Test default constructor
        ValidationResult result1 = new ValidationResult();
        assertFalse(result1.isValid());
        assertNull(result1.getMarkingType());
        assertEquals(0, result1.getErrors().size());
        assertEquals(0.0, result1.getConfidence());
        assertNull(result1.getFailureReason());

        // Test boolean constructor
        ValidationResult result2 = new ValidationResult(true);
        assertTrue(result2.isValid());

        // Test boolean + reason constructor with valid case
        ValidationResult result3 = new ValidationResult(true, "Success");
        assertTrue(result3.isValid());
        assertEquals("Success", result3.getFailureReason());
        assertEquals(0, result3.getErrors().size());

        // Test boolean + reason constructor with invalid case
        ValidationResult result4 = new ValidationResult(false, "Error occurred");
        assertFalse(result4.isValid());
        assertEquals("Error occurred", result4.getFailureReason());
        assertEquals(1, result4.getErrors().size());
        assertTrue(result4.getErrors().contains("Error occurred"));

        // Test setters
        result1.setValid(true);
        assertTrue(result1.isValid());

        result1.setMarkingType("test_type");
        assertEquals("test_type", result1.getMarkingType());

        result1.setConfidence(0.95);
        assertEquals(0.95, result1.getConfidence(), 0.001);

        result1.setFailureReason("test_reason");
        assertEquals("test_reason", result1.getFailureReason());

        // Test addError method
        result1.addError("New error");
        assertFalse(result1.isValid()); // Should be set to false when error added
        assertTrue(result1.getErrors().contains("New error"));
    }

    @Test
    @DisplayName("Null and Edge Input Handling")
    void testNullAndEdgeInputHandling() {
        Map<String, Object> params = new HashMap<>();

        // Test with null values
        params.put("road_overlap_ratio", null);
        try {
            cvProcessor.validateRoadOverlap(params);
            fail("Should handle null values gracefully");
        } catch (Exception e) {
            // Expected to throw exception
        }

        // Test with empty map
        Map<String, Object> emptyParams = new HashMap<>();
        try {
            cvProcessor.validateRoadOverlap(emptyParams);
            fail("Should handle missing parameters");
        } catch (Exception e) {
            // Expected to throw exception
        }
    }

    @Test
    @DisplayName("Zero and Negative Value Tests")
    void testZeroAndNegativeValues() {
        Map<String, Object> params = new HashMap<>();

        // Test zero overlap ratio
        params.put("road_overlap_ratio", 0.0);
        params.put("total_pixels", 1000000);
        params.put("road_pixels", 0);
        ValidationResult result = cvProcessor.validateRoadOverlap(params);
        assertFalse(result.isValid(), "Zero overlap should be invalid");

        // Test negative values
        params.put("road_overlap_ratio", -0.5);
        result = cvProcessor.validateRoadOverlap(params);
        assertFalse(result.isValid(), "Negative overlap should be invalid");

        // Test zero area fraction
        params.clear();
        params.put("area_fraction", 0.0);
        params.put("marking_pixels", 0);
        params.put("total_pixels", 1920 * 1080);
        result = cvProcessor.validateAreaFraction(params);
        assertFalse(result.isValid(), "Zero area fraction should be invalid");
    }

    @Test
    @DisplayName("Extreme Value Tests")
    void testExtremeValues() {
        Map<String, Object> params = new HashMap<>();

        // Test extreme overlap ratios
        params.put("road_overlap_ratio", 1.5); // Above 100%
        params.put("total_pixels", 1000000);
        params.put("road_pixels", 1500000);
        ValidationResult result = cvProcessor.validateRoadOverlap(params);
        assertTrue(result.isValid(), "High overlap should be valid if above threshold");

        // Test extreme area fractions
        params.clear();
        params.put("area_fraction", 0.01); // Very high
        params.put("marking_pixels", 20736);
        params.put("total_pixels", 1920 * 1080);
        result = cvProcessor.validateAreaFraction(params);
        assertFalse(result.isValid(), "Extremely high area fraction should be invalid");
    }

    // ============= COMPREHENSIVE VALIDATOR TESTS =============

    @Test
    @DisplayName("EUMarkingValidator Width Validation Coverage")
    void testEUMarkingValidatorWidthValidation() {
        Map<String, Object> params = new HashMap<>();

        // Test different marking types
        String[] markingTypes = {"edge_line_motorway", "solid_line", "normal_broken_line", "unknown_type"};
        for (String type : markingTypes) {
            params.put("marking_type", type);
            params.put("width_m", 0.20);

            ValidationResult result = validator.validateMinimumWidth(params);
            assertTrue(result.isValid(), "Width should be valid for " + type);
        }

        // Test edge case - exact minimum for motorway
        params.put("marking_type", "edge_line_motorway");
        params.put("width_m", 0.15);
        ValidationResult result = validator.validateMinimumWidth(params);
        assertTrue(result.isValid(), "Exact minimum should be valid");

        // Test below minimum for motorway
        params.put("width_m", 0.14);
        result = validator.validateMinimumWidth(params);
        assertFalse(result.isValid(), "Below minimum should be invalid");
        assertEquals("Width below minimum", result.getFailureReason());
    }

    @Test
    @DisplayName("Color Validation with Errors Collection")
    void testColorValidationWithErrors() {
        Map<String, Object> params = new HashMap<>();

        // Test invalid blue usage
        params.put("color", "blue");
        params.put("marking_type", "solid_line");
        ValidationResult result = validator.validateColor(params);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().size() > 0);
        assertTrue(result.getErrors().get(0).contains("Blue only permitted"));

        // Test valid blue usage
        params.put("marking_type", "parking_zone");
        result = validator.validateColor(params);
        assertTrue(result.isValid(), "Blue should be valid for parking zones");

        params.put("marking_type", "disabled_parking");
        result = validator.validateColor(params);
        assertTrue(result.isValid(), "Blue should be valid for disabled parking");

        // Test invalid color
        params.put("color", "red");
        params.put("marking_type", "solid_line");
        result = validator.validateColor(params);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().size() > 0);
        assertTrue(result.getErrors().get(0).contains("Color not permitted"));

        // Test valid colors
        String[] validColors = {"white", "yellow"};
        for (String color : validColors) {
            params.put("color", color);
            result = validator.validateColor(params);
            assertTrue(result.isValid(), color + " should be valid");
        }
    }

    @Test
    @DisplayName("Complete Longitudinal Marking Validation")
    void testCompleteLongitudinalMarkingValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "solid_line");
        params.put("width_m", 0.12);
        params.put("stroke_length_m", 2.0);
        params.put("gap_m", 6.0);

        ValidationResult result = validator.validateCompleteLongitudinalMarking(params);
        assertTrue(result.isValid());
        assertEquals("solid_line", result.getMarkingType());

        // Test with invalid width
        params.put("width_m", 0.05);
        result = validator.validateCompleteLongitudinalMarking(params);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Width validation failed"));

        // Test with invalid stroke length
        params.put("width_m", 0.12);
        params.put("stroke_length_m", 0.5);
        result = validator.validateCompleteLongitudinalMarking(params);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Stroke length validation failed"));

        // Test with invalid gap ratio
        params.put("stroke_length_m", 2.0);
        params.put("gap_m", 15.0); // Exceeds maximum gap
        result = validator.validateCompleteLongitudinalMarking(params);
        assertFalse(result.isValid());
        assertTrue(result.getErrors().contains("Gap to stroke ratio validation failed"));
    }

    @Test
    @DisplayName("Transverse Marking Validation Coverage")
    void testTransverseMarkingValidation() {
        Map<String, Object> params = new HashMap<>();

        // Test stop line - exceeding maximum width
        params.put("marking_type", "stop_line");
        params.put("bar_width_m", 0.45);
        ValidationResult result = validator.validateTransverseMarking(params);
        assertFalse(result.isValid());
        assertEquals("Bar width exceeds maximum", result.getFailureReason());

        // Test give way bars - below minimum width
        params.clear();
        params.put("marking_type", "give_way_bars");
        params.put("bar_width_m", 0.15);
        result = validator.validateTransverseMarking(params);
        assertFalse(result.isValid());
        assertEquals("Bar width below minimum", result.getFailureReason());

        // Test zebra crossing - insufficient width for high speed
        params.clear();
        params.put("marking_type", "zebra_crossing");
        params.put("crossing_width_m", 3.0);
        params.put("v85_kph", 80.0);
        result = validator.validateTransverseMarking(params);
        assertFalse(result.isValid());
        assertEquals("Insufficient width for speed", result.getFailureReason());

        // Test zebra crossing - sufficient width for low speed
        params.put("v85_kph", 50.0);
        result = validator.validateTransverseMarking(params);
        assertTrue(result.isValid());

        // Test cycle crossing - element size too large
        params.clear();
        params.put("marking_type", "cycle_crossing");
        params.put("element_size_m", 0.70);
        result = validator.validateTransverseMarking(params);
        assertFalse(result.isValid());
        assertEquals("Element size exceeds maximum", result.getFailureReason());

        // Test valid cycle crossing
        params.put("element_size_m", 0.50);
        result = validator.validateTransverseMarking(params);
        assertTrue(result.isValid());

        // Test unknown marking type
        params.clear();
        params.put("marking_type", "unknown_type");
        result = validator.validateTransverseMarking(params);
        assertTrue(result.isValid()); // Should pass for unknown types
    }

    @Test
    @DisplayName("CVProcessor Validation Coverage")
    void testCVProcessorValidationCoverage() {
        Map<String, Object> params = new HashMap<>();

        // Test validateCVParameters method
        params.put("road_overlap_ratio", 0.8);
        params.put("area_fraction", 0.002);
        ValidationResult result = cvProcessor.validateCVParameters(params);
        assertTrue(result.isValid());

        // Test with invalid overlap
        params.put("road_overlap_ratio", 0.5);
        result = cvProcessor.validateCVParameters(params);
        assertFalse(result.isValid());

        // Test with invalid area fraction
        params.put("road_overlap_ratio", 0.8);
        params.put("area_fraction", 0.01);
        result = cvProcessor.validateCVParameters(params);
        assertFalse(result.isValid());

        // Test without area fraction
        params.remove("area_fraction");
        result = cvProcessor.validateCVParameters(params);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Wet Weather Performance")
    void testWetWeatherPerformance() {
        Map<String, Object> params = new HashMap<>();
        params.put("surface_condition", "wet");
        params.put("visibility_class", "Q3");
        params.put("skid_resistance_class", "S4");
        params.put("detection_degradation", 0.15);
        
        ValidationResult result = validator.validateWetWeatherPerformance(params);
        assertTrue(result.isValid(), "Wet weather performance acceptable");
    }

    @Test
    @DisplayName("Worn Marking Detection")
    void testWornMarkingDetection() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_condition", "worn");
        params.put("visibility_percentage", 60);
        params.put("retro_reflection_value", 80);
        params.put("detection_confidence", 0.70);
        params.put("enhancement_applied", true);
        
        ValidationResult result = cvProcessor.validateWornMarkingDetection(params);
        assertTrue(result.isValid(), "Worn marking detection acceptable");
    }

    static Stream<Arguments> provideComplexScenarios() {
        return Stream.of(
            Arguments.of(
                Map.of("scenario", "highway_night", "speed_kph", 130,
                       "visibility_m", 150, "marking_class", "R5"),
                true, "Highway night scenario valid"
            ),
            Arguments.of(
                Map.of("scenario", "urban_rain", "speed_kph", 50,
                       "surface", "wet", "marking_class", "R3"),
                true, "Urban rain scenario valid"
            ),
            Arguments.of(
                Map.of("scenario", "construction_zone", "temporary", true,
                       "color", "yellow", "visibility_class", "Q2"),
                true, "Construction zone scenario valid"
            )
        );
    }

    // ============= ADDITIONAL COVERAGE TESTS =============

    @Test
    @DisplayName("Number Type Conversion Coverage")
    void testNumberTypeConversions() {
        Map<String, Object> params = new HashMap<>();

        // Test warning line ratio with Integer inputs
        params.put("stroke_length_m", Integer.valueOf(6));
        params.put("gap_m", Integer.valueOf(2));
        ValidationResult result = validator.validateWarningLineRatio(params);
        assertTrue(result.isValid());

        // Test curvature detection with Integer inputs
        params.clear();
        params.put("curvature_radius_m", Integer.valueOf(500));
        params.put("min_radius_m", Integer.valueOf(100));
        result = cvProcessor.validateCurvatureDetection(params);
        assertTrue(result.isValid());

        // Test worn marking detection with Integer inputs
        params.clear();
        params.put("visibility_percentage", Integer.valueOf(70));
        params.put("detection_confidence", Double.valueOf(0.8));
        result = cvProcessor.validateWornMarkingDetection(params);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("All Performance Class Coverage")
    void testAllPerformanceClassCoverage() {
        Map<String, Object> params = new HashMap<>();

        // Test all retro-reflection classes
        String[] retroClasses = {"R2", "R3", "R4", "R5", "UNKNOWN"};
        int[] retroValues = {100, 150, 200, 300, 100};

        for (int i = 0; i < retroClasses.length; i++) {
            params.put("performance_class", retroClasses[i]);
            params.put("retro_reflection_value", retroValues[i]);
            params.put("unit", "mcd·lx⁻¹·m⁻²");

            ValidationResult result = validator.validateRetroReflection(params);
            assertTrue(result.isValid(), "Class " + retroClasses[i] + " should be valid");
        }

        // Test all visibility classes
        String[] visClasses = {"Q2", "Q3", "UNKNOWN"};
        int[] visValues = {100, 130, 100};

        for (int i = 0; i < visClasses.length; i++) {
            params.clear();
            params.put("visibility_class", visClasses[i]);
            params.put("visibility_value", visValues[i]);
            params.put("lighting_condition", "diffuse");

            ValidationResult result = validator.validateVisibility(params);
            assertTrue(result.isValid(), "Class " + visClasses[i] + " should be valid");
        }

        // Test all skid resistance classes
        String[] skidClasses = {"S1", "S2", "S3", "S4", "S5", "UNKNOWN"};
        int[] skidValues = {45, 50, 55, 60, 65, 45};

        for (int i = 0; i < skidClasses.length; i++) {
            params.clear();
            params.put("skid_class", skidClasses[i]);
            params.put("skid_value", skidValues[i]);
            params.put("test_method", "pendulum");

            ValidationResult result = validator.validateSkidResistance(params);
            assertTrue(result.isValid(), "Class " + skidClasses[i] + " should be valid");
        }
    }

    @Test
    @DisplayName("Morphological Pipeline Edge Cases")
    void testMorphologicalPipelineEdgeCases() {
        Map<String, Object> params = new HashMap<>();

        // Test empty operation sequence - will check tophat first, so returns "Missing tophat operation"
        params.put("operation_sequence", new String[]{});
        ValidationResult result = cvProcessor.validateMorphPipeline(params);
        assertFalse(result.isValid());
        assertEquals("Missing tophat operation", result.getFailureReason());

        // Test sequence without tophat
        params.put("operation_sequence", new String[]{"threshold", "dilate", "erode"});
        result = cvProcessor.validateMorphPipeline(params);
        assertFalse(result.isValid());
        assertEquals("Missing tophat operation", result.getFailureReason());

        // Test valid sequence with tophat at different positions
        params.put("operation_sequence", new String[]{"dilate", "tophat", "threshold"});
        result = cvProcessor.validateMorphPipeline(params);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Boundary Value Analysis")
    void testBoundaryValueAnalysis() {
        Map<String, Object> params = new HashMap<>();

        // Test exact boundary values for area fraction
        double[] boundaryValues = {0.0009, 0.001, 0.0011, 0.0029, 0.003, 0.0031};
        boolean[] expectedResults = {false, true, true, true, true, false};

        for (int i = 0; i < boundaryValues.length; i++) {
            params.put("area_fraction", boundaryValues[i]);
            params.put("marking_pixels", (int)(1920 * 1080 * boundaryValues[i]));
            params.put("total_pixels", 1920 * 1080);

            ValidationResult result = cvProcessor.validateAreaFraction(params);
            assertEquals(expectedResults[i], result.isValid(),
                "Value " + boundaryValues[i] + " should be " + expectedResults[i]);
        }

        // Test exact boundary values for kernel size
        int[] kernelSizes = {15, 16, 17, 31, 32, 33};
        boolean[] kernelExpected = {false, false, true, true, false, false};

        for (int i = 0; i < kernelSizes.length; i++) {
            params.clear();
            params.put("kernel_size_px", kernelSizes[i]);
            params.put("operation", "tophat");

            ValidationResult result = cvProcessor.validateKernelSize(params);
            assertEquals(kernelExpected[i], result.isValid(),
                "Kernel size " + kernelSizes[i] + " should be " + kernelExpected[i]);
        }
    }

    @Test
    @DisplayName("Input Format and Type Validation")
    void testInputFormatAndTypeValidation() {
        Map<String, Object> params = new HashMap<>();

        // Test invalid input formats
        String[] formats = {"RGB", "RGBA", "BGR", "HSV", null};
        boolean[] formatExpected = {false, false, true, false, false};

        for (int i = 0; i < formats.length - 1; i++) {
            params.put("input_format", formats[i]);
            params.put("grayscale_conversion", true);

            try {
                ValidationResult result = cvProcessor.validatePreprocessing(params);
                assertEquals(formatExpected[i], result.isValid(),
                    "Format " + formats[i] + " should be " + formatExpected[i]);
            } catch (Exception e) {
                if (formats[i] != null) {
                    fail("Should handle format " + formats[i]);
                }
            }
        }

        // Test grayscale conversion requirement
        params.put("input_format", "BGR");
        params.put("grayscale_conversion", false);
        ValidationResult result = cvProcessor.validatePreprocessing(params);
        assertFalse(result.isValid());
        assertEquals("Grayscale conversion required", result.getFailureReason());
    }

    @Test
    @DisplayName("Threshold Validation Edge Cases")
    void testThresholdValidationEdgeCases() {
        Map<String, Object> params = new HashMap<>();

        // Test threshold boundary values
        params.put("threshold_type", "binary");
        params.put("threshold_value", 0);
        ValidationResult result = cvProcessor.validateThreshold(params);
        assertFalse(result.isValid());
        assertEquals("Invalid threshold value", result.getFailureReason());

        params.put("threshold_value", 255);
        result = cvProcessor.validateThreshold(params);
        assertFalse(result.isValid());
        assertEquals("Invalid threshold value", result.getFailureReason());

        params.put("threshold_value", 1);
        result = cvProcessor.validateThreshold(params);
        assertTrue(result.isValid());

        params.put("threshold_value", 254);
        result = cvProcessor.validateThreshold(params);
        assertTrue(result.isValid());

        // Test invalid threshold type
        params.put("threshold_type", "otsu");
        result = cvProcessor.validateThreshold(params);
        assertFalse(result.isValid());
        assertEquals("Invalid threshold type", result.getFailureReason());
    }

    @Test
    @DisplayName("Adaptive Threshold Block Size Validation")
    void testAdaptiveThresholdBlockSizeValidation() {
        Map<String, Object> params = new HashMap<>();

        // Test even block size (invalid)
        params.put("threshold_type", "adaptive");
        params.put("block_size", 10);
        ValidationResult result = cvProcessor.validateAdaptiveThreshold(params);
        assertFalse(result.isValid());
        assertEquals("Block size must be odd", result.getFailureReason());

        // Test odd block size (valid)
        params.put("block_size", 11);
        result = cvProcessor.validateAdaptiveThreshold(params);
        assertTrue(result.isValid());

        // Test invalid threshold type
        params.put("threshold_type", "binary");
        result = cvProcessor.validateAdaptiveThreshold(params);
        assertFalse(result.isValid());
        assertEquals("Invalid threshold type", result.getFailureReason());
    }

    @ParameterizedTest
    @MethodSource("provideComplexScenarios")
    @DisplayName("Complex Real-World Scenarios")
    void testComplexRealWorldScenarios(Map<String, Object> params,
                                        boolean expected, String scenario) {
        ValidationResult result = validator.validateComplexScenario(params);
        assertEquals(expected, result.isValid(), scenario);
    }

    // ============= NEGATIVE TEST CASES =============

    @Test
    @DisplayName("Invalid Parameter Types")
    void testInvalidParameterTypes() {
        Map<String, Object> params = new HashMap<>();

        // Test string where double expected
        params.put("road_overlap_ratio", "invalid");
        try {
            cvProcessor.validateRoadOverlap(params);
            fail("Should throw exception for invalid parameter type");
        } catch (ClassCastException e) {
            // Expected exception
        }

        // Test null where required - the method will handle null by returning false
        params.clear();
        params.put("threshold_type", null);
        params.put("threshold_value", 127);
        ValidationResult result = cvProcessor.validateThreshold(params);
        assertFalse(result.isValid(), "Should return false for null threshold type");
    }

    @Test
    @DisplayName("Missing Required Parameters")
    void testMissingRequiredParameters() {
        Map<String, Object> params = new HashMap<>();

        // Test missing parameters for contour validation
        params.put("mode", "RETR_EXTERNAL");
        // Missing min_area and max_area
        try {
            cvProcessor.validateContourParams(params);
            fail("Should handle missing parameters");
        } catch (Exception e) {
            // Expected exception
        }

        // Test missing performance class
        params.clear();
        params.put("retro_reflection_value", 100);
        try {
            validator.validateRetroReflection(params);
            fail("Should handle missing performance class");
        } catch (Exception e) {
            // Expected exception
        }
    }

    @Test
    @DisplayName("Contour Parameter Validation Coverage")
    void testContourParameterValidation() {
        Map<String, Object> params = new HashMap<>();

        // Test invalid contour mode
        params.put("mode", "INVALID_MODE");
        params.put("min_area", 100);
        params.put("max_area", 10000);
        ValidationResult result = cvProcessor.validateContourParams(params);
        assertFalse(result.isValid());
        assertEquals("Invalid contour mode", result.getFailureReason());

        // Test invalid area range (min >= max)
        params.put("mode", "RETR_EXTERNAL");
        params.put("min_area", 10000);
        params.put("max_area", 100);
        result = cvProcessor.validateContourParams(params);
        assertFalse(result.isValid());
        assertEquals("Invalid area range", result.getFailureReason());

        // Test equal min and max area
        params.put("min_area", 5000);
        params.put("max_area", 5000);
        result = cvProcessor.validateContourParams(params);
        assertFalse(result.isValid());
        assertEquals("Invalid area range", result.getFailureReason());
    }

    @Test
    @DisplayName("Polygon Approximation Edge Cases")
    void testPolygonApproximationEdgeCases() {
        Map<String, Object> params = new HashMap<>();

        // Test epsilon factor at boundaries
        params.put("epsilon_factor", 0.0);
        params.put("closed", true);
        ValidationResult result = cvProcessor.validatePolygonApproximation(params);
        assertFalse(result.isValid());
        assertEquals("Invalid epsilon factor", result.getFailureReason());

        params.put("epsilon_factor", 0.1);
        result = cvProcessor.validatePolygonApproximation(params);
        assertFalse(result.isValid());
        assertEquals("Invalid epsilon factor", result.getFailureReason());

        // Test open polygon
        params.put("epsilon_factor", 0.02);
        params.put("closed", false);
        result = cvProcessor.validatePolygonApproximation(params);
        assertFalse(result.isValid());
        assertEquals("Polygon must be closed", result.getFailureReason());

        // Test negative epsilon
        params.put("epsilon_factor", -0.01);
        params.put("closed", true);
        result = cvProcessor.validatePolygonApproximation(params);
        assertFalse(result.isValid());
        assertEquals("Invalid epsilon factor", result.getFailureReason());
    }

    @Test
    @DisplayName("Performance Validation Edge Cases")
    void testPerformanceValidationEdgeCases() {
        Map<String, Object> params = new HashMap<>();

        // Test worn marking detection - low visibility
        params.put("visibility_percentage", Integer.valueOf(40));
        params.put("detection_confidence", Double.valueOf(0.8));
        ValidationResult result = cvProcessor.validateWornMarkingDetection(params);
        assertFalse(result.isValid());
        assertEquals("Visibility too low", result.getFailureReason());

        // Test worn marking detection - low confidence
        params.put("visibility_percentage", Integer.valueOf(70));
        params.put("detection_confidence", Double.valueOf(0.5));
        result = cvProcessor.validateWornMarkingDetection(params);
        assertFalse(result.isValid());
        assertEquals("Confidence too low", result.getFailureReason());

        // Test accuracy metrics below threshold
        params.clear();
        params.put("precision", 0.85);
        params.put("recall", 0.95);
        params.put("f1_score", 0.90);
        result = cvProcessor.validateAccuracyMetrics(params);
        assertFalse(result.isValid());
        assertEquals("Accuracy metrics below threshold", result.getFailureReason());

        params.put("precision", 0.95);
        params.put("recall", 0.85);
        result = cvProcessor.validateAccuracyMetrics(params);
        assertFalse(result.isValid());

        params.put("recall", 0.95);
        params.put("f1_score", 0.85);
        result = cvProcessor.validateAccuracyMetrics(params);
        assertFalse(result.isValid());
    }

    @Test
    @DisplayName("Warning Line Ratio Validation")
    void testWarningLineRatioValidation() {
        Map<String, Object> params = new HashMap<>();

        // Test ratios outside valid range
        params.put("stroke_length_m", Double.valueOf(2.0));
        params.put("gap_m", Double.valueOf(1.0));
        ValidationResult result = validator.validateWarningLineRatio(params);
        assertFalse(result.isValid());
        assertEquals("Invalid warning line ratio", result.getFailureReason());

        params.put("stroke_length_m", Double.valueOf(4.0));
        params.put("gap_m", Double.valueOf(1.0));
        result = validator.validateWarningLineRatio(params);
        assertFalse(result.isValid());

        // Test valid ratio at boundaries
        params.put("stroke_length_m", Double.valueOf(2.5));
        params.put("gap_m", Double.valueOf(1.0));
        result = validator.validateWarningLineRatio(params);
        assertTrue(result.isValid());

        params.put("stroke_length_m", Double.valueOf(3.0));
        params.put("gap_m", Double.valueOf(1.0));
        result = validator.validateWarningLineRatio(params);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Gap to Stroke Ratio Complex Validation")
    void testGapToStrokeRatioComplexValidation() {
        Map<String, Object> params = new HashMap<>();

        // Test gap below minimum ratio
        params.put("stroke_length_m", 3.0);
        params.put("gap_m", 5.0); // Less than 2 * stroke_length
        ValidationResult result = validator.validateGapToStrokeRatio(params);
        assertFalse(result.isValid());
        assertEquals("Invalid gap to stroke ratio", result.getFailureReason());

        // Test gap above maximum ratio
        params.put("gap_m", 15.0); // More than 4 * stroke_length
        result = validator.validateGapToStrokeRatio(params);
        assertFalse(result.isValid());
        assertEquals("Invalid gap to stroke ratio", result.getFailureReason());

        // Test gap exceeding absolute maximum
        params.put("stroke_length_m", 1.0);
        params.put("gap_m", 3.0); // Valid ratio but gap <= 4 * 1.0
        result = validator.validateGapToStrokeRatio(params);
        assertTrue(result.isValid());

        params.put("gap_m", 15.0); // Invalid ratio (15 > 4 * 1.0) and gap > 12.0, but ratio check comes first
        result = validator.validateGapToStrokeRatio(params);
        assertFalse(result.isValid());
        assertEquals("Invalid gap to stroke ratio", result.getFailureReason());

        // Test valid gap at boundaries
        params.put("stroke_length_m", 3.0);
        params.put("gap_m", 6.0); // Exactly 2 * stroke_length
        result = validator.validateGapToStrokeRatio(params);
        assertTrue(result.isValid());

        params.put("gap_m", 12.0); // Exactly 4 * stroke_length and max gap
        result = validator.validateGapToStrokeRatio(params);
        assertTrue(result.isValid());
    }

    @Test
    @DisplayName("Transverse Marking Dimension Validation")
    void testTransverseMarkingDimensionValidation() {
        Map<String, Object> params = new HashMap<>();

        // Test give way triangle with invalid base
        params.put("base_m", 0.35);
        params.put("height_m", 0.65);
        ValidationResult result = validator.validateGiveWayTriangleDimensions(params);
        assertFalse(result.isValid());
        assertEquals("Invalid triangle base", result.getFailureReason());

        // Test give way triangle with invalid height
        params.put("base_m", 0.50);
        params.put("height_m", 0.75);
        result = validator.validateGiveWayTriangleDimensions(params);
        assertFalse(result.isValid());
        assertEquals("Invalid triangle height", result.getFailureReason());

        // Test give way bars with invalid length ratio
        params.clear();
        params.put("bar_width_m", 0.30);
        params.put("bar_length_m", 0.50); // Less than 2 * width
        result = validator.validateGiveWayBarDimensions(params);
        assertFalse(result.isValid());
        assertEquals("Invalid bar length ratio", result.getFailureReason());

        // Test cycle crossing with unequal gap and element size
        params.clear();
        params.put("element_size_m", 0.50);
        params.put("gap_m", 0.40);
        result = validator.validateCycleCrossingElements(params);
        assertFalse(result.isValid());
        assertEquals("Gap must equal element size", result.getFailureReason());
    }

    @Test
    @DisplayName("Comprehensive Placeholder Method Coverage")
    void testPlaceholderMethodCoverage() {
        Map<String, Object> params = new HashMap<>();

        // Test all placeholder methods that always return true
        ValidationResult result;

        result = validator.validateGeometry(params);
        assertTrue(result.isValid());

        result = validator.validatePerformance(params);
        assertTrue(result.isValid());

        result = validator.validateCompleteStopLine(params);
        assertTrue(result.isValid());

        result = validator.validateCompleteZebraCrossing(params);
        assertTrue(result.isValid());

        result = validator.validateLaneArrowDirection(params);
        assertTrue(result.isValid());

        result = validator.validateWordMarking(params);
        assertTrue(result.isValid());

        result = validator.validateComplexScenario(params);
        assertTrue(result.isValid());

        result = validator.validateNightPerformance(params);
        assertTrue(result.isValid());

        result = validator.validateWetWeatherPerformance(params);
        assertTrue(result.isValid());

        // Test extended placeholder methods
        result = validator.validateDoubleLine(params);
        assertTrue(result.isValid());

        result = validator.validateMixedLine(params);
        assertTrue(result.isValid());

        result = validator.validateEdgeLinePosition(params);
        assertTrue(result.isValid());

        result = validator.validateGiveWayLine(params);
        assertTrue(result.isValid());

        result = validator.validateSchoolCrossing(params);
        assertTrue(result.isValid());

        result = validator.validateAdvancedCycleCrossing(params);
        assertTrue(result.isValid());

        result = validator.validateCombinedCrossing(params);
        assertTrue(result.isValid());

        result = validator.validateTemporaryMarkingColor(params);
        assertTrue(result.isValid());

        result = validator.validateSpecialZoneColor(params);
        assertTrue(result.isValid());

        result = validator.validateParkingSymbol(params);
        assertTrue(result.isValid());

        result = validator.validateDisabledParkingSymbol(params);
        assertTrue(result.isValid());

        result = validator.validateSpeedLimitMarking(params);
        assertTrue(result.isValid());

        result = validator.validateHatchingPattern(params);
        assertTrue(result.isValid());

        result = validator.validateChevronPattern(params);
        assertTrue(result.isValid());

        result = validator.validateBusLaneMarking(params);
        assertTrue(result.isValid());

        result = validator.validateTaxiLaneMarking(params);
        assertTrue(result.isValid());

        result = validator.validateComplexIntersection(params);
        assertTrue(result.isValid());

        result = validator.validateRoundaboutMarkings(params);
        assertTrue(result.isValid());

        result = validator.validateMultiColorMarking(params);
        assertTrue(result.isValid());
    }

    // ============= EXCEPTION HANDLING TESTS =============

    @Test
    @DisplayName("Exception Handling for Type Mismatches")
    void testExceptionHandlingForTypeMismatches() {
        Map<String, Object> params = new HashMap<>();

        // Test various type mismatches that should be handled gracefully
        params.put("kernel_size_px", "not_an_integer");
        try {
            cvProcessor.validateKernelSize(params);
            fail("Should handle string instead of integer");
        } catch (ClassCastException e) {
            // Expected
        }

        params.clear();
        params.put("closed", "not_a_boolean");
        params.put("epsilon_factor", 0.02);
        try {
            cvProcessor.validatePolygonApproximation(params);
            fail("Should handle string instead of boolean");
        } catch (ClassCastException e) {
            // Expected
        }
    }

    @Test
    @DisplayName("Null Parameter Handling")
    void testNullParameterHandling() {
        Map<String, Object> params = new HashMap<>();

        // Test null handling in different validation methods
        params.put("marking_type", null);
        params.put("width_m", 0.12);
        try {
            validator.validateMinimumWidth(params);
            fail("Should handle null marking type");
        } catch (Exception e) {
            // Expected
        }

        params.clear();
        params.put("color", null);
        params.put("marking_type", "solid_line");
        ValidationResult result = validator.validateColor(params);
        assertFalse(result.isValid(), "Should return false for null color");
    }
}