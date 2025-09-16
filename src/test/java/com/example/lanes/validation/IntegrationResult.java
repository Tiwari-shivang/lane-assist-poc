package com.example.lanes.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the result of an integration test with multiple validation steps
 */
public class IntegrationResult {
    private boolean valid;
    private List<String> passedSteps = new ArrayList<>();
    private List<String> failedSteps = new ArrayList<>();
    private String failureReason;
    private List<String> validatedComponents = new ArrayList<>();
    private List<String> validatedExits = new ArrayList<>();
    private List<String> validatedModes = new ArrayList<>();
    private Map<String, Boolean> weatherAcceptance = new HashMap<>();
    private double performanceRating;
    
    // Standards compliance flags
    private boolean motorwayStandards = false;
    private boolean temporaryStandards = false;
    private boolean childSafetyStandards = false;
    private boolean accessibilityStandards = false;
    private boolean publicTransportStandards = false;
    private boolean cyclingStandards = false;
    private boolean highwayStandards = false;
    
    public boolean isValid() { 
        return valid; 
    }
    
    public void setValid(boolean valid) { 
        this.valid = valid; 
    }
    
    public List<String> getPassedSteps() { 
        return passedSteps; 
    }
    
    public void addPassedStep(String step) { 
        this.passedSteps.add(step); 
    }
    
    public List<String> getFailedSteps() { 
        return failedSteps; 
    }
    
    public void addFailedStep(String step) { 
        this.failedSteps.add(step); 
        this.valid = false;
    }
    
    public String getFailureReason() { 
        return failureReason; 
    }
    
    public void setFailureReason(String reason) { 
        this.failureReason = reason; 
    }
    
    public List<String> getValidatedComponents() { 
        return validatedComponents; 
    }
    
    public void addValidatedComponent(String component) { 
        this.validatedComponents.add(component); 
    }
    
    public List<String> getValidatedExits() { 
        return validatedExits; 
    }
    
    public void addValidatedExit(String exit) { 
        this.validatedExits.add(exit); 
    }
    
    public List<String> getValidatedModes() { 
        return validatedModes; 
    }
    
    public void addValidatedMode(String mode) { 
        this.validatedModes.add(mode); 
    }
    
    public void addWeatherAcceptance(String condition, boolean acceptable) {
        this.weatherAcceptance.put(condition, acceptable);
    }
    
    public boolean isAcceptableForCondition(String condition) {
        return weatherAcceptance.getOrDefault(condition, false);
    }
    
    public double getPerformanceRating() { 
        return performanceRating; 
    }
    
    public void setPerformanceRating(double rating) { 
        this.performanceRating = rating; 
    }
    
    // Standards compliance methods
    public boolean meetsMotorwayStandards() { 
        return motorwayStandards; 
    }
    
    public void setMotorwayStandards(boolean meets) { 
        this.motorwayStandards = meets; 
    }
    
    public boolean meetsTemporaryStandards() { 
        return temporaryStandards; 
    }
    
    public void setTemporaryStandards(boolean meets) { 
        this.temporaryStandards = meets; 
    }
    
    public boolean meetsChildSafetyStandards() { 
        return childSafetyStandards; 
    }
    
    public void setChildSafetyStandards(boolean meets) { 
        this.childSafetyStandards = meets; 
    }
    
    public boolean meetsAccessibilityStandards() { 
        return accessibilityStandards; 
    }
    
    public void setAccessibilityStandards(boolean meets) { 
        this.accessibilityStandards = meets; 
    }
    
    public boolean meetsPublicTransportStandards() { 
        return publicTransportStandards; 
    }
    
    public void setPublicTransportStandards(boolean meets) { 
        this.publicTransportStandards = meets; 
    }
    
    public boolean meetsCyclingStandards() { 
        return cyclingStandards; 
    }
    
    public void setCyclingStandards(boolean meets) { 
        this.cyclingStandards = meets; 
    }
    
    public boolean meetsHighwayStandards() { 
        return highwayStandards; 
    }
    
    public void setHighwayStandards(boolean meets) { 
        this.highwayStandards = meets; 
    }
}