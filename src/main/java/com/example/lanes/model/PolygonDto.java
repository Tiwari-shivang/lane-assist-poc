package com.example.lanes.model;

import java.util.List;
import java.util.Map;

public record PolygonDto(
    String type,
    List<int[]> points,
    double areaPx,
    Map<String, Double> features,
    List<String> ruleIds
) {}