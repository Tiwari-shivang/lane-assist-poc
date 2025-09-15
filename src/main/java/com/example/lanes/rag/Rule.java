package com.example.lanes.rag;

import java.util.List;
import java.util.Map;

public class Rule {
    private String id;
    private List<String> tags;
    private Map<String, double[]> params;
    private List<String> checks;
    
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }
    
    public Map<String, double[]> getParams() { return params; }
    public void setParams(Map<String, double[]> params) { this.params = params; }
    
    public List<String> getChecks() { return checks; }
    public void setChecks(List<String> checks) { this.checks = checks; }
}