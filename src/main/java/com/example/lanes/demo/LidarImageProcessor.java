package com.example.lanes.demo;

import com.example.lanes.core.LanePolygonService;
import com.example.lanes.core.OverlayResult;
import com.example.lanes.model.DebugFrames;
import com.example.lanes.model.PolygonDto;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Demo application to process LiDAR road images and detect European standard lane markings.
 * This component runs automatically when the Spring Boot application starts.
 */
@Component
@RequiredArgsConstructor
public class LidarImageProcessor implements CommandLineRunner {

    private final LanePolygonService lanePolygonService;

    @Override
    public void run(String... args) throws Exception {
        System.out.println("\n=== LiDAR Image Processing Demo ===");
        System.out.println("Processing European road markings...\n");

        try {
            // Look for any input image in common formats
            Path inputPath = findInputImage();
            if (inputPath == null) {
                System.out.println("INFO: No input image found for demo processing.");
                System.out.println("To run the demo, provide one of these files:");
                System.out.println("  - input_lidar_road.png");
                System.out.println("  - test_image.png");
                System.out.println("  - sample.png");
                System.out.println("Or use the API endpoints to process images directly.");
                return;
            }

            byte[] imageBytes = Files.readAllBytes(inputPath);
            System.out.println("✓ Loaded input image: " + inputPath.toAbsolutePath());
            System.out.println("  Image size: " + imageBytes.length + " bytes");

            // Set pixels per meter (typical LiDAR resolution: ~10-20 pixels per meter)
            double ppm = 15.0; // pixels per meter
            System.out.println("  Using resolution: " + ppm + " pixels per meter");

            // Process the image to detect lane markings
            System.out.println("\n--- Processing Image ---");
            long startTime = System.currentTimeMillis();
            OverlayResult result = lanePolygonService.process(imageBytes, ppm);
            long processingTime = System.currentTimeMillis() - startTime;

            System.out.println("✓ Processing completed in " + processingTime + "ms");

            // Extract detected polygons
            List<PolygonDto> polygons = result.polygons();
            System.out.println("✓ Detected " + polygons.size() + " lane marking polygons:");

            // Display polygon information
            for (int i = 0; i < polygons.size(); i++) {
                PolygonDto polygon = polygons.get(i);
                System.out.println("  Polygon " + (i + 1) + ":");
                System.out.println("    Points: " + polygon.points().size());
                System.out.println("    Type: " + polygon.type());
                System.out.println("    Area: " + String.format("%.1f", polygon.areaPx()) + " pixels²");
                System.out.println("    Rule matches: " + polygon.ruleIds());

                // Calculate approximate center and dimensions
                if (!polygon.points().isEmpty()) {
                    double avgX = polygon.points().stream().mapToDouble(p -> p[0]).average().orElse(0);
                    double avgY = polygon.points().stream().mapToDouble(p -> p[1]).average().orElse(0);
                    System.out.println("    Center: (" + String.format("%.1f", avgX) + ", " + String.format("%.1f", avgY) + ")");

                    double minX = polygon.points().stream().mapToDouble(p -> p[0]).min().orElse(0);
                    double maxX = polygon.points().stream().mapToDouble(p -> p[0]).max().orElse(0);
                    double minY = polygon.points().stream().mapToDouble(p -> p[1]).min().orElse(0);
                    double maxY = polygon.points().stream().mapToDouble(p -> p[1]).max().orElse(0);

                    double widthM = (maxX - minX) / ppm;
                    double heightM = (maxY - minY) / ppm;
                    System.out.println("    Dimensions: " + String.format("%.2f", widthM) + "m x " + String.format("%.2f", heightM) + "m");

                    // Display features if available
                    if (polygon.features() != null && !polygon.features().isEmpty()) {
                        System.out.println("    Features:");
                        polygon.features().forEach((key, value) ->
                            System.out.println("      " + key + ": " + String.format("%.3f", value)));
                    }
                }
            }

            // Save the output image with overlays
            Path outputPath = Paths.get("output_lidar_with_markings.png");
            Files.write(outputPath, result.pngBytes());
            System.out.println("\n✓ Output image saved: " + outputPath.toAbsolutePath());

            // Generate debug frames
            System.out.println("\n--- Generating Debug Information ---");
            DebugFrames debugFrames = lanePolygonService.processWithDebug(imageBytes);

            // Save debug images
            Files.write(Paths.get("debug_road_mask.png"), debugFrames.roadMask());
            Files.write(Paths.get("debug_marks_mask.png"), debugFrames.marksMask());
            Files.write(Paths.get("debug_bands_mask.png"), debugFrames.bandsMask());
            Files.write(Paths.get("debug_overlay.png"), debugFrames.overlayImage());

            System.out.println("✓ Debug images saved:");
            System.out.println("  - debug_road_mask.png (road surface detection)");
            System.out.println("  - debug_marks_mask.png (lane marking detection)");
            System.out.println("  - debug_bands_mask.png (processing bands)");
            System.out.println("  - debug_overlay.png (final overlay)");

            // Display European standard compliance information
            System.out.println("\n--- European Standards Analysis ---");
            analyzeEuropeanCompliance(polygons, ppm);

            System.out.println("\n=== Processing Complete ===");
            System.out.println("Generated files in project root:");
            System.out.println("  - input_lidar_road.png (original LiDAR image)");
            System.out.println("  - output_lidar_with_markings.png (processed with detected markings)");
            System.out.println("  - debug_*.png (diagnostic images)");

        } catch (IOException e) {
            System.err.println("ERROR: Failed to process image: " + e.getMessage());
            e.printStackTrace();
        } catch (Exception e) {
            System.err.println("ERROR: Unexpected error during processing: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Path findInputImage() {
        String[] possibleNames = {
            "input_lidar_road.png",
            "test_image.png", 
            "sample.png",
            "road_image.png",
            "lidar_image.png"
        };
        
        for (String name : possibleNames) {
            Path path = Paths.get(name);
            if (Files.exists(path)) {
                return path;
            }
        }
        return null;
    }

    private void analyzeEuropeanCompliance(List<PolygonDto> polygons, double ppm) {
        System.out.println("European Road Marking Standards (Vienna Convention/CEN EN 1436):");

        for (int i = 0; i < polygons.size(); i++) {
            PolygonDto polygon = polygons.get(i);

            if (!polygon.points().isEmpty()) {
                // Calculate polygon dimensions in meters
                double minX = polygon.points().stream().mapToDouble(p -> p[0]).min().orElse(0);
                double maxX = polygon.points().stream().mapToDouble(p -> p[0]).max().orElse(0);
                double minY = polygon.points().stream().mapToDouble(p -> p[1]).min().orElse(0);
                double maxY = polygon.points().stream().mapToDouble(p -> p[1]).max().orElse(0);

                double widthM = (maxX - minX) / ppm;
                double heightM = (maxY - minY) / ppm;

                System.out.println("  Marking " + (i + 1) + ":");

                // Analyze width compliance
                if (widthM >= 0.10 && widthM <= 0.15) {
                    System.out.println("    ✓ Width compliant: " + String.format("%.3f", widthM) + "m (standard: 0.10-0.15m)");
                } else if (widthM >= 0.08 && widthM <= 0.20) {
                    System.out.println("    ~ Width acceptable: " + String.format("%.3f", widthM) + "m (standard: 0.10-0.15m)");
                } else {
                    System.out.println("    ✗ Width non-compliant: " + String.format("%.3f", widthM) + "m (standard: 0.10-0.15m)");
                }

                // Analyze aspect ratio for line markings
                double aspectRatio = Math.max(heightM, widthM) / Math.min(heightM, widthM);
                if (aspectRatio > 10) {
                    System.out.println("    ✓ Line marking detected (aspect ratio: " + String.format("%.1f", aspectRatio) + ")");
                } else {
                    System.out.println("    ~ Symbol/patch detected (aspect ratio: " + String.format("%.1f", aspectRatio) + ")");
                }

                // Estimate marking type based on dimensions
                if (heightM > 2.0 && widthM < 0.20) {
                    System.out.println("    Type: Longitudinal line marking");
                } else if (widthM > 2.0 && heightM < 0.20) {
                    System.out.println("    Type: Transverse line marking");
                } else {
                    System.out.println("    Type: Symbol or patch marking");
                }
            }
        }

        if (polygons.isEmpty()) {
            System.out.println("  No lane markings detected in the image.");
            System.out.println("  This may indicate:");
            System.out.println("    - Very low contrast markings");
            System.out.println("    - Insufficient LiDAR intensity difference");
            System.out.println("    - Algorithm parameters need adjustment");
        }
    }
}