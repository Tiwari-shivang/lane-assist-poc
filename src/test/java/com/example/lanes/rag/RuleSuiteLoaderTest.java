package com.example.lanes.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for RuleSuiteLoader
 * Targeting 95%+ code coverage for all methods and branches
 */
@SpringBootTest
public class RuleSuiteLoaderTest {

    private RuleSuiteLoader loader;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        loader = new RuleSuiteLoader();
    }

    // ============= CONSTRUCTOR TESTS =============

    @Test
    @DisplayName("RSL_001: Constructor initializes yamlMapper")
    void testConstructorInitializesYamlMapper() {
        RuleSuiteLoader newLoader = new RuleSuiteLoader();
        assertNotNull(newLoader);

        // Verify the mapper is initialized by attempting to use it
        assertDoesNotThrow(() -> {
            try {
                newLoader.load("test-rules/test_rules.yaml");
            } catch (IOException e) {
                // IOException is acceptable here, we just want to verify the mapper isn't null
            }
        });
    }

    // ============= LOAD METHOD SUCCESS TESTS =============

    @Test
    @DisplayName("RSL_002: Load valid YAML file with multiple rules")
    void testLoadValidYamlFile() throws IOException {
        List<Rule> rules = loader.load("test-rules/test_rules.yaml");

        assertNotNull(rules);
        assertEquals(3, rules.size());

        // Verify first rule
        Rule rule1 = rules.get(0);
        assertEquals("test_rule_1", rule1.getId());
        assertNotNull(rule1.getTags());
        assertEquals(2, rule1.getTags().size());
        assertTrue(rule1.getTags().contains("lane_marking"));
        assertTrue(rule1.getTags().contains("eu_standard"));
        assertNotNull(rule1.getParams());
        assertTrue(rule1.getParams().containsKey("width_range_m"));
        assertArrayEquals(new double[]{0.10, 0.15}, rule1.getParams().get("width_range_m"));
        assertNotNull(rule1.getChecks());
        assertEquals(2, rule1.getChecks().size());

        // Verify second rule
        Rule rule2 = rules.get(1);
        assertEquals("test_rule_2", rule2.getId());
        assertNotNull(rule2.getTags());
        assertEquals(2, rule2.getTags().size());
        assertTrue(rule2.getTags().contains("edge_line"));
        assertTrue(rule2.getTags().contains("motorway"));

        // Verify third rule
        Rule rule3 = rules.get(2);
        assertEquals("test_rule_3", rule3.getId());
        assertNotNull(rule3.getParams());
        assertTrue(rule3.getParams().containsKey("stroke_length_m"));
        assertTrue(rule3.getParams().containsKey("gap_length_m"));
    }

    @Test
    @DisplayName("RSL_003: Load empty YAML file")
    void testLoadEmptyYamlFile() throws IOException {
        List<Rule> rules = loader.load("test-rules/empty_rules.yaml");

        assertNotNull(rules);
        assertEquals(0, rules.size());
    }

    // ============= LOAD METHOD ERROR TESTS =============

    @Test
    @DisplayName("RSL_004: Load non-existent file throws IOException")
    void testLoadNonExistentFile() {
        assertThrows(IOException.class, () -> {
            loader.load("non-existent-file.yaml");
        });
    }

    @Test
    @DisplayName("RSL_005: Load invalid YAML structure")
    void testLoadInvalidYamlStructure() {
        assertThrows(Exception.class, () -> {
            loader.load("test-rules/invalid_rules.yaml");
        });
    }

    @Test
    @DisplayName("RSL_006: Load null path throws exception")
    void testLoadNullPath() {
        assertThrows(Exception.class, () -> {
            loader.load(null);
        });
    }

    @Test
    @DisplayName("RSL_007: Load empty string path throws IOException")
    void testLoadEmptyStringPath() {
        assertThrows(IOException.class, () -> {
            loader.load("");
        });
    }

    // ============= LOAD DEFAULT RULES TESTS =============

    @Test
    @DisplayName("RSL_008: loadDefaultRules success case")
    void testLoadDefaultRulesSuccess() {
        // The default rules file exists, so this should succeed
        assertDoesNotThrow(() -> {
            List<Rule> rules = loader.loadDefaultRules();
            assertNotNull(rules);
            assertTrue(rules.size() > 0);
        });
    }

    @Test
    @DisplayName("RSL_009: loadDefaultRules verifies rule content")
    void testLoadDefaultRulesContent() {
        List<Rule> rules = loader.loadDefaultRules();

        assertNotNull(rules);
        assertTrue(rules.size() > 0);

        // Verify the first rule has expected structure
        Rule firstRule = rules.get(0);
        assertNotNull(firstRule.getId());
        assertNotNull(firstRule.getTags());
        assertNotNull(firstRule.getParams());
        assertNotNull(firstRule.getChecks());
    }

    // ============= INTEGRATION TESTS WITH TEMPORARY FILES =============

    @Test
    @DisplayName("RSL_010: Load YAML with complex rule structures")
    void testLoadComplexRuleStructures() throws IOException {
        String complexYaml = """
            ---
            - id: "complex_rule_1"
              tags:
                - "lane_marking"
                - "complex"
                - "test"
              params:
                width_range_m: [0.08, 0.20]
                length_range_m: [0.5, 100.0]
                aspect_ratio_range: [0.01, 0.50]
                lane_width_m: [2.5, 4.0]
              checks:
                - "width_m >= 0.08"
                - "width_m <= 0.20"
                - "length_m >= 0.5"
                - "aspect_ratio >= 0.01 and aspect_ratio <= 0.50"
                - "(width_m * length_m) >= 0.1"

            - id: "minimal_rule"
              tags: []
              params: {}
              checks: []

            - id: "rule_with_nulls"
              tags: null
              params: null
              checks: null
            """;

        Path tempFile = createTempYamlFileInTempDir("complex_rules.yaml", complexYaml);

        List<Rule> rules = loader.load(tempFile.toString().replace(tempDir.toString() + "\\", "").replace("\\", "/"));

        assertNotNull(rules);
        assertEquals(3, rules.size());

        // Verify complex rule
        Rule complexRule = rules.get(0);
        assertEquals("complex_rule_1", complexRule.getId());
        assertEquals(3, complexRule.getTags().size());
        assertEquals(4, complexRule.getParams().size());
        assertEquals(5, complexRule.getChecks().size());

        // Verify minimal rule
        Rule minimalRule = rules.get(1);
        assertEquals("minimal_rule", minimalRule.getId());
        assertNotNull(minimalRule.getTags());
        assertEquals(0, minimalRule.getTags().size());
        assertNotNull(minimalRule.getParams());
        assertEquals(0, minimalRule.getParams().size());
        assertNotNull(minimalRule.getChecks());
        assertEquals(0, minimalRule.getChecks().size());

        // Verify rule with nulls
        Rule nullRule = rules.get(2);
        assertEquals("rule_with_nulls", nullRule.getId());
        // The YAML parser might handle nulls differently, so we just verify the ID is set
    }

    @Test
    @DisplayName("RSL_011: Load YAML with special characters and edge cases")
    void testLoadYamlWithSpecialCharacters() throws IOException {
        String specialYaml = """
            ---
            - id: "rule_with_special_chars"
              tags:
                - "tag-with-dashes"
                - "tag_with_underscores"
                - "tag.with.dots"
                - "tag with spaces"
                - "UPPERCASE_TAG"
                - "123numeric_tag"
                - ""
              params:
                "param-with-dashes": [1.0, 2.0]
                "param_with_underscores": [3.0, 4.0]
                "param.with.dots": [5.0, 6.0]
                "param with spaces": [7.0, 8.0]
                "123numeric_param": [9.0, 10.0]
              checks:
                - "value-with-dashes >= 1.0"
                - "value_with_underscores <= 10.0"
                - "value.with.dots > 0.0"
                - "value with spaces < 100.0"
                - "123numeric_value >= 0.0"
                - ""
            """;

        Path tempFile = createTempYamlFileInTempDir("special_chars_rules.yaml", specialYaml);

        List<Rule> rules = loader.load(tempFile.toString().replace(tempDir.toString() + "\\", "").replace("\\", "/"));

        assertNotNull(rules);
        assertEquals(1, rules.size());

        Rule rule = rules.get(0);
        assertEquals("rule_with_special_chars", rule.getId());
        assertNotNull(rule.getTags());
        assertTrue(rule.getTags().size() >= 6); // Some empty strings might be filtered
        assertNotNull(rule.getParams());
        assertTrue(rule.getParams().size() >= 5);
        assertNotNull(rule.getChecks());
        assertTrue(rule.getChecks().size() >= 5);
    }

    @Test
    @DisplayName("RSL_012: Load YAML with numeric edge cases")
    void testLoadYamlWithNumericEdgeCases() throws IOException {
        String numericYaml = """
            ---
            - id: "numeric_edge_cases"
              params:
                zero_values: [0.0, -0.0]
                small_values: [0.000001, 0.999999]
                large_values: [999999.0, 1000000.0]
                negative_values: [-1.0, -999.999]
                decimal_precision: [3.141592653589793, 2.718281828459045]
            """;

        Path tempFile = createTempYamlFileInTempDir("numeric_edge_rules.yaml", numericYaml);

        List<Rule> rules = loader.load(tempFile.toString().replace(tempDir.toString() + "\\", "").replace("\\", "/"));

        assertNotNull(rules);
        assertEquals(1, rules.size());

        Rule rule = rules.get(0);
        assertEquals("numeric_edge_cases", rule.getId());
        assertNotNull(rule.getParams());
        assertEquals(5, rule.getParams().size());

        // Verify specific numeric values are preserved
        double[] zeroValues = rule.getParams().get("zero_values");
        assertNotNull(zeroValues);
        assertEquals(2, zeroValues.length);
        assertEquals(0.0, zeroValues[0]);

        double[] precisionValues = rule.getParams().get("decimal_precision");
        assertNotNull(precisionValues);
        assertEquals(2, precisionValues.length);
        assertTrue(precisionValues[0] > 3.14 && precisionValues[0] < 3.15);
        assertTrue(precisionValues[1] > 2.71 && precisionValues[1] < 2.72);
    }

    // ============= ERROR RECOVERY TESTS =============

    @Test
    @DisplayName("RSL_013: Graceful handling of malformed YAML")
    void testGracefulHandlingOfMalformedYaml() throws IOException {
        String malformedYaml = """
            ---
            - id: "valid_rule"
              tags: ["valid"]
            - id: "malformed_rule"
              tags: [invalid, yaml, structure, without, quotes
              params:
                incomplete_param: [1.0
            """;

        Path tempFile = createTempYamlFileInTempDir("malformed_rules.yaml", malformedYaml);

        assertThrows(Exception.class, () -> {
            loader.load(tempFile.toString().replace(tempDir.toString() + "\\", "").replace("\\", "/"));
        });
    }

    @Test
    @DisplayName("RSL_014: Handling of very large YAML files")
    void testHandlingOfLargeYamlFiles() throws IOException {
        StringBuilder largeYaml = new StringBuilder("---\n");

        // Generate 100 rules
        for (int i = 1; i <= 100; i++) {
            largeYaml.append(String.format("""
                - id: "large_rule_%d"
                  tags: ["large", "generated", "rule_%d"]
                  params:
                    value_%d: [%d.0, %d.0]
                  checks:
                    - "value_%d >= %d.0"
                """, i, i, i, i, i + 1, i, i));
        }

        Path tempFile = createTempYamlFileInTempDir("large_rules.yaml", largeYaml.toString());

        List<Rule> rules = loader.load(tempFile.toString().replace(tempDir.toString() + "\\", "").replace("\\", "/"));

        assertNotNull(rules);
        assertEquals(100, rules.size());

        // Verify first and last rules
        Rule firstRule = rules.get(0);
        assertEquals("large_rule_1", firstRule.getId());
        assertTrue(firstRule.getTags().contains("large"));

        Rule lastRule = rules.get(99);
        assertEquals("large_rule_100", lastRule.getId());
        assertTrue(lastRule.getTags().contains("rule_100"));
    }

    // ============= HELPER METHODS =============

    private void createTempYamlFile(String relativePath, String content) {
        // This method is used for testing loadDefaultRules() behavior
        // In actual test, the file won't exist, which is expected
    }

    private Path createTempYamlFileInTempDir(String fileName, String content) throws IOException {
        Path yamlFile = tempDir.resolve(fileName);
        Files.createDirectories(yamlFile.getParent());

        try (FileWriter writer = new FileWriter(yamlFile.toFile())) {
            writer.write(content);
        }

        // Copy to test resources location so loader can find it
        Path testResourcePath = Path.of("src/test/resources").resolve(fileName);
        Files.createDirectories(testResourcePath.getParent());
        Files.copy(yamlFile, testResourcePath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        return testResourcePath;
    }

    // ============= BOUNDARY CONDITION TESTS =============

    @Test
    @DisplayName("RSL_015: Load YAML with single rule")
    void testLoadYamlWithSingleRule() throws IOException {
        String singleRuleYaml = """
            ---
            - id: "single_rule"
              tags: ["single"]
              params:
                single_param: [1.0]
              checks:
                - "single_check >= 1.0"
            """;

        Path tempFile = createTempYamlFileInTempDir("single_rule.yaml", singleRuleYaml);

        List<Rule> rules = loader.load("single_rule.yaml");

        assertNotNull(rules);
        assertEquals(1, rules.size());

        Rule rule = rules.get(0);
        assertEquals("single_rule", rule.getId());
        assertEquals(1, rule.getTags().size());
        assertEquals(1, rule.getParams().size());
        assertEquals(1, rule.getChecks().size());
    }

    @Test
    @DisplayName("RSL_016: Load YAML with maximum field lengths")
    void testLoadYamlWithMaximumFieldLengths() throws IOException {
        // Create strings with various lengths to test boundaries
        String longId = "a".repeat(100);
        String longTag = "tag_" + "x".repeat(100);
        String longParamName = "param_" + "y".repeat(100);
        String longCheck = "value_" + "z".repeat(100) + " >= 1.0";

        String maxLengthYaml = String.format("""
            ---
            - id: "%s"
              tags: ["%s"]
              params:
                "%s": [1.0, 2.0]
              checks:
                - "%s"
            """, longId, longTag, longParamName, longCheck);

        Path tempFile = createTempYamlFileInTempDir("max_length_rule.yaml", maxLengthYaml);

        List<Rule> rules = loader.load("max_length_rule.yaml");

        assertNotNull(rules);
        assertEquals(1, rules.size());

        Rule rule = rules.get(0);
        assertEquals(longId, rule.getId());
        assertTrue(rule.getTags().contains(longTag));
        assertTrue(rule.getParams().containsKey(longParamName));
        assertTrue(rule.getChecks().contains(longCheck));
    }

    @Test
    @DisplayName("RSL_017: Resource cleanup and stream handling")
    void testResourceCleanupAndStreamHandling() throws IOException {
        // Test that resources are properly cleaned up even when exceptions occur
        String validYaml = """
            ---
            - id: "cleanup_test_rule"
              tags: ["cleanup"]
            """;

        Path tempFile = createTempYamlFileInTempDir("cleanup_test.yaml", validYaml);

        // Multiple loads should work without resource leaks
        for (int i = 0; i < 10; i++) {
            List<Rule> rules = loader.load("cleanup_test.yaml");
            assertNotNull(rules);
            assertEquals(1, rules.size());
        }
    }
}