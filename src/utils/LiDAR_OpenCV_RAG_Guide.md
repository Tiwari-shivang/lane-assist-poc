# LiDAR Lane & Junction Polygonization — OpenCV + RAG (Spring Boot, Java 17)
*Updated: 2025-09-15*

> **What you’ll build:** A monolithic Spring Boot service that ingests a **LiDAR intensity** image of roads/highways and returns (1) the **same image** with **red polygons** around lane legs/bands and the junction core, and (2) an optional **JSON** payload describing those polygons and their rule-based classifications (zebra, stop line, give‑way triangles, etc.).\
> The output style matches the example below.

![Example overlay](./lidar_overlay_example.png)

---

## 0) TL;DR Runbook

```bash
# 1) Build & run
./mvnw -DskipTests package
java -jar target/lanes-*.jar

# 2) Produce an overlaid image
curl -X POST "http://localhost:8080/api/lanes/overlay" \
  -H "Content-Type: image/png" \
  --data-binary @sample_lidar.png \
  -o overlay.png

# 3) Or get polygons as JSON (no image)
curl -X POST "http://localhost:8080/api/lanes/polygons" \
  -H "Content-Type: image/png" \
  --data-binary @sample_lidar.png | jq .
```

**Inputs:** LiDAR **intensity** (grayscale) PNG/TIFF/JPEG.  
**Outputs:** `overlay.png` with **red polygons** + optional `polygons.json` with points/area/type/rule IDs.

---

## 1) Project Layout (monolith)

```
com.example.lanes
  ├─ api/                  # REST controllers: /overlay, /polygons, /debug, /validate
  ├─ core/                 # OpenCV pipeline: masks → bands → contours → polygons
  ├─ rag/                  # Rules YAML loader + tiny rule checker
  ├─ model/                # DTOs: OverlayResult, PolygonDto, DebugFrames
  └─ config/               # Tunables (ppm, thresholds, kernels, flags)
```

---

## 2) Dependencies

**Maven (use latest stable versions from Maven Central):**
```xml
<dependencies>
  <!-- OpenCV via Bytedeco -->
  <dependency>
    <groupId>org.bytedeco</groupId>
    <artifactId>opencv-platform</artifactId>
    <version>${opencv.version}</version>
  </dependency>

  <!-- Spring Boot web -->
  <dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
  </dependency>

  <!-- YAML (rules loader) -->
  <dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-yaml</artifactId>
  </dependency>

  <!-- (Optional) GeoTIFF metadata/CRS if you want ppm from georeferencing -->
  <!-- <dependency>
    <groupId>org.geotools</groupId>
    <artifactId>gt-geotiff</artifactId>
    <version>${geotools.version}</version>
  </dependency> -->
</dependencies>
```
In your parent POM properties, add a version property:
```xml
<properties>
  <java.version>17</java.version>
  <opencv.version>4.8.0-1.5.9</opencv.version> <!-- or newer -->
</properties>
```

---

## 3) Configuration (`application.yaml`)

```yaml
lanes:
  useVision: false             # LiDAR-only (no OCR)
  ppm: 5.0                     # pixels-per-meter; set from dataset or GeoTIFF
  minAreaFrac: 0.0015          # min contour area as fraction of H*W
  epsilonFrac: 0.012           # approxPolyDP fraction (shape simplification)
  roadOverlapMin: 0.65         # polygon must lie mostly on road mask
  kernel:
    lengthFrac: 0.08           # rotated dilation kernel length ≈ 6–10% of max(H,W)
    thickFrac: 0.02            # band thickness; may be overridden by lane width rule
  tophat:
    kernelPx: 21               # 17–31 typical (odd only)
  debugFrames: true            # enable /debug ZIP with masks & overlay
```

---

## 4) OpenCV Pipeline (LiDAR-friendly)

**Input → CLAHE → masks → bands → polygons**

1. **Road mask (`road`)**
   - Otsu **inverse** threshold (asphalt darker than background).
   - Morphology: `CLOSE(9×9)` then `OPEN(5×5)`.
   - Keep **large components** only to drop speckle/vegetation.

2. **Markings mask (`marks`)**
   - **Top-hat** on grayscale (kernel 17–31 px) to boost bright paint.
   - Otsu binary, then **AND** with `road` to confine to asphalt.

3. **Dominant direction (`theta`)**
   - `Canny` edges → `HoughLines` on `edges ∧ road` to estimate axis.

