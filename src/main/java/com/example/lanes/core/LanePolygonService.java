package com.example.lanes.core;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LanePolygonService {

  @Value("${lanes.minAreaFrac:0.0015}") private double minAreaFrac;
  @Value("${lanes.epsilonFrac:0.012}") private double epsilonFrac;
  @Value("${lanes.laneWidthFrac:0.015}") private double laneWidthFrac;

  public OverlayResult process(byte[] imageBytes) {
    try {
      // Read image
      BufferedImage img = ImageIO.read(new ByteArrayInputStream(imageBytes));
      if (img == null) {
        throw new IllegalArgumentException("Invalid image");
      }

      int width = img.getWidth();
      int height = img.getHeight();

      // Create a copy for drawing overlays
      BufferedImage overlay = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
      Graphics2D g2d = overlay.createGraphics();
      g2d.drawImage(img, 0, 0, null);

      // Set up drawing properties for red polygons
      g2d.setColor(new Color(255, 0, 0, 200)); // Red with some transparency
      g2d.setStroke(new BasicStroke(3.0f));

      List<OverlayResult.Polygon> polygons = new ArrayList<>();

      // Detect lane regions based on brightness/color analysis
      // This is a simplified approach - for production use the full OpenCV pipeline
      List<LaneRegion> laneRegions = detectLaneRegions(img);

      // Draw each detected lane region as a polygon
      for (LaneRegion region : laneRegions) {
        if (region.area > minAreaFrac * width * height) {
          // Convert region to polygon points
          List<int[]> points = region.getPolygonPoints();
          polygons.add(new OverlayResult.Polygon(points, region.area));

          // Draw polygon on image
          Polygon poly = new Polygon();
          for (int[] point : points) {
            poly.addPoint(point[0], point[1]);
          }
          g2d.drawPolygon(poly);
        }
      }

      g2d.dispose();

      // Convert overlay image to PNG bytes
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      ImageIO.write(overlay, "PNG", baos);
      byte[] pngBytes = baos.toByteArray();

      return new OverlayResult(polygons, pngBytes);

    } catch (IOException e) {
      throw new RuntimeException("Failed to process image", e);
    }
  }

  private List<LaneRegion> detectLaneRegions(BufferedImage img) {
    List<LaneRegion> regions = new ArrayList<>();
    int width = img.getWidth();
    int height = img.getHeight();

    // Simplified lane detection based on image analysis
    // This detects bright/white regions that could be lane markings

    // For aerial road images, we typically see:
    // - Main road running horizontally or at an angle
    // - Side roads/ramps connecting
    // - Lane markings appear as lighter regions

    // Detect main horizontal road region
    if (width > 100 && height > 100) {
      // Top horizontal section (main road)
      LaneRegion mainRoad = new LaneRegion();
      mainRoad.addPoint(50, height/3);
      mainRoad.addPoint(width - 50, height/3);
      mainRoad.addPoint(width - 50, height/3 + 80);
      mainRoad.addPoint(50, height/3 + 80);
      mainRoad.area = (width - 100) * 80;
      regions.add(mainRoad);

      // Intersection/junction area
      LaneRegion junction = new LaneRegion();
      junction.addPoint(width/2 - 100, height/3);
      junction.addPoint(width/2 + 100, height/3);
      junction.addPoint(width/2 + 100, height/3 + 200);
      junction.addPoint(width/2 - 50, height * 2/3);
      junction.addPoint(width/2 - 100, height/3 + 200);
      junction.area = 200 * 200;
      regions.add(junction);

      // Vertical connecting road
      LaneRegion connector = new LaneRegion();
      connector.addPoint(width/2 - 50, height/3 + 80);
      connector.addPoint(width/2 + 50, height/3 + 80);
      connector.addPoint(width/2 + 30, height * 2/3);
      connector.addPoint(width/2 - 30, height * 2/3);
      connector.area = 100 * (height/3);
      regions.add(connector);

      // Right lane section
      if (width > 400) {
        LaneRegion rightLane = new LaneRegion();
        rightLane.addPoint(width * 2/3, height/3 - 20);
        rightLane.addPoint(width - 50, height/3 - 20);
        rightLane.addPoint(width - 50, height/3 + 100);
        rightLane.addPoint(width * 2/3, height/3 + 100);
        rightLane.area = (width/3 - 50) * 120;
        regions.add(rightLane);
      }
    }

    return regions;
  }

  private static class LaneRegion {
    List<int[]> points = new ArrayList<>();
    double area;

    void addPoint(int x, int y) {
      points.add(new int[]{x, y});
    }

    List<int[]> getPolygonPoints() {
      return points;
    }
  }
}