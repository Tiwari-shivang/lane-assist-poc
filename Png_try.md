# PNG‑Only Update — Polygon Generator with RAG + OpenCV (+ Mask R‑CNN optional)
*Updated: 2025-09-17*

**Goal:** Accept a **PNG** road/highway image (LiDAR intensity or RGB), and return a **PNG** with **red polygons** around lane legs/bands and the junction core — like your second reference image. Optionally return `polygons.json`. No LAZ/TFRecord required.

---

## 1) What changes
- **Input simplified:** only **PNG/TIFF/JPEG**; no point‑cloud pre‑processing.  
- **Endpoints:** keep the same URLs but accept raw images and/or multipart.
  - `POST /api/polygons/overlay` → **image/png** (red polygons drawn)  
  - `POST /api/polygons` → **application/json** (polygon list)  
  - `POST /api/polygons/debug` → **application/zip** (road.png, marks.png, bands.png, overlay.png)
- **RAG stays on:** rules drive thickness/filters and validate polygon types.  
- **Mask R‑CNN (optional):** segmentation model to pre‑highlight markings; OpenCV still converts them to polygons; RAG validates.

**Scale & RAG:** If you want **strict EU rule checks in meters**, pass `ppm` (pixels‑per‑meter) in the query. If `ppm` is omitted, RAG runs in **pixel/ratio mode** (uses relative thresholds + heuristics).

---

## 2) High‑level flow (PNG → PNG)
```
[Input PNG]
   ├─ (optional) Mask R‑CNN → marking mask (probability)
   ├─ OpenCV pipeline → road mask + markings mask → anisotropic dilation → bands
   ├─ Contours → polygons → simplify → classify via RAG
   ├─ Draw red polygons on the original image
   └─ Return overlay.png  (+ polygons.json if requested)
```

---

## 3) API contract (unchanged URLs)

### 3.1 `/api/polygons/overlay`
- **Consumes:** either `image/png` **or** `multipart/form-data` with `data=<file>`  
- **Query:** `ppm` *(optional, double)* — pixels per meter; `debug` *(optional, boolean)*  
- **Produces:** `image/png`

**cURL (raw PNG body):**
```bash
curl -X POST "http://localhost:8080/api/polygons/overlay?ppm=5"   -H "Content-Type: image/png" --data-binary @input.png -o overlay.png
```

**cURL (multipart):**
```bash
curl -X POST "http://localhost:8080/api/polygons/overlay?ppm=5"   -F "data=@input.png" -o overlay.png
```

### 3.2 `/api/polygons` (JSON only)
```bash
curl -X POST "http://localhost:8080/api/polygons?ppm=5"   -H "Content-Type: image/png" --data-binary @input.png
```

### 3.3 `/api/polygons/debug`
Returns `road.png`, `marks.png`, `bands.png`, `overlay.png` in a ZIP for tuning.

---

## 4) Spring Boot changes (code sketches)

### 4.1 Controller (accept raw or multipart, call pipeline)
```java
// api/PolygonController.java
@RestController
public class PolygonController {
  private final LanePolygonService lanePolygonService;
  private final SegmentationClient segmentationClient; // can be a no-op if disabled
  private final PolygonConfig polygonConfig;

  public PolygonController(LanePolygonService svc, SegmentationClient seg, PolygonConfig cfg) {
    this.lanePolygonService = svc; this.segmentationClient = seg; this.polygonConfig = cfg;
  }

  @PostMapping(value="/api/polygons/overlay", consumes={MediaType.IMAGE_PNG_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
  public ResponseEntity<byte[]> overlay(HttpServletRequest req,
      @RequestParam(value="ppm", required=false) Double ppmParam,
      @RequestParam(value="debug", defaultValue="false") boolean debug,
      @RequestPart(value="data", required=false) MultipartFile data) throws Exception {
    byte[] pngBytes;
    if (data != null) {
      pngBytes = data.getBytes();
    } else {
      pngBytes = req.getInputStream().readAllBytes();
    }
    double ppm = ppmParam != null ? ppmParam : polygonConfig.getDefaultPpm();

    // Optional Mask R-CNN: returns probability mask the same size as image (or null if disabled)
    Optional<byte[]> probMask = segmentationClient.segment(pngBytes);

    OverlayResult out = lanePolygonService.processPng(pngBytes, ppm, probMask, debug);
    return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(out.pngBytes());
  }

  @PostMapping(value="/api/polygons", consumes=MediaType.IMAGE_PNG_VALUE, produces=MediaType.APPLICATION_JSON_VALUE)
  public PolygonResponse polygons(@RequestBody byte[] pngBytes,
      @RequestParam(value="ppm", required=false) Double ppmParam) throws Exception {
    double ppm = ppmParam != null ? ppmParam : polygonConfig.getDefaultPpm();
    Optional<byte[]> probMask = segmentationClient.segment(pngBytes);
    return lanePolygonService.polygonsOnly(pngBytes, ppm, probMask);
  }
}
```

