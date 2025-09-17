package com.example.lanes.core;

import com.example.lanes.config.LaneConfig;
import com.example.lanes.dto.MaskRCNNPolygonDTO;
import com.example.lanes.dto.MaskRCNNResponse;
import com.example.lanes.model.DebugFrames;
import com.example.lanes.model.PolygonDto;
import com.example.lanes.rag.Rule;
import com.example.lanes.rag.RuleSuiteLoader;
import com.example.lanes.rag.RuleValidator;
import lombok.RequiredArgsConstructor;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_imgproc.CLAHE;
import org.bytedeco.opencv.opencv_imgproc.Vec4iVector;
import org.springframework.stereotype.Service;

import java.util.*;

import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

import org.bytedeco.javacpp.IntPointer;

@Service
@RequiredArgsConstructor
public class LanePolygonService {

    private final LaneConfig config;
    private final RuleSuiteLoader ruleSuiteLoader;
    private final RuleValidator ruleValidator;
    private List<Rule> rules;

    public OverlayResult process(byte[] imageBytes, Double overridePpm) {
        double ppm = overridePpm != null ? overridePpm : config.getPpm();
        
        // Load rules if not already loaded
        if (rules == null) {
            rules = ruleSuiteLoader.loadDefaultRules();
        }

        // Decode image to grayscale
        Mat buf = new Mat(1, imageBytes.length, CV_8U);
        buf.data().put(imageBytes);
        Mat gray = imdecode(buf, IMREAD_GRAYSCALE);
        if (gray == null || gray.empty()) {
            throw new IllegalArgumentException("Invalid image");
        }

        // Apply CLAHE for contrast enhancement
        Mat enhanced = applyCLAHE(gray);

        // Build masks
        Mat roadMask = buildRoadMask(enhanced);
        Mat marksMask = buildMarksMask(enhanced, roadMask);

        // Estimate dominant direction
        double theta = estimateHeading(enhanced, roadMask);

        // Grow bands using anisotropic dilation
        Mat bands = growBands(marksMask, theta, ppm);

        // Find and classify polygons
        List<PolygonDto> polygons = findPolygons(bands, roadMask, enhanced, marksMask, ppm);

        // Draw overlay
        byte[] overlayPng = drawOverlay(gray, polygons);

        return new OverlayResult(polygons, overlayPng);
    }

    public DebugFrames processWithDebug(byte[] imageBytes) {
        // Similar to process but returns debug frames
        Mat buf = new Mat(1, imageBytes.length, CV_8U);
        buf.data().put(imageBytes);
        Mat gray = imdecode(buf, IMREAD_GRAYSCALE);
        
        Mat enhanced = applyCLAHE(gray);
        Mat roadMask = buildRoadMask(enhanced);
        Mat marksMask = buildMarksMask(enhanced, roadMask);
        double theta = estimateHeading(enhanced, roadMask);
        Mat bands = growBands(marksMask, theta, config.getPpm());
        List<PolygonDto> polygons = findPolygons(bands, roadMask, enhanced, marksMask, config.getPpm());
        
        byte[] roadPng = matToPng(roadMask);
        byte[] marksPng = matToPng(marksMask);
        byte[] bandsPng = matToPng(bands);
        byte[] overlayPng = drawOverlay(gray, polygons);
        
        return new DebugFrames(roadPng, marksPng, bandsPng, overlayPng);
    }

    private Mat applyCLAHE(Mat gray) {
        CLAHE clahe = createCLAHE(3.0, new Size(8, 8));
        Mat enhanced = new Mat();
        clahe.apply(gray, enhanced);
        return enhanced;
    }

    private Mat buildRoadMask(Mat enhanced) {
        // Road is darker than background - inverse threshold
        Mat roadMask = new Mat();
        threshold(enhanced, roadMask, 0, 255, THRESH_BINARY_INV | THRESH_OTSU);

        // Morphological operations to clean up
        Mat kernel9 = getStructuringElement(MORPH_RECT, new Size(9, 9));
        Mat kernel5 = getStructuringElement(MORPH_RECT, new Size(5, 5));
        
        morphologyEx(roadMask, roadMask, MORPH_CLOSE, kernel9);
        morphologyEx(roadMask, roadMask, MORPH_OPEN, kernel5);

        // Keep only large components (remove noise)
        roadMask = keepLargeComponents(roadMask, enhanced.rows() * enhanced.cols() * 0.01);
        
        return roadMask;
    }

