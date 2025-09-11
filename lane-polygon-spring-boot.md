# Lane Polygon Extraction Service — Spring Boot 3.5 (Java 17, OpenCV)

> **Author’s context:** This guide is written with 20+ years in HD map & navigation systems (ADAS, lane-level guidance, in-vehicle nav). It distills production practices for extracting **lane polygons** from **aerial** or **LiDAR intensity** imagery using **OpenCV** and returning the **annotated image** from a Spring Boot service.

---

## 0) What you’ll build

A Spring Boot **REST** service that accepts an image (multipart upload), detects lane markings, **grows** them into lane-shaped regions, and returns the **same image** with **polygon overlays** (PNG). A companion endpoint can return **polygons as JSON** for downstream map tools.

---

## 1) Tech & versions

- **Java 17** (Amazon Corretto recommended)
- **Spring Boot 3.5.x**
- **OpenCV** via **Bytedeco** (`org.bytedeco:opencv-platform`) — ships native libs for Linux/macOS/Windows
- **MySQL** datasource (per requirements) — `jdbc:mysql://localhost:3306/EMS`, user `root`, **empty password**

> The DB is not needed for image processing itself but is configured to satisfy the project requirements (e.g., audit logs later).

---

## 2) Project creation

### Option A — Spring Initializr (UI)
- Project: **Maven**, Language: **Java**, Spring Boot: **3.5.x**
- Dependencies: **Spring Web**, **Spring Data JPA**, **MySQL Driver**, **Lombok** (optional)
- Generate & unzip.

### Option B — Quickstart (terminal)
```bash
curl https://start.spring.io/starter.tgz   -d bootVersion=3.5.0   -d type=maven-project   -d language=java   -d groupId=com.example   -d artifactId=lane-polygons   -d name=lane-polygons   -d dependencies=web,data-jpa,mysql,lombok   | tar -xzvf -
cd lane-polygons
```

---

## 3) `pom.xml`

Replace the generated contents as needed (ensure Java 17 & OpenCV).

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.5.0</version>
    <relativePath/>
  </parent>

  <groupId>com.example</groupId>
  <artifactId>lane-polygons</artifactId>
  <version>0.0.1-SNAPSHOT</version>
  <name>lane-polygons</name>
  <description>Lane polygon extraction with Spring Boot + OpenCV</description>

  <properties>
    <java.version>17</java.version>
    <opencv.version>4.9.0-1.5.10</opencv.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-web</artifactId>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-data-jpa</artifactId>
    </dependency>

    <dependency>
      <groupId>mysql</groupId>
      <artifactId>mysql-connector-j</artifactId>
      <scope>runtime</scope>
    </dependency>

    <!-- OpenCV with bundled native binaries -->
    <dependency>
      <groupId>org.bytedeco</groupId>
      <artifactId>opencv-platform</artifactId>
      <version>${opencv.version}</version>
    </dependency>

    <!-- Optional quality-of-life -->
    <dependency>
      <groupId>org.projectlombok</groupId>
      <artifactId>lombok</artifactId>
      <optional>true</optional>
    </dependency>

    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-test</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-compiler-plugin</artifactId>
        <version>3.11.0</version>
        <configuration>
          <release>17</release>
        </configuration>
      </plugin>
      <plugin>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-maven-plugin</artifactId>
      </plugin>
    </plugins>
  </build>
</project>
```

---

## 4) Configuration (`src/main/resources/application.yml`)

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/EMS?useSSL=false&serverTimezone=UTC
    username: root
    password: ""     # empty per requirement
  jpa:
    hibernate:
      ddl-auto: none
    properties:
      hibernate.dialect: org.hibernate.dialect.MySQLDialect
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 20MB

# Lane extraction tuning (can be changed without code)
lanes:
  minAreaFrac: 0.0015          # fraction of image area to keep a polygon
  epsilonFrac: 0.012           # poly simplify (smaller -> more points)
  laneWidthFrac: 0.015         # dilation kernel vs image size
  exportJson: true             # include polygons in overlay response (X-Polygons header)
```

---

## 5) Controller

`src/main/java/com/example/lanes/api/LaneController.java`

```java
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
```

---

## 6) Service (OpenCV pipeline)

`src/main/java/com/example/lanes/core/LanePolygonService.java`

