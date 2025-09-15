package com.example.lanes.rag;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RuleValidator {
    
    public List<String> validatePolygon(Map<String, Double> features, List<Rule> rules) {
        List<String> matchingRuleIds = new ArrayList<>();
        
        for (Rule rule : rules) {
            if (passesAllChecks(features, rule)) {
                matchingRuleIds.add(rule.getId());
            }
        }
        
        return matchingRuleIds;
    }
    
    private boolean passesAllChecks(Map<String, Double> features, Rule rule) {
        if (rule.getChecks() == null || rule.getChecks().isEmpty()) {
            return true;
        }
        
        for (String check : rule.getChecks()) {
            if (!evaluateCheck(check, features)) {
                return false;
            }
        }
        
        return true;
    }
    
    private boolean evaluateCheck(String check, Map<String, Double> features) {
        // Simple expression evaluator for checks like "width_m >= 0.10"
        // This is a simplified version - in production, use a proper expression evaluator
        
        try {
            // Replace feature names with their values
            String expression = check;
            for (Map.Entry<String, Double> entry : features.entrySet()) {
                expression = expression.replace(entry.getKey(), String.valueOf(entry.getValue()));
            }
            
            // Basic evaluation for common operators
            if (expression.contains(">=")) {
                String[] parts = expression.split(">=");
                double left = evaluateSimpleExpression(parts[0].trim());
                double right = evaluateSimpleExpression(parts[1].trim());
                return left >= right;
            } else if (expression.contains("<=")) {
                String[] parts = expression.split("<=");
                double left = evaluateSimpleExpression(parts[0].trim());
                double right = evaluateSimpleExpression(parts[1].trim());
                return left <= right;
            } else if (expression.contains(">")) {
                String[] parts = expression.split(">");
                double left = evaluateSimpleExpression(parts[0].trim());
                double right = evaluateSimpleExpression(parts[1].trim());
                return left > right;
            } else if (expression.contains("<")) {
                String[] parts = expression.split("<");
                double left = evaluateSimpleExpression(parts[0].trim());
                double right = evaluateSimpleExpression(parts[1].trim());
                return left < right;
            } else if (expression.contains("and")) {
                String[] parts = expression.split("and");
                boolean result = true;
                for (String part : parts) {
                    result = result && evaluateCheck(part.trim(), features);
                }
                return result;
            }
            
            return true;
        } catch (Exception e) {
            // If evaluation fails, return false (conservative approach)
            return false;
        }
    }
    
    private double evaluateSimpleExpression(String expr) {
        expr = expr.trim();
        
        // Handle parentheses
        expr = expr.replace("(", "").replace(")", "");
        
        // Handle division
        if (expr.contains("/")) {
            String[] parts = expr.split("/");
            return evaluateSimpleExpression(parts[0]) / evaluateSimpleExpression(parts[1]);
        }
        
        // Handle multiplication
        if (expr.contains("*")) {
            String[] parts = expr.split("\\*");
            return evaluateSimpleExpression(parts[0]) * evaluateSimpleExpression(parts[1]);
        }
        
        // Handle addition
        if (expr.contains("+")) {
            String[] parts = expr.split("\\+");
            return evaluateSimpleExpression(parts[0]) + evaluateSimpleExpression(parts[1]);
        }
        
        // Handle subtraction
        if (expr.contains("-") && !expr.startsWith("-")) {
            String[] parts = expr.split("-");
            return evaluateSimpleExpression(parts[0]) - evaluateSimpleExpression(parts[1]);
        }
        
        // Handle abs function
        if (expr.startsWith("abs")) {
            String inner = expr.substring(3).trim();
            return Math.abs(evaluateSimpleExpression(inner));
        }
        
        // Handle max function
        if (expr.startsWith("max")) {
            String inner = expr.substring(3).trim();
            String[] parts = inner.split(",");
            return Math.max(evaluateSimpleExpression(parts[0]), evaluateSimpleExpression(parts[1]));
        }
        
        // Parse as number
        return Double.parseDouble(expr);
    }
    
    public double getLaneWidthFromRules(List<Rule> rules) {
        // Extract lane width from rules for dilation kernel sizing
        for (Rule rule : rules) {
            if (rule.getParams() != null && rule.getParams().containsKey("lane_width_m")) {
                double[] widthRange = rule.getParams().get("lane_width_m");
                if (widthRange != null && widthRange.length > 0) {
                    return widthRange[0]; // Use minimum width
                }
            }
        }
        return 3.0; // Default lane width in meters
    }
}