### 4.2 Service (OpenCV pipeline + optional mask)
```java
// core/LanePolygonService.java (high-level pseudo-code)
public OverlayResult processPng(byte[] png, double ppm, Optional<byte[]> segProbOpt, boolean debug) {
  Mat src = Imgcodecs.imdecode(new MatOfByte(png), Imgcodecs.IMREAD_GRAYSCALE);

  // 1) Preprocess
  Mat eq = clahe(src, 2.0);

  // 2) Road mask (asphalt darker): Otsu inverse → morph close/open → keep large components
  Mat road = roadMask(eq);

  // 3) Markings mask
  Mat marks = markingMask(eq);               // Top-hat → Otsu
  Core.bitwise_and(marks, road, marks);      // keep paint on road

  // 3b) If segmentation provided, fuse: marks = max(marks, prob>t)
  if (segProbOpt.isPresent()) {
    Mat prob = Imgcodecs.imdecode(new MatOfByte(segProbOpt.get()), Imgcodecs.IMREAD_GRAYSCALE);
    Mat segBin = new Mat(); Imgproc.threshold(prob, segBin, 128, 255, Imgproc.THRESH_BINARY);
    Core.max(marks, segBin, marks);
  }

  // 4) Dominant heading (Canny + HoughLines) to build rotated kernel
  double theta = estimateHeading(eq, road);

  // 5) Anisotropic dilation to grow dashed into continuous lane bands
  Mat bands = growBands(marks, theta, ppm);

  // 6) Contours → polygons; filter by area & road-overlap; simplify
  List<Polygon> polys = findAndFilterPolygons(bands, road, ppm);

  // 7) Feature extraction and RAG validation/classification
  List<LabeledPolygon> labeled = ragValidate(polys, ppm);

  // 8) Draw overlay (red) and optionally save debug frames
  byte[] overlayPng = drawOverlay(png, labeled);
  DebugFrames dbg = debug ? exportDebug(road, marks, bands, overlayPng) : null;

  return new OverlayResult(labeled, overlayPng, dbg);
}
```

### 4.3 Mask R‑CNN client (optional)
```java
// core/SegmentationClient.java (interface)
public interface SegmentationClient {
  Optional<byte[]> segment(byte[] png); // returns grayscale prob-mask PNG or empty if disabled
}

// impl using FastAPI service at INFER_URL=/infer returning { "mask_png_b64": "..." }
```

---

## 5) OpenCV pipeline knobs (PNG scale)
- `tophat.kernelPx`: 17–31 (odd); increase if paint is faint.
- Rotated kernel: `lengthFrac` 0.06–0.10 of max(H,W); thickness from `lane_width_px`.
  - If `ppm` provided: `lane_width_px = (lane_width_m) × ppm`.  
  - Else: estimate `lane_width_px` by sampling median stripe thickness in `marks`.
- Filters: `minAreaFrac` 0.001–0.003; `roadOverlapMin` 0.6–0.8.
- `epsilonFrac` (poly simplification): 0.010–0.015 × perimeter.

---

## 6) RAG (works with or without ppm)
- **With `ppm`:** convert EU metric rules (e.g., stripe 3m, gap 9m, width ≥ 0.10m) to pixels using `px = m × ppm` and evaluate strictly.  
- **Without `ppm`:** evaluate **ratios** and **relative** checks (e.g., `gap/stroke ∈ [2,4]`, stripe≈gap, aspect ratios), and mark measurements in **pixels** in the JSON.

**Response JSON example:**
```json
{
  "type": "lane_band",
  "points": [[x1,y1],...],
  "score": 0.92,
  "features": {"width_px": 24, "length_px": 1460, "gap_to_stroke": 3.1},
  "rule_ids": ["lane_guidance_broken_v1"],
  "ppm": 5.0
}
```

---

## 7) Configuration (`application.yaml`)
```yaml
polygons:
  defaultPpm: 5.0           # used if caller doesn't pass ?ppm=
  minAreaFrac: 0.0015
  epsilonFrac: 0.012
  roadOverlapMin: 0.65
  kernel:
    lengthFrac: 0.08
    thickFrac: 0.02
  tophat:
    kernelPx: 21
  segmentation:
    enabled: false          # turn on when Mask R-CNN service is deployed
    url: http://inference:8000/infer
```

---

## 8) How Mask R‑CNN slots in (optional)
- Train on your PNGs (BEV LiDAR or RGB) with **Detectron2 Mask R‑CNN**.  
- Service returns a **probability mask** (same size as input).  
- The pipeline **fuses** it with the classical `marks` (logical max) → cleaner, more complete bands.  
- Everything downstream (polygons, RAG, overlay) remains unchanged.

**FastAPI contract (example):**
```
POST /infer  (multipart file: 'file')
→ { "mask_png_b64": "<...>" }
```

---

## 9) Testing checklist (for the “input PNG → red‑polygon PNG” goal)
- [ ] Submit the **first reference PNG** to `/overlay` → visually matches the **second reference** (lane legs + junction core).  
- [ ] `/polygons` returns a non‑empty set with reasonable widths/lengths.  
- [ ] With and without `?ppm=` produce polygons; with `ppm` → stricter RAG typing.  
- [ ] `debug.zip` contains interpretable `road.png`, `marks.png`, `bands.png`, `overlay.png`.  
- [ ] Throughput meets needs (e.g., ≥ 5 images/sec on target box).

---

## 10) Troubleshooting
- **Too many/merged polygons:** decrease kernel `lengthFrac` or add one `erode` before finding contours.
- **Missing dashed lines:** increase `tophat.kernelPx` and/or band thickness; if using Mask R‑CNN, lower score threshold.
- **RAG rejects true positives:** wrong scale — pass a sensible `ppm` or relax width bounds to pixel mode.

---

## 11) Deliverables for this PNG‑only update
- Updated **controller** to accept raw image bytes and multipart.
- **LanePolygonService** modifications to accept optional segmentation mask.
- Config + RAG rule mapping for pixel/ratio mode.
- A short README note explaining how to call `/overlay` with PNG and how to interpret outputs.