4. **Anisotropic growth → lane **bands****
   - Build rotated **long‑thin kernel** at `theta` (length ≈ 6–10% max(H,W)).
   - Set thickness from `lanes.kernel.thickFrac` or **RAG lane width × ppm**.
   - `dilate(marks, k_rot)` → `CLOSE(15×15)` → `medianBlur(7)`.

5. **Contours → polygons**
   - `findContours(..., RETR_EXTERNAL)`
   - Filter by **area** (`minAreaFrac * H*W`) and **overlap** with `road` (≥ `roadOverlapMin`).
   - **Lane legs:** `minAreaRect` → `boxPoints` → red rectangles.
   - **Junction core:** cluster union around skeleton node → `convexHull` + `approxPolyDP`.

6. **(Optional) Node split**
   - Skeletonize `road` to find **junction node** (degree ≥ 3).
   - Use watershed/erosion to split bands cleanly around the node.

**Why this works for LiDAR intensity:** paint is brighter than asphalt; top‑hat + anisotropic dilation grows dashed strokes into continuous **bands** that capture a full lane leg as a single polygon.

---

## 5) Feature Extraction (meters)

Let `ppm` be pixels-per-meter (from config or GeoTIFF). For each `RotatedRect rr`:
```
length_m = max(rr.w, rr.h) / ppm
width_m  = min(rr.w, rr.h) / ppm
```
**Broken vs solid lines:** sample a 1‑px profile **along** the band axis on `marks`; run‑length encode **stroke** vs **gap** to measure `stroke_length_m`, `gap_m`.  
**Zebra:** sample several perpendicular profiles to estimate median **stripe**/**gap** widths and `crossing_width_m`.  
**Give‑way triangles:** `approxPolyDP` small clusters; compute **base**/**height**.

---

## 6) RAG = Retrieval‑Augmented **Detection**
Rules live in YAML and are retrieved to (a) **parameterize** detection and (b) **validate/classify** polygons post‑hoc.

### 6.1 Rules YAML (example)
`resources/rules/eu_lane_rules.yaml`
```yaml
- id: lane_guidance_broken
  tags: [lane, broken]
  params:
    lane_width_m: [2.8, 4.0]      # used to set dilation thickness if present
    stroke_m:   [1.0, 3.0]
    gap_m:      [3.0, 12.0]
    line_width_m: [0.10, 0.15]
  checks:
    - "width_m >= 0.10"
    - "gap_m / stroke_m >= 2 and gap_m / stroke_m <= 4"

- id: solid_line
  tags: [lane, solid]
  params:
    line_width_m: [0.10, 0.20]
  checks:
    - "width_m >= 0.10"
    - "continuous_m >= 20"

- id: zebra_crossing
  tags: [zebra]
  params:
    stripe_gap_period_m: [0.80, 1.40]
    crossing_width_m: [2.5, 5.0]
  checks:
    - "(stripe_m + gap_m) >= 0.80 and (stripe_m + gap_m) <= 1.40"
    - "abs(stripe_m - gap_m) / max(stripe_m, gap_m) <= 0.25"
    - "crossing_width_m >= 2.5"
```

### 6.2 Rule Engine (tiny)
- Load YAML → build a map of rule **params** and string **checks**.
- Compute a **feature map** for each polygon: `width_m`, `length_m`, `stroke_m`, `gap_m`, `continuous_m`, etc.
- Evaluate check expressions (e.g., SpEL or a simple custom evaluator).

**Where rules plug in**
1. **Before detection:** if `lane_width_m` exists → set dilation **thickness = lane_width_m × ppm × factor**.
2. **After polygonization:** score polygons against rules → assign `type` and `rule_ids`.

---

## 7) REST API

### `POST /api/lanes/overlay` → `image/png`
- **Request:** body = binary grayscale image (PNG/JPEG/TIFF).
- **Response:** image with **red polygons** overlaid.
- **Query params (optional):** `ppm`, `debug=true` to override config or export frames.

### `POST /api/lanes/polygons` → `application/json`
- **Request:** same as above.
- **Response:** list of polygons + features + rule results.
```json
[
  {
    "type": "lane_leg",
    "rule_ids": ["lane_guidance_broken"],
    "area_px": 14238,
    "points": [[123,456],[234,456],[234,567],[123,567]],
    "features": {
      "width_m": 3.5, "length_m": 62.1, "gap_m": 7.2, "stroke_m": 2.4
    }
  }
]
```

### `POST /api/lanes/debug` → `application/zip`
- Contains `road.png`, `marks.png`, `bands.png`, `overlay.png` to help tuning.

### `POST /api/lanes/validate` → `application/json`
- Pass `{group, type, features}` to unit‑test rule checks without running CV.

---

## 8) Core Code Sketch (Java)