```java
package com.example.lanes.core;

import lombok.RequiredArgsConstructor;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.opencv.opencv_core.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.bytedeco.opencv.global.opencv_imgcodecs.imdecode;
import static org.bytedeco.opencv.global.opencv_imgcodecs.imencode;
import static org.bytedeco.opencv.global.opencv_imgproc.*;

@Service
@RequiredArgsConstructor
public class LanePolygonService {

  @Value("${lanes.minAreaFrac:0.0015}") private double minAreaFrac;
  @Value("${lanes.epsilonFrac:0.012}") private double epsilonFrac;
  @Value("${lanes.laneWidthFrac:0.015}") private double laneWidthFrac;

  public OverlayResult process(byte[] imageBytes) {
    // ---- read ----
    Mat buf = new Mat(1, imageBytes.length, CvType.CV_8U);
    buf.data().put(imageBytes);
    Mat img = imdecode(buf, IMREAD_COLOR);
    if (img == null || img.empty()) throw new IllegalArgumentException("Invalid image");

    int H = img.rows(), W = img.cols();
    double minArea = minAreaFrac * H * W;

    // ---- enhance contrast (CLAHE on L channel) ----
    Mat lab = new Mat(); cvtColor(img, lab, COLOR_BGR2Lab);
    MatVector labSplit = new MatVector(3); split(lab, labSplit);
    Mat L = labSplit.get(0);
    CLAHE clahe = createCLAHE(3.0, new Size(8,8));
    Mat Lc = new Mat(); clahe.apply(L, Lc);
    labSplit.put(0, Lc);
    Mat norm = new Mat(); merge(labSplit, lab); cvtColor(lab, norm, COLOR_Lab2BGR);

    // ---- threshold lane paint (white & yellow) + tophat ----
    Mat hsv = new Mat(); cvtColor(norm, hsv, COLOR_BGR2HSV);
    Mat maskW = new Mat(); inRange(hsv, new Scalar(0,0,180,0), new Scalar(180,40,255,0), maskW);
    Mat maskY = new Mat(); inRange(hsv, new Scalar(12,60,120,0), new Scalar(40,255,255,0), maskY);
    Mat mask = new Mat(); bitwise_or(maskW, maskY, mask);

    Mat gray = new Mat(); cvtColor(norm, gray, COLOR_BGR2GRAY);
    Mat topK = getStructuringElement(MORPH_RECT, new Size(17,17));
    Mat tophat = new Mat(); morphologyEx(gray, tophat, MORPH_TOPHAT, topK);
    Mat th = new Mat(); threshold(tophat, th, 0, 255, THRESH_BINARY | THRESH_OTSU);
    bitwise_or(mask, th, mask);

    // ---- clean & grow into lane bands ----
    Mat k5 = getStructuringElement(MORPH_RECT, new Size(5,5));
    morphologyEx(mask, mask, MORPH_CLOSE, k5, new Point(-1,-1), 2);
    morphologyEx(mask, mask, MORPH_OPEN,  k5, new Point(-1,-1), 1);

    int laneWidth = Math.max(9, (int)(laneWidthFrac * Math.max(H, W)));
    Mat growK = getStructuringElement(MORPH_RECT, new Size(laneWidth, laneWidth));
    Mat grown = new Mat(); dilate(mask, grown, growK, new Point(-1,-1), 2);

    // ---- fill small holes ----
    Mat fillK = getStructuringElement(MORPH_RECT, new Size(laneWidth*2, laneWidth*2));
    Mat filled = new Mat(); morphologyEx(grown, filled, MORPH_CLOSE, fillK);
    medianBlur(filled, filled, 7);

    // ---- contours -> convex hull -> approx polygon ----
    MatVector contours = new MatVector();
    Mat hierarchy = new Mat();
    findContours(filled.clone(), contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

    List<OverlayResult.Polygon> polys = new ArrayList<>();
    Mat vis = img.clone();

    for (long i = 0; i < contours.size(); i++) {
      Mat c = contours.get(i);
      double area = contourArea(c);
      if (area < minArea) continue;

      Mat hull = new Mat(); convexHull(c, hull);
      double peri = arcLength(hull, true);
      Mat approx = new Mat();
      approxPolyDP(hull, approx, epsilonFrac * peri, true);

      // collect points
      List<int[]> pts = new ArrayList<>();
      IntPointer data = new IntPointer(approx.data());
      for (int j = 0; j < approx.rows(); j++) {
        int x = data.get(j * 2);
        int y = data.get(j * 2 + 1);
        pts.add(new int[]{x, y});
      }
      polys.add(new OverlayResult.Polygon(pts, area));

      polylines(vis, new MatVector(approx), true, new Scalar(0,0,255,0), 3);
    }

    // ---- encode overlay ----
    MatOfByte png = new MatOfByte();
    imencode(".png", vis, png);
    byte[] pngBytes = new byte[(int) png.total()];
    png.data().get(pngBytes);

    return new OverlayResult(polys, pngBytes);
  }
}
```

### Result DTO

`src/main/java/com/example/lanes/core/OverlayResult.java`

```java
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
```

---

## 7) Build & run

```bash
# Java 17 (Amazon Corretto recommended)
mvn -v        # verify Java 17 is active
mvn clean package
java -jar target/lane-polygons-0.0.1-SNAPSHOT.jar
```

