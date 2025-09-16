package com.example.lanes.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test suite for EU Other Markings and Color validation
 * Based on UNECE Vienna Convention & CEN EN Standards
 */
public class OtherMarkingsAndColorTest {

    private EUMarkingValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EUMarkingValidator();
    }

    // ============= LANE ARROWS TESTS =============

    @DisplayName("OM_001: Lane Selection Arrow - Minimum Length")
    @ParameterizedTest(name = "Arrow length {0}m should be {1}")
    @CsvSource({
        "1.8, false, Below minimum length",
        "2.0, true, Exact minimum length",
        "3.5, true, Valid arrow length",
        "5.0, true, Long arrow for high speed"
    })
    void testLaneArrowMinimumLength(double arrowLength, boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "lane_arrow");
        params.put("arrow_length_m", arrowLength);
        
        ValidationResult result = validator.validateLaneArrowLength(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @Test
    @DisplayName("Lane Arrow Direction Types")
    void testLaneArrowDirectionTypes() {
        String[] validDirections = {"straight", "left", "right", "straight_left", 
                                    "straight_right", "left_right", "u_turn"};
        
        for (String direction : validDirections) {
            Map<String, Object> params = new HashMap<>();
            params.put("marking_type", "lane_arrow");
            params.put("arrow_direction", direction);
            params.put("arrow_length_m", 3.0);
            
            ValidationResult result = validator.validateLaneArrowDirection(params);
            assertTrue(result.isValid(), "Direction " + direction + " should be valid");
        }
    }

    // ============= WORD MARKINGS TESTS =============

    @DisplayName("OM_002: Word Markings - Character Height Low Speed")
    @ParameterizedTest(name = "Character height {0}m at {1}kph should be {2}")
    @CsvSource({
        "1.4, 50, false, Below minimum for low speed",
        "1.6, 60, true, Exact minimum for low speed",
        "2.0, 40, true, Valid height for low speed",
        "3.0, 30, true, Large text for low speed"
    })
    void testWordMarkingsHeightLowSpeed(double charHeight, double speed, 
                                         boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "word_marking");
        params.put("char_height_m", charHeight);
        params.put("approach_speed_kph", speed);
        
        ValidationResult result = validator.validateWordMarkingHeight(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @DisplayName("OM_002: Word Markings - Character Height High Speed")
    @ParameterizedTest(name = "Character height {0}m at {1}kph should be {2}")
    @CsvSource({
        "2.0, 80, false, Below minimum for high speed",
        "2.5, 70, true, Exact minimum for high speed",
        "4.0, 100, true, Valid height for high speed",
        "5.0, 120, true, Large text for very high speed"
    })
    void testWordMarkingsHeightHighSpeed(double charHeight, double speed, 
                                          boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "word_marking");
        params.put("char_height_m", charHeight);
        params.put("approach_speed_kph", speed);
        
        ValidationResult result = validator.validateWordMarkingHeight(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @Test
    @DisplayName("Common Word Markings Validation")
    void testCommonWordMarkings() {
        String[] commonWords = {"STOP", "SLOW", "SCHOOL", "BUS", "TAXI", "ONLY", 
                                "LANE", "EXIT", "AHEAD", "XING"};
        
        for (String word : commonWords) {
            Map<String, Object> params = new HashMap<>();
            params.put("marking_type", "word_marking");
            params.put("text", word);
            params.put("char_height_m", 2.0);
            params.put("approach_speed_kph", 50);
            
            ValidationResult result = validator.validateWordMarking(params);
            assertTrue(result.isValid(), "Word '" + word + "' should be valid");
        }
    }

    // ============= COLOR VALIDATION TESTS =============

    @DisplayName("CV_001: Basic Color Validation")
    @ParameterizedTest(name = "Color {0} for {1} should be {2}")
    @CsvSource({
        "white, lane_line, true, White valid for lane lines",
        "yellow, parking_restriction, true, Yellow valid for parking",
        "blue, parking_zone, true, Blue valid for parking zones",
        "blue, lane_line, false, Blue not permitted for lane lines",
        "red, stop_line, false, Red not permitted color",
        "green, bike_lane, false, Green not standard EU color"
    })
    void testBasicColorValidation(String color, String markingType, 
                                   boolean expected, String reason) {
        Map<String, Object> params = new HashMap<>();
        params.put("color", color);
        params.put("marking_type", markingType);
        
        ValidationResult result = validator.validateColor(params);
        assertEquals(expected, result.isValid(), reason);
    }

    @Test
    @DisplayName("Temporary Marking Colors")
    void testTemporaryMarkingColors() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "temporary_lane_line");
        params.put("color", "yellow");
        params.put("is_temporary", true);
        
        ValidationResult result = validator.validateTemporaryMarkingColor(params);
        assertTrue(result.isValid(), "Yellow should be valid for temporary markings");
    }

    @Test
    @DisplayName("Special Zone Color Validation")
    void testSpecialZoneColors() {
        // Blue for disabled parking
        Map<String, Object> disabledParams = new HashMap<>();
        disabledParams.put("marking_type", "disabled_parking");
        disabledParams.put("color", "blue");
        
        ValidationResult disabledResult = validator.validateSpecialZoneColor(disabledParams);
        assertTrue(disabledResult.isValid(), "Blue valid for disabled parking");
        
        // Yellow for loading zone
        Map<String, Object> loadingParams = new HashMap<>();
        loadingParams.put("marking_type", "loading_zone");
        loadingParams.put("color", "yellow");
        
        ValidationResult loadingResult = validator.validateSpecialZoneColor(loadingParams);
        assertTrue(loadingResult.isValid(), "Yellow valid for loading zones");
    }

    // ============= SYMBOL MARKINGS TESTS =============

    @Test
    @DisplayName("Parking Symbol Validation")
    void testParkingSymbolValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "parking_symbol");
        params.put("symbol_type", "P");
        params.put("symbol_height_m", 2.0);
        params.put("color", "white");
        
        ValidationResult result = validator.validateParkingSymbol(params);
        assertTrue(result.isValid(), "Parking symbol should be valid");
    }

    @Test
    @DisplayName("Disabled Parking Symbol Validation")
    void testDisabledParkingSymbolValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "disabled_parking_symbol");
        params.put("symbol_type", "wheelchair");
        params.put("symbol_height_m", 1.5);
        params.put("color", "blue");
        params.put("background_color", "white");
        
        ValidationResult result = validator.validateDisabledParkingSymbol(params);
        assertTrue(result.isValid(), "Disabled parking symbol should be valid");
    }

    @Test
    @DisplayName("Speed Limit Marking Validation")
    void testSpeedLimitMarkingValidation() {
        int[] commonSpeedLimits = {30, 50, 70, 90, 110, 130};
        
        for (int speed : commonSpeedLimits) {
            Map<String, Object> params = new HashMap<>();
            params.put("marking_type", "speed_limit");
            params.put("speed_value", speed);
            params.put("char_height_m", 4.0);
            params.put("approach_speed_kph", speed + 20);
            
            ValidationResult result = validator.validateSpeedLimitMarking(params);
            assertTrue(result.isValid(), "Speed limit " + speed + " should be valid");
        }
    }

    // ============= HATCHING AND CHEVRON TESTS =============

    @Test
    @DisplayName("Hatching Pattern Validation")
    void testHatchingPatternValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "hatching");
        params.put("line_width_m", 0.30);
        params.put("line_spacing_m", 1.0);
        params.put("angle_degrees", 45);
        params.put("color", "white");
        
        ValidationResult result = validator.validateHatchingPattern(params);
        assertTrue(result.isValid(), "Hatching pattern should be valid");
    }

    @Test
    @DisplayName("Chevron Pattern Validation")
    void testChevronPatternValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "chevron");
        params.put("chevron_width_m", 0.50);
        params.put("chevron_spacing_m", 2.0);
        params.put("chevron_angle_degrees", 45);
        params.put("color", "white");
        
        ValidationResult result = validator.validateChevronPattern(params);
        assertTrue(result.isValid(), "Chevron pattern should be valid");
    }

    // ============= BUS AND TAXI LANE TESTS =============

    @Test
    @DisplayName("Bus Lane Marking Validation")
    void testBusLaneMarkingValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "bus_lane");
        params.put("text", "BUS");
        params.put("char_height_m", 3.0);
        params.put("lane_width_m", 3.5);
        params.put("color", "white");
        params.put("has_solid_boundary", true);
        
        ValidationResult result = validator.validateBusLaneMarking(params);
        assertTrue(result.isValid(), "Bus lane marking should be valid");
    }

    @Test
    @DisplayName("Taxi Lane Marking Validation")
    void testTaxiLaneMarkingValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "taxi_lane");
        params.put("text", "TAXI");
        params.put("char_height_m", 2.5);
        params.put("color", "yellow");
        params.put("is_reserved", true);
        
        ValidationResult result = validator.validateTaxiLaneMarking(params);
        assertTrue(result.isValid(), "Taxi lane marking should be valid");
    }

    // ============= COMPLEX MARKING COMBINATIONS =============

    @Test
    @DisplayName("Complex Intersection Markings")
    void testComplexIntersectionMarkings() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "complex_intersection");
        params.put("has_stop_line", true);
        params.put("has_pedestrian_crossing", true);
        params.put("has_lane_arrows", true);
        params.put("has_bike_box", true);
        params.put("stop_line_width_m", 0.30);
        params.put("crossing_width_m", 4.0);
        
        ValidationResult result = validator.validateComplexIntersection(params);
        assertTrue(result.isValid(), "Complex intersection should be valid");
    }

    @Test
    @DisplayName("Roundabout Markings")
    void testRoundaboutMarkings() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "roundabout");
        params.put("has_yield_line", true);
        params.put("has_lane_arrows", true);
        params.put("has_spiral_markings", true);
        params.put("outer_diameter_m", 30.0);
        params.put("lane_count", 2);
        
        ValidationResult result = validator.validateRoundaboutMarkings(params);
        assertTrue(result.isValid(), "Roundabout markings should be valid");
    }

    static Stream<Arguments> provideInvalidColorCombinations() {
        return Stream.of(
            Arguments.of("red", "lane_line", "Red not permitted"),
            Arguments.of("blue", "stop_line", "Blue only for parking"),
            Arguments.of("green", "crossing", "Green not standard"),
            Arguments.of("orange", "lane_arrow", "Orange not permitted"),
            Arguments.of("purple", "word_marking", "Purple not permitted")
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidColorCombinations")
    @DisplayName("Invalid Color Combinations")
    void testInvalidColorCombinations(String color, String markingType, String expectedError) {
        Map<String, Object> params = new HashMap<>();
        params.put("color", color);
        params.put("marking_type", markingType);
        
        ValidationResult result = validator.validateColor(params);
        assertFalse(result.isValid(), "Should be invalid: " + expectedError);
        assertTrue(result.getErrors().stream().anyMatch(e -> e.toLowerCase().contains(color)),
                  "Error should mention the invalid color");
    }

    @Test
    @DisplayName("Multi-Color Marking Validation")
    void testMultiColorMarkingValidation() {
        Map<String, Object> params = new HashMap<>();
        params.put("marking_type", "combined_marking");
        params.put("primary_color", "white");
        params.put("secondary_color", "yellow");
        params.put("is_transition_zone", true);
        
        ValidationResult result = validator.validateMultiColorMarking(params);
        assertTrue(result.isValid(), "Multi-color marking in transition zone should be valid");
    }
}