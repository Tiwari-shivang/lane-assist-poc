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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/polygons")
@RequiredArgsConstructor
public class PolygonController {

    private final LanePolygonService lanePolygonService;
    private final PolygonConfig polygonConfig;
    private final MaskRCNNInferenceService maskRCNNService;

    @PostMapping(value = "/overlay", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> overlay(
            @RequestParam("data") MultipartFile data,
            @RequestParam(name = "resolution", defaultValue = "0.20") double resolution,
            @RequestParam(name = "debug", defaultValue = "false") boolean debug) throws Exception {
        
        File tmp = File.createTempFile("in_", null);
        data.transferTo(tmp);

        File png;
        double ppm;
        String name = data.getOriginalFilename().toLowerCase();

        if (name.endsWith(".laz") || name.endsWith(".las")) {
            LidarPreprocessor.PreResult pr = LidarPreprocessor.lazToPng(tmp, resolution);
            png = pr.png();
            ppm = 1.0 / resolution;
        } else {
            png = tmp;
            ppm = polygonConfig.getPpm();
        }

        byte[] pngBytes = java.nio.file.Files.readAllBytes(png.toPath());
        OverlayResult out = lanePolygonService.process(pngBytes, ppm);
        
        tmp.delete();
        if (!png.equals(tmp)) {
            png.delete();
        }
        
        return ResponseEntity.ok()
            .contentType(MediaType.IMAGE_PNG)
            .body(out.pngBytes());
    }

    @PostMapping(value = "", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public List<PolygonDto> polygons(
            @RequestParam("data") MultipartFile data,
            @RequestParam(name = "resolution", defaultValue = "0.20") double resolution) throws Exception {
        
        File tmp = File.createTempFile("in_", null);
        data.transferTo(tmp);

        File png;
        double ppm;
        String name = data.getOriginalFilename().toLowerCase();

        if (name.endsWith(".laz") || name.endsWith(".las")) {
            LidarPreprocessor.PreResult pr = LidarPreprocessor.lazToPng(tmp, resolution);
            png = pr.png();
            ppm = 1.0 / resolution;
        } else {
            png = tmp;
            ppm = polygonConfig.getPpm();
        }

        byte[] pngBytes = java.nio.file.Files.readAllBytes(png.toPath());
        OverlayResult out = lanePolygonService.process(pngBytes, ppm);
        
        tmp.delete();
        if (!png.equals(tmp)) {
            png.delete();
        }
        
        return out.polygons();
    }

    @PostMapping(value = "/debug", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = "application/zip")
    public ResponseEntity<byte[]> debug(
            @RequestParam("data") MultipartFile data,
            @RequestParam(name = "resolution", defaultValue = "0.20") double resolution) throws Exception {
        
        File tmp = File.createTempFile("in_", null);
        data.transferTo(tmp);

        File png;
        String name = data.getOriginalFilename().toLowerCase();

        if (name.endsWith(".laz") || name.endsWith(".las")) {
            LidarPreprocessor.PreResult pr = LidarPreprocessor.lazToPng(tmp, resolution);
            png = pr.png();
        } else {
            png = tmp;
        }

        byte[] pngBytes = java.nio.file.Files.readAllBytes(png.toPath());
        DebugFrames frames = lanePolygonService.processWithDebug(pngBytes);
        
        tmp.delete();
        if (!png.equals(tmp)) {
            png.delete();
        }
        
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
    
    // MaskRCNN Endpoints
    
    @PostMapping(value = "/maskrcnn", consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public MaskRCNNResponse maskrcnnInference(
            @RequestParam("data") MultipartFile data,
            @RequestParam(name = "resolution", defaultValue = "0.20") double resolution,
            @RequestParam(name = "min_area", defaultValue = "200") int minArea) throws Exception {
        
        File tmp = File.createTempFile("in_", null);
        data.transferTo(tmp);

        File png;
        double ppm;
        String name = data.getOriginalFilename().toLowerCase();

        if (name.endsWith(".laz") || name.endsWith(".las")) {
            LidarPreprocessor.PreResult pr = LidarPreprocessor.lazToPng(tmp, resolution);
            png = pr.png();
            ppm = 1.0 / resolution;
        } else {
            png = tmp;
            ppm = polygonConfig.getPpm();
        }

        MaskRCNNResponse response = maskRCNNService.inferPolygons(png, ppm, minArea);
        
        tmp.delete();
        if (!png.equals(tmp)) {
            png.delete();
        }
        
        return response;
    }
    
    @PostMapping(value = "/maskrcnn/overlay", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<byte[]> maskrcnnOverlay(
            @RequestParam("data") MultipartFile data,
            @RequestParam(name = "resolution", defaultValue = "0.20") double resolution,
            @RequestParam(name = "min_area", defaultValue = "200") int minArea,
            @RequestParam(name = "validate_rules", defaultValue = "true") boolean validateRules) throws Exception {
        
        File tmp = File.createTempFile("in_", null);
        data.transferTo(tmp);

        File png;
        double ppm;
        String name = data.getOriginalFilename().toLowerCase();

        if (name.endsWith(".laz") || name.endsWith(".las")) {
            LidarPreprocessor.PreResult pr = LidarPreprocessor.lazToPng(tmp, resolution);
            png = pr.png();
            ppm = 1.0 / resolution;
        } else {
            png = tmp;
            ppm = polygonConfig.getPpm();
        }

        // Get MaskRCNN polygons
        MaskRCNNResponse maskrcnnResponse = maskRCNNService.inferPolygons(png, ppm, minArea);
        
        // Convert to traditional pipeline for overlay rendering and rule validation
        byte[] pngBytes = java.nio.file.Files.readAllBytes(png.toPath());
        OverlayResult overlayResult;
        
        if (validateRules) {
            // Use traditional pipeline with RAG validation
            overlayResult = lanePolygonService.process(pngBytes, ppm);
        } else {
            // Use MaskRCNN results directly for overlay
            overlayResult = lanePolygonService.processWithMaskRCNN(pngBytes, maskrcnnResponse);
        }
        
        tmp.delete();
        if (!png.equals(tmp)) {
            png.delete();
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
}