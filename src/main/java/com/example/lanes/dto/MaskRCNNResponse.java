package com.example.lanes.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MaskRCNNResponse(
    List<MaskRCNNPolygonDTO> polygons,
    
    @JsonProperty("image_shape")
    int[] imageShape,
    
    @JsonProperty("total_detections")
    int totalDetections,
    
    @JsonProperty("model_info")
    ModelInfo modelInfo
) {
    
    public record ModelInfo(
        @JsonProperty("score_threshold")
        double scoreThreshold,
        
        @JsonProperty("num_classes")
        int numClasses
    ) {}
    
    public List<MaskRCNNPolygonDTO> getValidLaneMarkings() {
        return polygons.stream()
                .filter(MaskRCNNPolygonDTO::isValidLaneMarking)
                .toList();
    }
    
    public List<MaskRCNNPolygonDTO> getPolygonsByClass(int classId) {
        return polygons.stream()
                .filter(p -> p.classId() == classId)
                .toList();
    }
}