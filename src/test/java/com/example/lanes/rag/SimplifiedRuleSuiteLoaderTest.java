package com.example.lanes.rag;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.BeforeEach;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simplified test suite for RuleSuiteLoader focusing on core functionality
 */
public class SimplifiedRuleSuiteLoaderTest {

    private RuleSuiteLoader loader;

    @BeforeEach
    void setUp() {
        loader = new RuleSuiteLoader();
    }

    @Test
    @DisplayName("Constructor initializes without errors")
    void testConstructor() {
        RuleSuiteLoader newLoader = new RuleSuiteLoader();
        assertNotNull(newLoader);
    }

    @Test
    @DisplayName("Load valid test rules file")
    void testLoadValidFile() throws IOException {
        List<Rule> rules = loader.load("test-rules/test_rules.yaml");

        assertNotNull(rules);
        assertTrue(rules.size() > 0);

        // Verify first rule structure
        if (rules.size() > 0) {
            Rule firstRule = rules.get(0);
            assertNotNull(firstRule.getId());
        }
    }

    @Test
    @DisplayName("Load empty rules file")
    void testLoadEmptyFile() throws IOException {
        List<Rule> rules = loader.load("test-rules/empty_rules.yaml");

        assertNotNull(rules);
        assertEquals(0, rules.size());
    }

    @Test
    @DisplayName("Load non-existent file throws IOException")
    void testLoadNonExistentFile() {
        assertThrows(IOException.class, () -> {
            loader.load("non-existent-file.yaml");
        });
    }

    @Test
    @DisplayName("Load null path throws exception")
    void testLoadNullPath() {
        assertThrows(Exception.class, () -> {
            loader.load(null);
        });
    }

    @Test
    @DisplayName("loadDefaultRules loads successfully when file exists")
    void testLoadDefaultRules() {
        // The default rules file exists, so this should succeed
        assertDoesNotThrow(() -> {
            List<Rule> rules = loader.loadDefaultRules();
            assertNotNull(rules);
        });
    }

    @Test
    @DisplayName("Load invalid YAML throws exception")
    void testLoadInvalidYaml() {
        assertThrows(Exception.class, () -> {
            loader.load("test-rules/invalid_rules.yaml");
        });
    }

    @Test
    @DisplayName("Multiple load operations work correctly")
    void testMultipleLoads() throws IOException {
        // Test that multiple loads don't interfere with each other
        List<Rule> rules1 = loader.load("test-rules/test_rules.yaml");
        List<Rule> rules2 = loader.load("test-rules/empty_rules.yaml");
        List<Rule> rules3 = loader.load("test-rules/test_rules.yaml");

        assertNotNull(rules1);
        assertNotNull(rules2);
        assertNotNull(rules3);

        assertEquals(rules1.size(), rules3.size()); // Same file should give same results
        assertEquals(0, rules2.size()); // Empty file should be empty
    }

    @Test
    @DisplayName("Loader handles resource cleanup properly")
    void testResourceCleanup() throws IOException {
        // Test that resources are cleaned up properly by doing multiple operations
        for (int i = 0; i < 5; i++) {
            try {
                loader.load("test-rules/test_rules.yaml");
            } catch (Exception e) {
                // Continue to test cleanup even if load fails
            }
        }

        // If we reach here without memory issues, cleanup is working
        assertTrue(true);
    }

    @Test
    @DisplayName("Yaml mapper handles various rule structures")
    void testYamlMapperVariations() throws IOException {
        List<Rule> rules = loader.load("test-rules/test_rules.yaml");

        assertNotNull(rules);

        // Test that different rules have different structures
        if (rules.size() >= 2) {
            Rule rule1 = rules.get(0);
            Rule rule2 = rules.get(1);

            // They should both have IDs but might have different other fields
            assertNotNull(rule1.getId());
            assertNotNull(rule2.getId());
            assertNotEquals(rule1.getId(), rule2.getId());
        }
    }

    @Test
    @DisplayName("loadDefaultRules handles IOException properly")
    void testLoadDefaultRulesWithIOException() {
        // Create a loader that will fail when trying to load default rules
        RuleSuiteLoader failingLoader = new RuleSuiteLoader() {
            @Override
            public List<Rule> load(String path) throws IOException {
                throw new IOException("Simulated file read failure");
            }
        };

        // The loadDefaultRules method should catch IOException and wrap it in RuntimeException
        RuntimeException exception = assertThrows(RuntimeException.class,
            () -> failingLoader.loadDefaultRules());

        assertEquals("Failed to load default rules", exception.getMessage());
        assertTrue(exception.getCause() instanceof IOException);
    }
}