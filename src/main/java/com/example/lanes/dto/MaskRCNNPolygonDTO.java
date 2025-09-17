package com.example.lanes.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MaskRCNNPolygonDTO(
    @JsonProperty("class_id")
    int classId,
    
    @JsonProperty("class_name")
    String className,
    
    double score,
    
    List<int[]> points,
    
    @JsonProperty("area_px")
    double areaPx,
    
    @JsonProperty("area_m2")
    double areaM2,
    
    @JsonProperty("perimeter_m")
    double perimeterM,
    
    @JsonProperty("width_m")
    double widthM,
    
    double ppm
) {
    
    public String getMarkingType() {
        return switch (classId) {
            case 0 -> "LANE_MARKING";
            case 1 -> "JUNCTION_CORE";
            case 2 -> "ZEBRA_CROSSING";
            case 3 -> "STOP_LINE";
            case 4 -> "ARROW";
            default -> "UNKNOWN";
        };
    }
    
    public boolean isValidLaneMarking() {
        return score > 0.5 && areaPx > 200 && widthM > 0.05 && widthM < 0.5;
    }
}