Test with curl:

```bash
curl -F image=@/path/to/aerial.png http://localhost:8080/api/lanes/overlay -o overlay.png
curl -F image=@/path/to/lidar_intensity.png http://localhost:8080/api/lanes/polygons | jq .
```

---

## 8) Best practices (from production mapping)

1. **Normalize lighting:** Use **CLAHE** (already included) to stabilize lane paint contrast.
2. **Dual-threshold strategy:** Combine **HSV white/yellow** with **Top-hat** on grayscale to capture both painted lines and bright curbs.
3. **Anisotropic growth:** If roads are very skewed, estimate dominant direction with **HoughLines** and use a **rotated, long–thin kernel** for dilation so polygons stretch along the carriageway, not across it.
4. **ROI masks:** Pre-mask irrelevant regions (sky, buildings) using a hand-drawn polygon or a road prior when available; reduces false positives.
5. **Junction splitting:** When one huge blob covers a junction, cut at the neck using **distance transform + watershed** or a single erosion before polygonization.
6. **Convex first, then simplify:** `convexHull → approxPolyDP` yields clean, rectangular bands similar to cartographic lane units.
7. **Scale-aware tuning:** Make kernel sizes **relative to image size** (we do this via `laneWidthFrac`) for consistent behavior across zoom levels.
8. **Fail-soft thresholds:** Prefer Otsu/adaptive thresholds where possible; log masks to debug mis-detections.
9. **Color-space sanity:** OpenCV uses **BGR**; convert carefully when moving between RGB (web) and BGR (OpenCV).
10. **Determinism:** Fix all parameters in config for reproducible outputs across environments.
11. **Performance:** Reuse small objects (kernels), avoid accidental `clone()` calls, and keep the entire pipeline **CPU-only** for server deployments (GPU is optional for batch).
12. **AI fallback (optional):** For night/rain, add a small **lane segmentation** model (ONNX Runtime) and run the same polygonization on the predicted mask.
13. **QC hooks:** Persist polygon vertices & masks for audits; emit metrics (polygon count, total area, runtime) to spot regressions.
14. **Coordinate mapping:** If your inputs are geo-referenced, maintain an **affine** from image pixels to WGS‑84/UTM so polygons can be pushed into your map DB.
15. **Safety checks:** Reject images smaller than a threshold; cap request size; return 422 for “no lane detected”.

---

## 9) Security & Ops

- Restrict file size (`multipart.max-file-size`), content types (PNG/JPEG only).
- Timebox processing per request; use a dedicated **thread pool** for image tasks.
- If exposing externally, put behind **API Gateway** with rate limiting.
- Health endpoint: `/actuator/health` (add `spring-boot-starter-actuator` if you want).

---

## 10) Optional: Dockerfile

```dockerfile
# Use Amazon Corretto 17
FROM amazoncorretto:17-alpine

WORKDIR /app
COPY target/lane-polygons-0.0.1-SNAPSHOT.jar app.jar

# Bytedeco bundles natives; no extra apt-get needed on Alpine
EXPOSE 8080
ENTRYPOINT ["java","-jar","/app/app.jar"]
```

---

## 11) Notes for LiDAR vs Aerial

- **Aerial RGB/RGBA:** pipeline above works directly.
- **LiDAR intensity/orthophoto:** if single-channel, load/convert to BGR before HSV. For hillshade-like images, rely more on **tophat + Otsu** and reduce yellow thresholds.
- **Point clouds:** rasterize intensity/reflectance first (GDAL/PDAL), then feed the raster into this service.

---

## 12) API contract summary

- `POST /api/lanes/overlay` — `multipart/form-data` (`image`); **returns** `image/png` with drawn polygons. Header `X-Polygons` contains JSON array.
- `POST /api/lanes/polygons` — `multipart/form-data` (`image`); **returns** `application/json` list of polygons:  
  `[{ "points": [[x,y], ...], "area": 12345.6 }]`

---

## 13) Troubleshooting

- **UnsatisfiedLinkError:** Ensure `opencv-platform` dependency is present (it bundles natives). On exotic distros, set `-Djava.library.path` only if you use non-platform artifacts.
- **All-black or all-white masks:** Tune HSV thresholds; visualize intermediate masks during dev.
- **Huge merged polygons:** Lower `laneWidthFrac` or erode once before `findContours`.
- **No lanes found:** Check exposure; consider adaptive thresholding and increase the tophat kernel.

---

## 14) Next steps

- Persist polygon features to the `EMS` DB for later analytics.
- Add a `/debug` mode to return intermediate masks.
- Add ONNX Runtime for ML fallback when needed.

---

**You’re set.** Build, run, and point Claude (or any codegen) at this document to scaffold the Spring project and wire in OpenCV.