```java
// api/OverlayController.java
@PostMapping(value = "/api/lanes/overlay", consumes = MediaType.IMAGE_PNG_VALUE)
public ResponseEntity<byte[]> overlay(@RequestBody byte[] imageBytes) {
    OverlayResult out = lanePolygonService.process(imageBytes);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_PNG)
        .body(out.pngBytes());
}
```

```java
// core/LanePolygonService.java (sketch)
public OverlayResult process(byte[] bytes) {
    Mat gray = imdecode(new Mat(bytes), IMREAD_GRAYSCALE);
    Mat eq   = clahe(gray);

    Mat road = buildRoadMask(eq);
    Mat marks= buildMarkMask(eq, road);

    double theta = estimateHeading(eq, road);
    Mat bands = growBands(marks, theta, kernelFromRules());

    List<PolygonDto> polys = findPolygons(bands, road);
    annotateTypesWithRules(polys, featureExtractor(eq, marks));

    byte[] overlayPng = drawOverlay(eq, polys, Color.RED);
    return new OverlayResult(polys, overlayPng);
}
```

```java
// rag/RuleSuiteLoader.java (sketch)
public class RuleSuiteLoader {
  public List<Rule> load(String path) throws IOException {
     ObjectMapper yaml = new ObjectMapper(new YAMLFactory());
     return Arrays.asList(yaml.readValue(resource(path), Rule[].class));
  }
}
```

---

## 9) Tuning Cheatsheet

- **Top‑hat kernel:** 17–31 px (odd). Increase if paint is faint.
- **Rotated kernel length:** 6–10% of max(H,W).
- **Band thickness:** 1.5–2.5% of max dim **or** from `lane_width_m × ppm`.
- **Min area:** `0.001–0.003 × H×W`.
- **Road overlap:** `0.6–0.8`. Raise if off‑road detections appear.
- Merge at junctions? Shorten kernel length, add one `erode`, or use **watershed**.
- Noisy speckles? Strengthen `OPEN/CLOSE` on `road` and `marks`.

---

## 10) Performance & Scaling

- Prefer single‑channel (uint8) ops and reuse Mats to reduce GC pressure.
- Use `native` OpenCV (bytedeco) with the `platform` artifact for packaged natives.
- For high throughput: pre‑warm a small worker pool; avoid per‑request rule reloads.
- Images up to 4096×4096 are fine on commodity instances with proper GC settings.

---

## 11) Security & Licensing

- Uses **OpenCV (Bytedeco)** and **Jackson YAML** (permissive licenses).  
- **Do not** use Google Maps/Earth tiles for LiDAR rasters; source from **USGS 3DEP**, **OpenTopography**, or national portals.  
- Disable OCR unless you add RGB aerials and need word/number labels.

---

## 12) Troubleshooting

- **No polygons drawn:** verify `roadOverlapMin` not too high; check `marks.png` in /debug ZIP.
- **Over‑merged bands:** reduce rotated kernel **length**, or add `erode` before contours.
- **Broken zebra detection:** adjust `tophat.kernelPx` and ensure ppm is correct.
- **Wrong scale:** set `ppm` explicitly or read from GeoTIFF metadata.

---

## 13) Implementation Checklist

- [ ] Wire `/overlay`, `/polygons`, `/debug`, `/validate` controllers.
- [ ] Implement masks → bands → contours in `LanePolygonService`.
- [ ] Add `FeatureExtractor` for widths/lengths and line/zebra profiles.
- [ ] Load rules YAML; implement tiny evaluator.
- [ ] Overlay drawer (red polygons) and JSON DTOs.
- [ ] Unit tests with synthetic patterns (dashed, solid, zebra).

---

## 14) Appendix A — Example DTOs

```java
public record OverlayResult(List<PolygonDto> polygons, byte[] pngBytes) {}

public record PolygonDto(
   String type,
   List<int[]> points,
   double areaPx,
   Map<String, Double> features,
   List<String> ruleIds
) {}
```

---

## 15) Appendix B — Example Rule Validation Payload

```json
{
  "group": "lane",
  "type": "broken",
  "features": {
    "width_m": 0.12,
    "stroke_m": 2.4,
    "gap_m": 7.2,
    "crossing_width_m": 0.0
  }
}
```

---

### You’re ready to transform your project
Start by dropping this guide into your repo as `README-lidar-rag.md`, add the YAML rules, and wire the `/debug` endpoint so you can tune kernels and thresholds quickly. When you POST a LiDAR intensity image, you’ll get an **overlay with red polygons** like the example included above along with machine‑readable polygon JSON for downstream systems.
