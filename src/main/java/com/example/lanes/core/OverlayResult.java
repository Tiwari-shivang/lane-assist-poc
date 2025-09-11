package com.example.lanes.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

public record OverlayResult(List<Polygon> polygons, byte[] pngBytes) {
  public record Polygon(List<int[]> points, double area) {}

  public String polygonJson() {
    try {
      return new ObjectMapper().writeValueAsString(polygons);
    } catch (JsonProcessingException e) {
      return "[]";
    }
  }
}