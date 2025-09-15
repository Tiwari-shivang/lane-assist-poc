package com.example.lanes.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

@Component
public class RuleSuiteLoader {
    private final ObjectMapper yamlMapper;
    
    public RuleSuiteLoader() {
        this.yamlMapper = new ObjectMapper(new YAMLFactory());
    }
    
    public List<Rule> load(String path) throws IOException {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream inputStream = resource.getInputStream()) {
            return Arrays.asList(yamlMapper.readValue(inputStream, Rule[].class));
        }
    }
    
    public List<Rule> loadDefaultRules() {
        try {
            return load("rules/eu_lane_rules.yaml");
        } catch (IOException e) {
            throw new RuntimeException("Failed to load default rules", e);
        }
    }
}