package com.example.lanes.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for EU Longitudinal Road Markings validation
 * Based on UNECE Vienna Convention & CEN EN Standards
 */
public class LongitudinalMarkingsTest {

    private EUMarkingValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EUMarkingValidator();
    }

    @DisplayName("LM_001: Normal Broken Line - Minimum Width Validation")
    @ParameterizedTest(name = "Width {0}m should be {1}")
    @CsvSource({
        "0.09, false, Below minimum width",
        "0.10, true, Exact minimum width",
        "0.12, true, Valid width",
        "0.15, true, Above minimum width"
    })
    void testNormalBrokenLineMinimumWidth(double width, boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "normal_broken_line");
        params.put("width_m", width);
        
        ValidationResult result = validator.validateMinimumWidth(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("LM_001: Normal Broken Line - Minimum Stroke Length")
    @ParameterizedTest(name = "Stroke length {0}m should be {1}")
    @CsvSource({
        "0.8, false, Below minimum stroke length",
        "1.0, true, Exact minimum stroke length",
        "2.5, true, Valid stroke length",
        "5.0, true, Above minimum stroke length"
    })
    void testNormalBrokenLineStrokeLength(double strokeLength, boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "normal_broken_line");
        params.put("stroke_length_m", strokeLength);
        
        ValidationResult result = validator.validateStrokeLength(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("LM_001: Normal Broken Line - Gap to Stroke Ratio")
    @ParameterizedTest(name = "Stroke {0}m with gap {1}m should be {2}")
    @CsvSource({
        "1.0, 1.5, false, Gap too small relative to stroke",
        "1.0, 2.0, true, Valid minimum ratio",
        "1.0, 4.0, true, Valid maximum ratio",
        "1.0, 5.0, false, Gap too large relative to stroke",
        "4.0, 15.0, false, Gap exceeds 12m maximum",
        "2.0, 4.0, true, Valid 2:1 ratio",
        "3.0, 12.0, true, Valid at maximum gap",
        "3.0, 12.1, false, Exceeds maximum gap"
    })
    void testNormalBrokenLineGapToStrokeRatio(double strokeLength, double gap, 
                                                boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "normal_broken_line");
        params.put("stroke_length_m", strokeLength);
        params.put("gap_m", gap);
        
        ValidationResult result = validator.validateGapToStrokeRatio(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("LM_002: Broken Warning Line - Stroke Longer Than Gap")
    @ParameterizedTest(name = "Stroke {0}m with gap {1}m should be {2}")
    @CsvSource({
        "3.0, 2.0, false, Stroke not sufficiently longer than gap",
        "4.0, 1.5, true, Valid warning line ratio",
        "8.0, 2.5, false, Stroke too long relative to gap",
        "6.0, 2.0, true, Valid 3:1 ratio",
        "5.0, 2.0, true, Valid 2.5:1 ratio"
    })
    void testBrokenWarningLineStrokeLongerThanGap(double strokeLength, double gap, 
                                                    boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "broken_warning_line");
        params.put("stroke_length_m", strokeLength);
        params.put("gap_m", gap);
        
        ValidationResult result = validator.validateWarningLineRatio(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("LM_003: Solid Line - Minimum Width")
    @ParameterizedTest(name = "Width {0}m should be {1}")
    @CsvSource({
        "0.08, false, Below minimum width",
        "0.10, true, Exact minimum width",
        "0.15, true, Valid width",
        "0.20, true, Above minimum width"
    })
    void testSolidLineMinimumWidth(double width, boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "solid_line");
        params.put("width_m", width);
        
        ValidationResult result = validator.validateMinimumWidth(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("LM_003: Solid Line - Minimum Continuous Length")
    @ParameterizedTest(name = "Length {0}m should be {1}")
    @CsvSource({
        "15.0, false, Below minimum continuous length",
        "20.0, true, Exact minimum continuous length",
        "50.0, true, Valid continuous length",
        "100.0, true, Long continuous section"
    })
    void testSolidLineMinimumContinuousLength(double length, boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "solid_line");
        params.put("continuous_length_m", length);
        
        ValidationResult result = validator.validateContinuousLength(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("LM_004: Motorway Edge Line - Minimum Width")
    @ParameterizedTest(name = "Width {0}m should be {1}")
    @CsvSource({
        "0.12, false, Below motorway minimum",
        "0.15, true, Exact motorway minimum",
        "0.20, true, Valid motorway width",
        "0.25, true, Wide motorway edge line"
    })
    void testMotorwayEdgeLineMinimumWidth(double width, boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "edge_line_motorway");
        params.put("width_m", width);
        
        ValidationResult result = validator.validateMotorwayEdgeWidth(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @Test
    @DisplayName("Complex Normal Broken Line Validation")
    void testCompleteNormalBrokenLineValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "normal_broken_line");
        params.put("width_m", 0.12);
        params.put("stroke_length_m", 1.5);
        params.put("gap_m", 3.0);
        params.put("color", "white");
        
        ValidationResult result = validator.validateCompleteLongitudinalMarking(params);
        assertTrue(result.isValid(), "Complete normal broken line should be valid");
        assertEquals("normal_broken_line", result.getMarkingType());
    }

    @Test
    @DisplayName("Invalid Complex Broken Line - Multiple Failures")
    void testInvalidComplexBrokenLine() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "normal_broken_line");
        params.put("width_m", 0.08); // Below minimum
        params.put("stroke_length_m", 0.5); // Below minimum
        params.put("gap_m", 15.0); // Exceeds maximum
        
        ValidationResult result = validator.validateCompleteLongitudinalMarking(params);
        assertFalse(result.isValid(), "Should fail with multiple violations");
        assertTrue(result.getErrors().size() >= 3, "Should have at least 3 errors");
    }

    @Test
    @DisplayName("Double Line Validation")
    void testDoubleLineValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "double_solid_line");
        params.put("width_m", 0.10);
        params.put("line_separation_m", 0.10);
        params.put("continuous_length_m", 25.0);
        
        ValidationResult result = validator.validateDoubleLine(params);
        assertTrue(result.isValid(), "Valid double solid line");
    }

    @Test
    @DisplayName("Mixed Line Validation (Solid + Broken)")
    void testMixedLineValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "mixed_line");
        params.put("solid_width_m", 0.10);
        params.put("broken_width_m", 0.10);
        params.put("stroke_length_m", 2.0);
        params.put("gap_m", 4.0);
        params.put("line_separation_m", 0.10);
        
        ValidationResult result = validator.validateMixedLine(params);
        assertTrue(result.isValid(), "Valid mixed line configuration");
    }

    @Test
    @DisplayName("Edge Line Position Validation")
    void testEdgeLinePositionValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "edge_line");
        params.put("width_m", 0.15);
        params.put("distance_from_edge_m", 0.0);
        params.put("road_type", "motorway");
        
        ValidationResult result = validator.validateEdgeLinePosition(params);
        assertTrue(result.isValid(), "Valid edge line position for motorway");
    }

}