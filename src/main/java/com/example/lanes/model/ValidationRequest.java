package com.example.lanes.model;

import java.util.Map;

public record ValidationRequest(
    String group,
    String type,
    Map<String, Double> features
) {}