    private Mat buildMarksMask(Mat enhanced, Mat roadMask) {
        // Top-hat to extract bright markings
        int kernelSize = config.getTophat().getKernelPx();
        Mat tophatKernel = getStructuringElement(MORPH_RECT, new Size(kernelSize, kernelSize));
        Mat tophat = new Mat();
        morphologyEx(enhanced, tophat, MORPH_TOPHAT, tophatKernel);

        // Threshold to binary
        Mat marksMask = new Mat();
        threshold(tophat, marksMask, 0, 255, THRESH_BINARY | THRESH_OTSU);

        // Constrain to road area
        bitwise_and(marksMask, roadMask, marksMask);

        return marksMask;
    }

    private double estimateHeading(Mat enhanced, Mat roadMask) {
        // Detect edges
        Mat edges = new Mat();
        Canny(enhanced, edges, 50, 150);
        
        // Mask edges to road area
        bitwise_and(edges, roadMask, edges);

        // Hough lines to find dominant direction
        Vec4iVector lines = new Vec4iVector();
        HoughLinesP(edges, lines, 1, Math.PI / 180, 50, 50, 10);

        if (lines.size() == 0) {
            return 0.0; // Default to horizontal
        }

        // Average the angles
        double sumTheta = 0;
        int count = 0;
        for (long i = 0; i < Math.min(lines.size(), 10); i++) {
            int x1 = (int)lines.get(i).get(0);
            int y1 = (int)lines.get(i).get(1);
            int x2 = (int)lines.get(i).get(2);
            int y2 = (int)lines.get(i).get(3);
            
            double theta = Math.atan2(y2 - y1, x2 - x1);
            sumTheta += theta;
            count++;
        }

        return count > 0 ? sumTheta / count : 0.0;
    }

    private Mat growBands(Mat marksMask, double theta, double ppm) {
        int H = marksMask.rows();
        int W = marksMask.cols();
        int maxDim = Math.max(H, W);

        // Calculate kernel dimensions
        int kernelLength = (int)(maxDim * config.getKernel().getLengthFrac());
        
        // Get lane width from rules if available
        double laneWidthM = ruleValidator.getLaneWidthFromRules(rules);
        int kernelThickness = (int)(laneWidthM * ppm * 0.3); // Use 30% of lane width
        
        if (kernelThickness < 3) kernelThickness = 3;
        if (kernelThickness % 2 == 0) kernelThickness++; // Make odd

        // Create rotated kernel
        Mat kernel = createRotatedKernel(kernelLength, kernelThickness, theta);

        // Anisotropic dilation
        Mat bands = new Mat();
        dilate(marksMask, bands, kernel);

        // Additional morphological operations
        Mat closeKernel = getStructuringElement(MORPH_RECT, new Size(15, 15));
        morphologyEx(bands, bands, MORPH_CLOSE, closeKernel);
        
        medianBlur(bands, bands, 7);

        return bands;
    }

    private Mat createRotatedKernel(int length, int thickness, double theta) {
        // Create a horizontal line kernel
        Mat kernel = Mat.zeros(thickness, length, CV_8U).asMat();
        rectangle(kernel, new Rect(0, 0, length, thickness), 
                 new Scalar(255, 0, 0, 0), -1, LINE_8, 0);

        // Rotate the kernel
        Point2f center = new Point2f(length / 2.0f, thickness / 2.0f);
        Mat rotMatrix = getRotationMatrix2D(center, Math.toDegrees(theta), 1.0);
        
        Mat rotatedKernel = new Mat();
        warpAffine(kernel, rotatedKernel, rotMatrix, kernel.size());

        // Threshold to binary
        threshold(rotatedKernel, rotatedKernel, 127, 255, THRESH_BINARY);

        return rotatedKernel;
    }

