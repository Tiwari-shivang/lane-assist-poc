# Polygon Generator for Road LiDAR — LAZ Pre‑processing + OpenCV Overlay (Spring Boot, Java 17)
*Updated: 2025-09-16*

This doc revises the app context: we are building a **Polygon Generator** service. Users upload **road LiDAR data** (either **LAZ/LAS point clouds** or **pre‑rasterized intensity images**). The service returns:
1) A **PNG** overlay with **red polygons** outlining **lane legs/bands** and **junction core** (optionally crosswalks/stop lines/etc.).  
2) A **JSON** array of polygons (points, area, type, rule IDs, features).

---

## High‑Level Flow

```
Input (LAZ/LAS or grayscale PNG/TIFF)
      └─ if LAZ/LAS: PDAL → Intensity GeoTIFF/PNG (BEV raster)
             └─ gdal_translate (8‑bit PNG, scaled)
                  └─ OpenCV pipeline → masks → bands → polygons
                       ├─ Overlay drawer → red polygons (PNG)
                       └─ Polygon JSON + rule classifications (RAG)
```

**Pixels‑per‑meter (ppm):** If rasterized at `R` meters/pixel, then `ppm = 1 / R`. Example: `0.20 m/px → ppm = 5`.

---

## Endpoints

1) **POST `/api/polygons/overlay`** → `image/png`  
   - **Consumes:** `multipart/form-data` with file `data` (either `.laz/.las` or `png/tif/jpg`).  
   - **Query:** `resolution` (for LAZ → raster, default `0.20` m/px), `debug` (bool).  
   - **Produces:** PNG with red polygons.

2) **POST `/api/polygons`** → `application/json`  
   - Same input; returns polygon list + features + rule IDs (no image).

3) **POST `/api/polygons/debug`** → `application/zip`  
   - Exports intermediate frames: `road.png`, `marks.png`, `bands.png`, `overlay.png`.

---

## System Requirements

- Java 17, Spring Boot 3.x
- **OpenCV** (via Bytedeco `opencv-platform`)
- **PDAL** and **GDAL** available on PATH (server or Docker)
  - Ubuntu example:
    ```bash
    sudo apt-get update
    sudo apt-get install -y pdal gdal-bin
    pdal --version && gdalinfo --version
    ```

> Docker users: install PDAL/GDAL in the image, or add a sidecar. A minimal Debian/Ubuntu base with `pdal gdal-bin` is fine.

---

## LAZ → Intensity Raster (PDAL)

### Bash (one‑liner used by the service)
```bash
pdal translate "{input}" "{tif}" writers.gdal   --writers.gdal.dimension=Intensity   --writers.gdal.output_type=max   --writers.gdal.resolution={resolution_m_per_px}   --writers.gdal.gdaldriver=GTiff   --writers.gdal.nodata=0
gdal_translate -of PNG -ot Byte -scale "{tif}" "{png}"
```

- Use `output_type=max` (or `mean`) to reduce multiple points into a pixel.
- If point cloud lacks a metric CRS, first add a **reprojection** filter to a UTM EPSG.

**Optional JSON pipeline (with reprojection & ground Z range):**
```json
[
  {"type":"readers.las","filename":"INPUT.laz"},
  {"type":"filters.reprojection","out_srs":"EPSG:32633"},
  {"type":"filters.range","limits":"Z[-10:200]"},
  {"type":"writers.gdal",
     "filename":"OUT.tif",
     "dimension":"Intensity",
     "output_type":"max",
     "resolution":0.20,
     "gdaldriver":"GTiff",
     "nodata":0
  }
]
```

---

## Spring Boot Integration (Controller + Pre‑processor)

### Multipart Controller (detect LAZ vs image)
```java
// api/PolygonController.java
@PostMapping(value = "/api/polygons/overlay", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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
        // LAZ → intensity.png via PDAL/GDAL
        LidarPreprocessor.PreResult pr = LidarPreprocessor.lazToPng(tmp, resolution);
        png = pr.png();
        ppm = 1.0 / resolution;
    } else {
        // Already an image
        png = tmp;
        ppm = polygonConfig.getPpm(); // from application.yaml or query override
    }

    OverlayResult out = lanePolygonService.processImage(png, ppm, debug);
    return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(out.pngBytes());
}
```

