package com.example.lanes.core;

import com.example.lanes.model.PolygonDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public record OverlayResult(List<PolygonDto> polygons, byte[] pngBytes) {
  public String polygonJson() {
    try {
      return new ObjectMapper().writeValueAsString(polygons);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }
}