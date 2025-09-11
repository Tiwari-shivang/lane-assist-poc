package com.example.lanes.api;

import com.example.lanes.core.LanePolygonService;
import com.example.lanes.core.OverlayResult;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/lanes")
@RequiredArgsConstructor
public class LaneController {

  private final LanePolygonService service;

  /**
   * Accepts an aerial or LiDAR intensity image and returns the same image with polygon overlays (PNG).
   * Curl: curl -F image=@/path/frame.png http://localhost:8080/api/lanes/overlay -o overlay.png
   */
  @PostMapping(value = "/overlay", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<byte[]> overlay(@RequestParam("image") MultipartFile image) throws Exception {
    OverlayResult res = service.process(image.getBytes());

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.IMAGE_PNG);
    // Optional: pass polygons as JSON in a header for convenience.
    headers.add("X-Polygons", res.polygonJson());

    return ResponseEntity.ok().headers(headers).body(res.pngBytes());
  }

  /**
   * Returns polygons as JSON only (no image).
   * Curl: curl -F image=@/path/frame.png http://localhost:8080/api/lanes/polygons
   */
  @PostMapping(value = "/polygons", consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
               produces = MediaType.APPLICATION_JSON_VALUE)
  public String polygons(@RequestParam("image") MultipartFile image) throws Exception {
    return service.process(image.getBytes()).polygonJson();
  }
}