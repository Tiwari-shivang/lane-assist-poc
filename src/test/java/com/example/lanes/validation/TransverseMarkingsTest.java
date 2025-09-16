package com.example.lanes.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for EU Transverse Road Markings validation
 * Based on UNECE Vienna Convention & CEN EN Standards
 */
public class TransverseMarkingsTest {

    private EUMarkingValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EUMarkingValidator();
    }

    @DisplayName("TM_001: Stop Line - Bar Width Range Validation")
    @ParameterizedTest(name = "Bar width {0}m should be {1}")
    @CsvSource({
        "0.20, false, Below minimum width",
        "0.25, true, Exact minimum width",
        "0.32, true, Valid mid-range width",
        "0.40, true, Exact maximum width",
        "0.45, false, Above maximum width"
    })
    void testStopLineBarWidthRange(double barWidth, boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "stop_line");
        params.put("bar_width_m", barWidth);
        
        ValidationResult result = validator.validateStopLineBarWidth(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("TM_002: Give Way Bars - Bar Dimensions")
    @ParameterizedTest(name = "Bar width {0}m, length {1}m should be {2}")
    @CsvSource({
        "0.15, 0.40, false, Width below minimum",
        "0.30, 0.50, false, Length not 2x width",
        "0.30, 0.60, true, Valid 2x ratio",
        "0.40, 0.80, true, Valid dimensions",
        "0.60, 1.20, true, Maximum width with valid ratio",
        "0.70, 1.40, false, Width exceeds maximum"
    })
    void testGiveWayBarDimensions(double barWidth, double barLength, 
                                   boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "give_way_bars");
        params.put("bar_width_m", barWidth);
        params.put("bar_length_m", barLength);
        
        ValidationResult result = validator.validateGiveWayBarDimensions(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("TM_003: Give Way Triangles - Triangle Dimensions")
    @ParameterizedTest(name = "Base {0}m, height {1}m should be {2}")
    @CsvSource({
        "0.35, 0.65, false, Base below minimum",
        "0.50, 0.55, false, Height below minimum",
        "0.45, 0.65, true, Valid triangle dimensions",
        "0.60, 0.70, true, Maximum valid dimensions",
        "0.50, 0.75, false, Height exceeds maximum",
        "0.65, 0.65, false, Base exceeds maximum"
    })
    void testGiveWayTriangleDimensions(double base, double height, 
                                        boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "give_way_triangles");
        params.put("base_m", base);
        params.put("height_m", height);
        
        ValidationResult result = validator.validateGiveWayTriangleDimensions(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("TM_004: Zebra Crossing - Stripe Gap Pattern")
    @ParameterizedTest(name = "Stripe {0}m, gap {1}m should be {2}")
    @CsvSource({
        "0.30, 0.40, false, Combined width too small",
        "0.40, 0.40, true, Valid minimum combined width",
        "0.60, 0.60, true, Valid typical pattern",
        "0.80, 0.80, false, Combined width too large",
        "0.70, 0.70, true, Maximum valid pattern"
    })
    void testZebraCrossingStripeGapPattern(double stripeWidth, double gapWidth, 
                                            boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "zebra_crossing");
        params.put("stripe_width_m", stripeWidth);
        params.put("gap_width_m", gapWidth);
        
        ValidationResult result = validator.validateZebraStripePattern(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("TM_004: Zebra Crossing - Minimum Width Low Speed")
    @ParameterizedTest(name = "Width {0}m at {1}kph should be {2}")
    @CsvSource({
        "2.0, 50, false, Below minimum for low speed",
        "2.5, 60, true, Exact minimum for low speed",
        "3.0, 45, true, Valid width for low speed",
        "4.0, 30, true, Wide crossing for low speed"
    })
    void testZebraCrossingMinimumWidthLowSpeed(double width, double speed, 
                                                boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "zebra_crossing");
        params.put("crossing_width_m", width);
        params.put("v85_kph", speed);
        
        ValidationResult result = validator.validateZebraCrossingWidth(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("TM_004: Zebra Crossing - Minimum Width High Speed")
    @ParameterizedTest(name = "Width {0}m at {1}kph should be {2}")
    @CsvSource({
        "3.5, 70, false, Below minimum for high speed",
        "4.0, 65, true, Exact minimum for high speed",
        "5.0, 80, true, Valid width for high speed",
        "6.0, 90, true, Wide crossing for high speed"
    })
    void testZebraCrossingMinimumWidthHighSpeed(double width, double speed, 
                                                 boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "zebra_crossing");
        params.put("crossing_width_m", width);
        params.put("v85_kph", speed);
        
        ValidationResult result = validator.validateZebraCrossingWidth(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("TM_005: Cycle Crossing - Element Dimensions")
    @ParameterizedTest(name = "Element {0}m, gap {1}m should be {2}")
    @CsvSource({
        "0.35, 0.35, false, Below minimum element size",
        "0.45, 0.40, false, Gap must equal element size",
        "0.50, 0.50, true, Valid equal dimensions",
        "0.60, 0.60, true, Maximum valid dimensions",
        "0.65, 0.65, false, Exceeds maximum element size"
    })
    void testCycleCrossingElementDimensions(double elementSize, double gap, 
                                             boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "cycle_crossing");
        params.put("element_size_m", elementSize);
        params.put("gap_m", gap);
        
        ValidationResult result = validator.validateCycleCrossingElements(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("TM_005: Cycle Crossing - Width by Direction")
    @ParameterizedTest(name = "Width {0}m for {1} should be {2}")
    @CsvSource({
        "1.5, one_way, false, Below minimum for one-way",
        "1.8, one_way, true, Exact minimum for one-way",
        "2.5, two_way, false, Below minimum for two-way",
        "3.0, two_way, true, Exact minimum for two-way",
        "4.0, two_way, true, Valid width for two-way"
    })
    void testCycleCrossingWidth(double width, String direction, 
                                 boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "cycle_crossing");
        params.put("width_m", width);
        params.put("direction", direction);
        
        ValidationResult result = validator.validateCycleCrossingWidth(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @Test
    @DisplayName("Complete Stop Line Validation")
    void testCompleteStopLineValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "stop_line");
        params.put("bar_width_m", 0.30);
        params.put("color", "white");
        params.put("position", "perpendicular");
        params.put("distance_from_conflict_m", 1.0);
        
        ValidationResult result = validator.validateCompleteStopLine(params);
        assertTrue(result.isValid(), "Complete stop line should be valid");
    }

    @Test
    @DisplayName("Complete Zebra Crossing Validation")
    void testCompleteZebraCrossingValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "zebra_crossing");
        params.put("stripe_width_m", 0.50);
        params.put("gap_width_m", 0.50);
        params.put("crossing_width_m", 4.5);
        params.put("v85_kph", 70);
        params.put("stripe_count", 8);
        
        ValidationResult result = validator.validateCompleteZebraCrossing(params);
        assertTrue(result.isValid(), "Complete zebra crossing should be valid");
    }

    @Test
    @DisplayName("Give Way Line with Multiple Bars")
    void testGiveWayLineWithMultipleBars() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "give_way_bars");
        params.put("bar_width_m", 0.40);
        params.put("bar_length_m", 0.80);
        params.put("bar_count", 2);
        params.put("bar_spacing_m", 0.40);
        
        ValidationResult result = validator.validateGiveWayLine(params);
        assertTrue(result.isValid(), "Give way line with multiple bars should be valid");
    }

    @Test
    @DisplayName("School Crossing Validation")
    void testSchoolCrossingValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "school_crossing");
        params.put("stripe_width_m", 0.50);
        params.put("gap_width_m", 0.50);
        params.put("crossing_width_m", 3.0);
        params.put("color", "yellow");
        params.put("v85_kph", 30);
        
        ValidationResult result = validator.validateSchoolCrossing(params);
        assertTrue(result.isValid(), "School crossing with yellow markings should be valid");
    }

    @Test
    @DisplayName("Advanced Cycle Crossing with Symbols")
    void testAdvancedCycleCrossingWithSymbols() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "cycle_crossing");
        params.put("element_size_m", 0.50);
        params.put("gap_m", 0.50);
        params.put("width_m", 3.5);
        params.put("direction", "two_way");
        params.put("has_cycle_symbols", true);
        params.put("symbol_height_m", 1.8);
        
        ValidationResult result = validator.validateAdvancedCycleCrossing(params);
        assertTrue(result.isValid(), "Cycle crossing with symbols should be valid");
    }

    @Test
    @DisplayName("Combined Pedestrian and Cycle Crossing")
    void testCombinedPedestrianCycleCrossing() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "combined_crossing");
        params.put("pedestrian_width_m", 3.0);
        params.put("cycle_width_m", 2.0);
        params.put("separation_gap_m", 0.5);
        params.put("total_width_m", 5.5);
        
        ValidationResult result = validator.validateCombinedCrossing(params);
        assertTrue(result.isValid(), "Combined crossing should be valid");
    }

    static Stream<Arguments> provideInvalidTransverseMarkings() {
        return Stream.of(
            Arguments.of("stop_line", Map.of("bar_width_m", 0.50), "Bar width exceeds maximum"),
            Arguments.of("give_way_bars", Map.of("bar_width_m", 0.10), "Bar width below minimum"),
            Arguments.of("zebra_crossing", Map.of("crossing_width_m", 1.5, "v85_kph", 80), "Insufficient width for speed"),
            Arguments.of("cycle_crossing", Map.of("element_size_m", 0.70), "Element size exceeds maximum")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidTransverseMarkings")
    @DisplayName("Invalid Transverse Markings")
    void testInvalidTransverseMarkings(String markingType, Map<String, Object> additionalParams, String expectedError) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", markingType);
        params.putAll(additionalParams);
        
        ValidationResult result = validator.validateTransverseMarking(params);
        assertFalse(result.isValid(), "Should be invalid: " + expectedError);
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains(expectedError.split(" ")[0])),
                  "Should contain error about: " + expectedError);
    }
}