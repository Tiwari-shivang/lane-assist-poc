package com.example.lanes.api;

import com.example.lanes.config.PolygonConfig;
import com.example.lanes.core.LanePolygonService;
import com.example.lanes.core.LidarPreprocessor;
import com.example.lanes.core.OverlayResult;
import com.example.lanes.dto.MaskRCNNResponse;
import com.example.lanes.dto.MaskRCNNPolygonDTO;
import com.example.lanes.model.DebugFrames;
import com.example.lanes.model.PolygonDto;
import com.example.lanes.service.MaskRCNNInferenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/polygons")
@RequiredArgsConstructor
public class PolygonController {

    private final LanePolygonService lanePolygonService;
    private final PolygonConfig polygonConfig;
    private final MaskRCNNInferenceService maskRCNNService;

    /**
     * Process PNG image and return overlay with red polygons
     * Supports both raw PNG body and multipart form data
     */
    @PostMapping(value = "/overlay", consumes = {MediaType.IMAGE_PNG_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<byte[]> overlay(
            HttpServletRequest request,
            @RequestParam(value = "ppm", required = false) Double ppmParam,
            @RequestParam(value = "debug", defaultValue = "false") boolean debug,
            @RequestPart(value = "data", required = false) MultipartFile data) throws Exception {
        
        // Get PNG bytes from either raw body or multipart
        byte[] pngBytes;
        if (data != null) {
            pngBytes = data.getBytes();
        } else {
            pngBytes = request.getInputStream().readAllBytes();
        }
        
        // Use provided ppm or default
        double ppm = ppmParam != null ? ppmParam : polygonConfig.getDefaultPpm();
        
        // Optional MaskRCNN segmentation (can be integrated later)
        Optional<byte[]> segmentationMask = Optional.empty();
        if (maskRCNNService.isServiceHealthy()) {
            try {
                // Get segmentation mask from MaskRCNN service if available
                segmentationMask = maskRCNNService.getSegmentationMask(pngBytes);
            } catch (Exception e) {
                // Continue without segmentation if service fails
            }
        }
        
        // Process the PNG
        OverlayResult out = lanePolygonService.processPng(pngBytes, ppm, segmentationMask, debug);
        
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(out.pngBytes());
    }

    /**
     * Process PNG image and return polygon JSON data only
     * Supports both raw PNG body and multipart form data
     */
    @PostMapping(value = "", consumes = {MediaType.IMAGE_PNG_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE}, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<PolygonDto> polygons(
            HttpServletRequest request,
            @RequestParam(value = "ppm", required = false) Double ppmParam,
            @RequestPart(value = "data", required = false) MultipartFile data) throws Exception {
        
        // Get PNG bytes from either raw body or multipart
        byte[] pngBytes;
        if (data != null) {
            pngBytes = data.getBytes();
        } else {
            pngBytes = request.getInputStream().readAllBytes();
        }
        
        // Use provided ppm or default
        double ppm = ppmParam != null ? ppmParam : polygonConfig.getDefaultPpm();
        
        // Optional MaskRCNN segmentation
        Optional<byte[]> segmentationMask = Optional.empty();
        if (maskRCNNService.isServiceHealthy()) {
            try {
                segmentationMask = maskRCNNService.getSegmentationMask(pngBytes);
            } catch (Exception e) {
                // Continue without segmentation
            }
        }
        
        // Process and return polygons only
        return lanePolygonService.polygonsOnly(pngBytes, ppm, segmentationMask);
    }

    /**
     * Debug endpoint - returns ZIP with intermediate processing images
     */
    @PostMapping(value = "/debug", consumes = {MediaType.IMAGE_PNG_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE}, produces = "application/zip")
    public ResponseEntity<byte[]> debug(
            HttpServletRequest request,
            @RequestParam(value = "ppm", required = false) Double ppmParam,
            @RequestPart(value = "data", required = false) MultipartFile data) throws Exception {
        
        // Get PNG bytes from either raw body or multipart
        byte[] pngBytes;
        if (data != null) {
            pngBytes = data.getBytes();
        } else {
            pngBytes = request.getInputStream().readAllBytes();
        }
        
        // Use provided ppm or default
        double ppm = ppmParam != null ? ppmParam : polygonConfig.getDefaultPpm();
        
        // Process with debug mode to get intermediate frames
        DebugFrames frames = lanePolygonService.processWithDebug(pngBytes, ppm);
        
        // Create ZIP with all debug images
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            addToZip(zos, "road.png", frames.roadMask());
            addToZip(zos, "marks.png", frames.marksMask());
            addToZip(zos, "bands.png", frames.bandsMask());
            addToZip(zos, "overlay.png", frames.overlayImage());
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/zip"));
        headers.setContentDispositionFormData("attachment", "debug_frames.zip");
        
        return ResponseEntity.ok().headers(headers).body(baos.toByteArray());
    }
    
    private void addToZip(ZipOutputStream zos, String filename, byte[] content) throws IOException {
        ZipEntry entry = new ZipEntry(filename);
        zos.putNextEntry(entry);
        zos.write(content);
        zos.closeEntry();
    }
    
    // ============ MaskRCNN Endpoints (Enhanced) ============
    
    @PostMapping(value = "/maskrcnn", consumes = {MediaType.IMAGE_PNG_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE}, produces = MediaType.APPLICATION_JSON_VALUE)
    public MaskRCNNResponse maskrcnnInference(
            HttpServletRequest request,
            @RequestParam(value = "ppm", required = false) Double ppmParam,
            @RequestParam(name = "min_area", defaultValue = "200") int minArea,
            @RequestPart(value = "data", required = false) MultipartFile data) throws Exception {
        
        // Get PNG bytes
        byte[] pngBytes;
        if (data != null) {
            pngBytes = data.getBytes();
        } else {
            pngBytes = request.getInputStream().readAllBytes();
        }
        
        double ppm = ppmParam != null ? ppmParam : polygonConfig.getDefaultPpm();
        
        // Save to temp file for MaskRCNN processing
        File tmp = File.createTempFile("maskrcnn_", ".png");
        java.nio.file.Files.write(tmp.toPath(), pngBytes);
        
        MaskRCNNResponse response = maskRCNNService.inferPolygons(tmp, ppm, minArea);
        
        tmp.delete();
        
        return response;
    }
    
    @PostMapping(value = "/maskrcnn/overlay", consumes = {MediaType.IMAGE_PNG_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity<byte[]> maskrcnnOverlay(
            HttpServletRequest request,
            @RequestParam(value = "ppm", required = false) Double ppmParam,
            @RequestParam(name = "min_area", defaultValue = "200") int minArea,
            @RequestParam(name = "validate_rules", defaultValue = "true") boolean validateRules,
            @RequestPart(value = "data", required = false) MultipartFile data) throws Exception {
        
        // Get PNG bytes
        byte[] pngBytes;
        if (data != null) {
            pngBytes = data.getBytes();
        } else {
            pngBytes = request.getInputStream().readAllBytes();
        }
        
        double ppm = ppmParam != null ? ppmParam : polygonConfig.getDefaultPpm();
        
        // Save to temp file for MaskRCNN
        File tmp = File.createTempFile("maskrcnn_", ".png");
        java.nio.file.Files.write(tmp.toPath(), pngBytes);
        
        // Get MaskRCNN polygons
        MaskRCNNResponse maskrcnnResponse = maskRCNNService.inferPolygons(tmp, ppm, minArea);
        
        tmp.delete();
        
        // Generate overlay
        OverlayResult overlayResult;
        
        if (validateRules) {
            // Use traditional pipeline with RAG validation
            Optional<byte[]> segMask = Optional.empty();
            overlayResult = lanePolygonService.processPng(pngBytes, ppm, segMask, false);
        } else {
            // Use MaskRCNN results directly for overlay
            overlayResult = lanePolygonService.processWithMaskRCNN(pngBytes, maskrcnnResponse);
        }
        
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .header("X-MaskRCNN-Detections", String.valueOf(maskrcnnResponse.totalDetections()))
            .header("X-Valid-Lane-Markings", String.valueOf(maskrcnnResponse.getValidLaneMarkings().size()))
            .body(overlayResult.pngBytes());
    }
    
    @GetMapping("/maskrcnn/health")
    public ResponseEntity<String> maskrcnnHealth() {
        boolean healthy = maskRCNNService.isServiceHealthy();
        return healthy ? 
            ResponseEntity.ok("MaskRCNN inference service is healthy") :
            ResponseEntity.status(503).body("MaskRCNN inference service is not available");
    }
    
    /**
     * Legacy endpoint for LAZ files (backwards compatibility)
     */
    @PostMapping(value = "/overlay/laz", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> overlayLaz(
            @RequestParam("data") MultipartFile data,
            @RequestParam(name = "resolution", defaultValue = "0.20") double resolution) throws Exception {
        
        File tmp = File.createTempFile("laz_", null);
        data.transferTo(tmp);
        
        // Convert LAZ to PNG
        LidarPreprocessor.PreResult pr = LidarPreprocessor.lazToPng(tmp, resolution);
        File png = pr.png();
        double ppm = 1.0 / resolution;
        
        byte[] pngBytes = java.nio.file.Files.readAllBytes(png.toPath());
        
        Optional<byte[]> segMask = Optional.empty();
        OverlayResult out = lanePolygonService.processPng(pngBytes, ppm, segMask, false);
        
        tmp.delete();
        png.delete();
        
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(out.pngBytes());
    }
}