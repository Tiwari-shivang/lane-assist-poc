package com.example.lanes.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Result class for validation operations
 */
public class ValidationResult {
    private boolean isValid;
    private String markingType;
    private List<String> errors;
    private double confidence;
    private String failureReason;

    // Default constructor
    public ValidationResult() {
        this.isValid = false;
        this.errors = new ArrayList<>();
        this.confidence = 0.0;
    }

    // Constructor with boolean
    public ValidationResult(boolean isValid) {
        this.isValid = isValid;
        this.errors = new ArrayList<>();
        this.confidence = 0.0;
    }

    // Constructor with boolean and reason
    public ValidationResult(boolean isValid, String reason) {
        this.isValid = isValid;
        this.failureReason = reason;
        this.errors = new ArrayList<>();
        if (!isValid && reason != null) {
            this.errors.add(reason);
        }
        this.confidence = 0.0;
    }

    // Getters and Setters
    public boolean isValid() {
        return isValid;
    }

    public void setValid(boolean valid) {
        isValid = valid;
    }

    public String getMarkingType() {
        return markingType;
    }

    public void setMarkingType(String markingType) {
        this.markingType = markingType;
    }

    public List<String> getErrors() {
        return errors;
    }

    public void setErrors(List<String> errors) {
        this.errors = errors;
    }

    public void addError(String error) {
        this.errors.add(error);
        this.isValid = false; // Automatically set to false when error is added
    }

    public double getConfidence() {
        return confidence;
    }

    public void setConfidence(double confidence) {
        this.confidence = confidence;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }
}