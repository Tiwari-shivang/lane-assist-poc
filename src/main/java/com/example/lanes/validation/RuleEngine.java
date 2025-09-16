package com.example.lanes.validation;

import java.util.Map;

/**
 * Simple rule engine for integration testing
 */
public class RuleEngine {

    public ValidationResult validateComplete(Map<String, Object> params) {
        // This method would typically orchestrate multiple validations
        return new ValidationResult(true, "Complete validation passed");
    }

    public boolean isValidConfiguration() {
        return true;
    }
}