    private List<PolygonDto> findPolygons(Mat bands, Mat roadMask, Mat enhanced, 
                                          Mat marksMask, double ppm) {
        List<PolygonDto> polygons = new ArrayList<>();
        
        int H = bands.rows();
        int W = bands.cols();
        double minArea = config.getMinAreaFrac() * H * W;

        // Find contours
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(bands, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

        for (long i = 0; i < contours.size(); i++) {
            Mat contour = contours.get(i);
            double area = contourArea(contour);

            // Filter by area
            if (area < minArea) continue;

            // Check road overlap
            if (!checkRoadOverlap(contour, roadMask)) continue;

            // Get bounding rectangle
            RotatedRect rr = minAreaRect(contour);
            
            // Extract points
            Point2f vertices = new Point2f(4);
            rr.points(vertices);
            
            List<int[]> points = new ArrayList<>();
            for (int j = 0; j < 4; j++) {
                points.add(new int[]{
                    (int)vertices.position(j).x(),
                    (int)vertices.position(j).y()
                });
            }

            // Extract features
            Map<String, Double> features = extractFeatures(rr, contour, marksMask, ppm);

            // Validate against rules
            List<String> ruleIds = ruleValidator.validatePolygon(features, rules);

            // Determine type based on features
            String type = determineType(features, ruleIds);

            polygons.add(new PolygonDto(type, points, area, features, ruleIds));
        }

        return polygons;
    }

    private boolean checkRoadOverlap(Mat contour, Mat roadMask) {
        // Create mask from contour
        Mat contourMask = Mat.zeros(roadMask.size(), CV_8U).asMat();
        MatVector contourVec = new MatVector(contour);
        drawContours(contourMask, contourVec, -1, new Scalar(255, 0, 0, 0), -1, LINE_8, new Mat(), 0, new Point());

        // Calculate intersection
        Mat intersection = new Mat();
        bitwise_and(contourMask, roadMask, intersection);

        double intersectionArea = countNonZero(intersection);
        double contourArea = countNonZero(contourMask);

        return (intersectionArea / contourArea) >= config.getRoadOverlapMin();
    }

    private Map<String, Double> extractFeatures(RotatedRect rr, Mat contour, 
                                                Mat marksMask, double ppm) {
        Map<String, Double> features = new HashMap<>();

        // Basic dimensions
        double width = Math.min(rr.size().width(), rr.size().height());
        double length = Math.max(rr.size().width(), rr.size().height());

        features.put("width_m", width / ppm);
        features.put("length_m", length / ppm);
        features.put("area_m2", contourArea(contour) / (ppm * ppm));

        // For broken lines, extract stroke and gap patterns
        // This is simplified - in production, implement proper line profiling
        features.put("stroke_m", 2.0); // Placeholder
        features.put("gap_m", 6.0); // Placeholder
        features.put("continuous_m", length / ppm);

        return features;
    }

    private String determineType(Map<String, Double> features, List<String> ruleIds) {
        // Determine polygon type based on features and matched rules
        if (!ruleIds.isEmpty()) {
            String firstRule = ruleIds.get(0);
            if (firstRule.contains("zebra")) return "zebra_crossing";
            if (firstRule.contains("stop")) return "stop_line";
            if (firstRule.contains("give_way")) return "give_way";
            if (firstRule.contains("solid")) return "solid_line";
            if (firstRule.contains("broken")) return "broken_line";
            if (firstRule.contains("edge")) return "edge_line";
        }

        // Default based on dimensions
        double widthM = features.getOrDefault("width_m", 0.0);
        double lengthM = features.getOrDefault("length_m", 0.0);
        
        if (lengthM > 10 && widthM < 5) {
            return "lane_leg";
        } else if (widthM > 2 && lengthM < 10) {
            return "transverse_marking";
        }
        
        return "unknown";
    }

    private Mat keepLargeComponents(Mat mask, double minComponentArea) {
        MatVector contours = new MatVector();
        Mat hierarchy = new Mat();
        findContours(mask.clone(), contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

        Mat result = Mat.zeros(mask.size(), CV_8U).asMat();
        
        for (long i = 0; i < contours.size(); i++) {
            double area = contourArea(contours.get(i));
            if (area >= minComponentArea) {
                MatVector singleContour = new MatVector(contours.get(i));
                drawContours(result, singleContour, -1, new Scalar(255, 0, 0, 0), -1, LINE_8, new Mat(), 0, new Point());
            }
        }

        return result;
    }

    private byte[] drawOverlay(Mat gray, List<PolygonDto> polygons) {
        // Convert to color for visualization
        Mat vis = new Mat();
        cvtColor(gray, vis, COLOR_GRAY2BGR);

        // Draw each polygon in red
        for (PolygonDto polygon : polygons) {
            List<int[]> points = polygon.points();
            if (points.size() >= 3) {
                for (int i = 0; i < points.size(); i++) {
                    int j = (i + 1) % points.size();
                    line(vis, 
                         new Point(points.get(i)[0], points.get(i)[1]),
                         new Point(points.get(j)[0], points.get(j)[1]),
                         new Scalar(0, 0, 255, 0), 2, LINE_8, 0); // Red color
                }
            }
        }

        return matToPng(vis);
    }

    private byte[] matToPng(Mat mat) {
        BytePointer pngData = new BytePointer();
        imencode(".png", mat, pngData);
        byte[] pngBytes = new byte[(int) pngData.capacity()];
        pngData.get(pngBytes);
        return pngBytes;
    }

    public List<Rule> getRules() {
        if (rules == null) {
            rules = ruleSuiteLoader.loadDefaultRules();
        }
        return rules;
    }
    
    public OverlayResult processWithMaskRCNN(byte[] pngBytes, MaskRCNNResponse maskrcnnResponse) {
        // Decode image
        Mat image = imdecode(new Mat(pngBytes), IMREAD_COLOR);
        if (image.empty()) {
            throw new RuntimeException("Failed to decode image");
        }
        
        // Convert MaskRCNN polygons to PolygonDto format
        List<PolygonDto> polygons = new ArrayList<>();
        
        for (MaskRCNNPolygonDTO maskPolygon : maskrcnnResponse.polygons()) {
            // Apply rule validation if enabled
            List<String> appliedRules = new ArrayList<>();
            boolean isValid = true;
            
            if (maskPolygon.isValidLaneMarking()) {
                // Run basic rule validation
                if (maskPolygon.widthM() >= 0.08 && maskPolygon.widthM() <= 0.20) {
                    appliedRules.add("EU_LANE_WIDTH_STANDARD");
                }
                if (maskPolygon.areaM2() >= 0.1) {
                    appliedRules.add("MINIMUM_AREA_THRESHOLD");
                }
            }
            
            Map<String, Double> features = new HashMap<>();
            features.put("area_m2", maskPolygon.areaM2());
            features.put("width_m", maskPolygon.widthM());
            features.put("perimeter_m", maskPolygon.perimeterM());
            features.put("score", maskPolygon.score());
            
            PolygonDto polygonDto = new PolygonDto(
                maskPolygon.getMarkingType(),
                maskPolygon.points(),
                maskPolygon.areaPx(),
                features,
                appliedRules
            );
            
            polygons.add(polygonDto);
        }
        
        // Draw polygons on overlay
        Mat overlay = image.clone();
        
        for (PolygonDto polygon : polygons) {
            // Draw simple circle markers for detected polygons
            for (int[] point : polygon.points()) {
                circle(overlay, new Point(point[0], point[1]), 5, new Scalar(0, 0, 255, 0), -1, LINE_8, 0);
            }
            
            // Draw polygon outline
            for (int i = 0; i < polygon.points().size(); i++) {
                int[] p1 = polygon.points().get(i);
                int[] p2 = polygon.points().get((i + 1) % polygon.points().size());
                line(overlay, new Point(p1[0], p1[1]), new Point(p2[0], p2[1]), 
                     new Scalar(0, 0, 255, 0), 2, LINE_8, 0);
            }
        }
        
        // Encode result using existing method
        byte[] overlayBytes = matToPng(overlay);
        
        // Cleanup
        image.release();
        overlay.release();
        
        return new OverlayResult(polygons, overlayBytes);
    }

    // ============ PNG-Only Processing Methods (New) ============
    
    /**
     * Process PNG image with optional segmentation mask
     * Main PNG processing pipeline as specified in Png_try.md
     */
    public OverlayResult processPng(byte[] pngBytes, double ppm, Optional<byte[]> segmentationMask, boolean debug) {
        // Load rules if not already loaded
        if (rules == null) {
            rules = ruleSuiteLoader.loadDefaultRules();
        }

        // Decode PNG to grayscale
        Mat buf = new Mat(1, pngBytes.length, CV_8U);
        buf.data().put(pngBytes);
        Mat src = imdecode(buf, IMREAD_GRAYSCALE);
        if (src == null || src.empty()) {
            throw new IllegalArgumentException("Invalid PNG image");
        }

        // 1) Preprocess with CLAHE
        Mat enhanced = applyCLAHE(src);

        // 2) Road mask (asphalt darker): Otsu inverse + morph operations
        Mat roadMask = buildRoadMask(enhanced);

        // 3) Markings mask: Top-hat + Otsu + constrain to road
        Mat marksMask = buildMarksMask(enhanced, roadMask);

        // 3b) Fuse with segmentation mask if provided
        if (segmentationMask.isPresent()) {
            Mat probMask = decodeProbabilityMask(segmentationMask.get());
            if (probMask != null) {
                // Threshold probability mask
                Mat segBin = new Mat();
                threshold(probMask, segBin, 128, 255, THRESH_BINARY);
                
                // Fuse with classical markings (logical max)
                max(marksMask, segBin, marksMask);
                
                segBin.release();
                probMask.release();
            }
        }

        // 4) Estimate dominant heading for anisotropic kernel
        double theta = estimateHeading(enhanced, roadMask);

        // 5) Anisotropic dilation to grow dashed lines into continuous bands
        Mat bands = growBands(marksMask, theta, ppm);

        // 6) Find contours, filter by area & road-overlap, simplify
        List<PolygonDto> polygons = findPolygons(bands, roadMask, enhanced, marksMask, ppm);

        // 7) RAG validation and classification already handled in findPolygons

        // 8) Draw red overlay
        byte[] overlayPng = drawOverlay(src, polygons);

        // Cleanup
        src.release();
        enhanced.release();
        roadMask.release();
        marksMask.release();
        bands.release();
        buf.release();

        return new OverlayResult(polygons, overlayPng);
    }

    /**
     * Process PNG and return polygons only (no overlay image)
     */
    public List<PolygonDto> polygonsOnly(byte[] pngBytes, double ppm, Optional<byte[]> segmentationMask) {
        OverlayResult result = processPng(pngBytes, ppm, segmentationMask, false);
        return result.polygons();
    }

    /**
     * Process PNG with debug mode - returns intermediate processing frames
     */
    public DebugFrames processWithDebug(byte[] pngBytes, double ppm) {
        // Load rules if not already loaded
        if (rules == null) {
            rules = ruleSuiteLoader.loadDefaultRules();
        }

        // Decode PNG
        Mat buf = new Mat(1, pngBytes.length, CV_8U);
        buf.data().put(pngBytes);
        Mat src = imdecode(buf, IMREAD_GRAYSCALE);
        
        // Process with intermediate steps
        Mat enhanced = applyCLAHE(src);
        Mat roadMask = buildRoadMask(enhanced);
        Mat marksMask = buildMarksMask(enhanced, roadMask);
        double theta = estimateHeading(enhanced, roadMask);
        Mat bands = growBands(marksMask, theta, ppm);
        List<PolygonDto> polygons = findPolygons(bands, roadMask, enhanced, marksMask, ppm);
        
        // Convert to PNG bytes for debug output
        byte[] roadPng = matToPng(roadMask);
        byte[] marksPng = matToPng(marksMask);
        byte[] bandsPng = matToPng(bands);
        byte[] overlayPng = drawOverlay(src, polygons);
        
        // Cleanup
        src.release();
        enhanced.release();
        roadMask.release();
        marksMask.release();
        bands.release();
        buf.release();
        
        return new DebugFrames(roadPng, marksPng, bandsPng, overlayPng);
    }

    /**
     * Decode probability mask from segmentation service
     */
    private Mat decodeProbabilityMask(byte[] maskBytes) {
        try {
            Mat buf = new Mat(1, maskBytes.length, CV_8U);
            buf.data().put(maskBytes);
            Mat probMask = imdecode(buf, IMREAD_GRAYSCALE);
            buf.release();
            return probMask;
        } catch (Exception e) {
            return null;
        }
    }
}