### Pre‑processor (shells out to PDAL/GDAL)
```java
// core/LidarPreprocessor.java
public class LidarPreprocessor {
  public record PreResult(File tif, File png) { }
  public static PreResult lazToPng(File laz, double resolution) throws IOException, InterruptedException {
    File tif = File.createTempFile("pdal_", ".tif");
    File png = File.createTempFile("pdal_", ".png");

    String[] pdal = new String[] {
      "pdal","translate", laz.getAbsolutePath(), tif.getAbsolutePath(), "writers.gdal",
      "--writers.gdal.dimension=Intensity",
      "--writers.gdal.output_type=max",
      "--writers.gdal.resolution="+resolution,
      "--writers.gdal.gdaldriver=GTiff",
      "--writers.gdal.nodata=0"
    };
    run(pdal);

    String[] gdal = new String[] {
      "gdal_translate","-of","PNG","-ot","Byte","-scale",
      tif.getAbsolutePath(), png.getAbsolutePath()
    };
    run(gdal);

    return new PreResult(tif, png);
  }

  private static void run(String[] cmd) throws IOException, InterruptedException {
    Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
    try (var in = p.getInputStream()) in.transferTo(System.out);
    if (p.waitFor() != 0) throw new IOException("Command failed: "+String.join(" ", cmd));
  }
}
```

> **Security:** Only run PDAL/GDAL on trusted infrastructure; validate file extensions and size, and clean up temp files.

---

## OpenCV Polygonization (unchanged core)

- Read grayscale image (PNG/TIFF), run:
  1. **Road mask** (Otsu inverse → morph CLOSE/OPEN → large components).
  2. **Markings mask** (Top‑hat → Otsu → AND with road).
  3. **Heading** (Canny + HoughLines).
  4. **Anisotropic dilation** using rotated kernel (length 6–10% image dim; thickness from rules or config) → **bands**.
  5. **Contours → polygons**; filter by area and road overlap; classify via rules (RAG).

- **Outputs:** overlay PNG + `polygons.json`.  
- Set **ppm = 1 / resolution** when image came from LAZ.

---

## Configuration (`application.yaml` snippet)

```yaml
polygons:
  ppm: 5.0                # used only when input is already an image
  minAreaFrac: 0.0015
  epsilonFrac: 0.012
  roadOverlapMin: 0.65
  kernel:
    lengthFrac: 0.08
    thickFrac: 0.02
  tophat:
    kernelPx: 21
```

---

## Example cURL

**Upload LAZ (server does the rasterization):**
```bash
curl -X POST "http://localhost:8080/api/polygons/overlay?resolution=0.20"   -F "data=@aoi.laz" -o overlay.png
```

**Upload already‑rasterized intensity PNG:**
```bash
curl -X POST "http://localhost:8080/api/polygons/overlay"   -F "data=@intensity.png" -o overlay.png
```

---

## Tasks for Claude (implementation checklist)

1. **Controller** to accept `multipart/form-data` and detect LAZ vs image.  
2. **LidarPreprocessor** class (above) that shells out to PDAL/GDAL; make resolution configurable.  
3. **LanePolygonService.processImage(File image, double ppm, boolean debug)`** to run the existing OpenCV pipeline and draw red polygons.  
4. **/api/polygons** to return polygon JSON; **/api/polygons/debug** to return a ZIP with intermediate frames.  
5. Add **unit tests**: feed a small LAZ sample (or mock PNG) and assert overlay/JSON not empty.  
6. **Dockerfile** installing `pdal` and `gdal-bin`; ensure binaries on PATH.  
7. Document **ppm = 1 / resolution** mapping; add a `--ppm` query override if needed.

---

## Notes & Tips

- If intensity looks dark, adjust `gdal_translate -scale` or apply CLAHE in OpenCV.  
- For very dense LAZ, try `output_type=mean` to reduce speckle.  
- Consider a `filters.outlier` (Statistical Outlier Removal) in PDAL if needed.  
- If CRS is geographic (degrees), reproject to UTM before rasterizing.

---

## Deliverables

- Working endpoints `/api/polygons/overlay`, `/api/polygons`, `/api/polygons/debug`.
- `LidarPreprocessor` (PDAL/GDAL) and `LanePolygonService` (OpenCV).
- Example image + polygon JSON artifacts to validate the flow.
