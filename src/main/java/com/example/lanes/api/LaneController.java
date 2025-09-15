package com.example.lanes.api;

import com.example.lanes.core.LanePolygonService;
import com.example.lanes.core.OverlayResult;
import com.example.lanes.model.DebugFrames;
import com.example.lanes.model.PolygonDto;
import com.example.lanes.model.ValidationRequest;
import com.example.lanes.rag.RuleValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@RestController
@RequestMapping("/api/lanes")
@RequiredArgsConstructor
public class LaneController {

  private final LanePolygonService service;
  private final RuleValidator ruleValidator;

  /**
   * Accepts an aerial or LiDAR intensity image and returns the same image with polygon overlays (PNG).
   * Supports both multipart form and raw binary image data.
   */
  @PostMapping(value = "/overlay", consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.IMAGE_PNG_VALUE, "image/jpeg", "image/tiff"})
  public ResponseEntity<byte[]> overlay(
      @RequestParam(value = "image", required = false) MultipartFile image,
      @RequestBody(required = false) byte[] imageBytes,
      @RequestParam(value = "ppm", required = false) Double ppm,
      @RequestParam(value = "debug", defaultValue = "false") boolean debug) throws Exception {
    
    byte[] inputBytes = image != null ? image.getBytes() : imageBytes;
    OverlayResult res = service.process(inputBytes, ppm);

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_PNG);
    headers.add("X-Polygons", res.polygonJson());

    return ResponseEntity.ok().headers(headers).body(res.pngBytes());
  }

  /**
   * Returns polygons as JSON only (no image).
   * Supports both multipart form and raw binary image data.
   */
  @PostMapping(value = "/polygons", 
               consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.IMAGE_PNG_VALUE, "image/jpeg", "image/tiff"},
               produces = MediaType.APPLICATION_JSON_VALUE)
  public List<PolygonDto> polygons(
      @RequestParam(value = "image", required = false) MultipartFile image,
      @RequestBody(required = false) byte[] imageBytes,
      @RequestParam(value = "ppm", required = false) Double ppm) throws Exception {
    
    byte[] inputBytes = image != null ? image.getBytes() : imageBytes;
    return service.process(inputBytes, ppm).polygons();
  }
  
  /**
   * Returns debug frames as a ZIP file containing road mask, marks mask, bands, and overlay.
   */
  @PostMapping(value = "/debug", 
               consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.IMAGE_PNG_VALUE},
               produces = "application/zip")
  public ResponseEntity<byte[]> debug(
      @RequestParam(value = "image", required = false) MultipartFile image,
      @RequestBody(required = false) byte[] imageBytes) throws Exception {
    
    byte[] inputBytes = image != null ? image.getBytes() : imageBytes;
    DebugFrames frames = service.processWithDebug(inputBytes);
    
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
  
  /**
   * Validates features against rules without running CV.
   */
  @PostMapping(value = "/validate", 
               consumes = MediaType.APPLICATION_JSON_VALUE,
               produces = MediaType.APPLICATION_JSON_VALUE)
  public List<String> validate(@RequestBody ValidationRequest request) {
    return ruleValidator.validatePolygon(request.features(), service.getRules());
  }
  
  private void addToZip(ZipOutputStream zos, String filename, byte[] content) throws IOException {
    ZipEntry entry = new ZipEntry(filename);
    zos.putNextEntry(entry);
    zos.write(content);
    zos.closeEntry();
  }
}