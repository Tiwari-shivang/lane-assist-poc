package com.example.lanes.validation;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents the result of a validation operation
 */
public class ValidationResult {
    private boolean valid;
    private String markingType;
    private List<String> errors = new ArrayList<>();
    private double confidence;
    private String failureReason;
    
    public ValidationResult() {}
    
    public ValidationResult(boolean valid) {
        this.valid = valid;
    }
    
    public ValidationResult(boolean valid, String reason) {
        this.valid = valid;
        this.failureReason = reason;
        if (!valid && reason != null) {
            this.errors.add(reason);
        }
    }
    
    public boolean isValid() { 
        return valid; 
    }
    
    public void setValid(boolean valid) { 
        this.valid = valid; 
    }
    
    public String getMarkingType() { 
        return markingType; 
    }
    
    public void setMarkingType(String type) { 
        this.markingType = type; 
    }
    
    public List<String> getErrors() { 
        return errors; 
    }
    
    public void addError(String error) { 
        this.errors.add(error);
        this.valid = false;
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
    
    public void setFailureReason(String reason) { 
        this.failureReason = reason; 
    }
}