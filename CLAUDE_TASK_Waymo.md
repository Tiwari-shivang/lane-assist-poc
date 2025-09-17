# Claude Task Brief — Waymo TFRecord → LiDAR/BEV Extraction + Spring Polygonizer Integration
*Updated: 2025-09-17*


This brief tells Claude exactly what to build so our Spring **Polygon Generator** can use **Waymo TFRecords** directly. The deliverable is a set of small Python tools + docs that export **LiDAR point clouds** and **BEV intensity PNGs**, which we then send to our Spring **/api/polygons/overlay** endpoint.

---

## 1) Objectives (must have)
1. **Export per‑frame LiDAR point clouds** from a `*.tfrecord` into **PLY** (and optionally LAZ via PDAL).
2. **Generate BEV intensity images** (grayscale PNG, e.g., 0.20 m/px) from LiDAR, including a sidecar JSON with **ppm = 1 / resolution**.
3. **(Optional)** Extract **camera PNGs** from frames.
4. Provide **CLI scripts** with clear arguments and a **README** showing end‑to‑end usage, including calling our Spring endpoint.

---

## 2) Project layout (create in repo root)
```
tools/
  extract_lidar_to_ply.py
  make_bev_intensity.py
  extract_camera_pngs.py
  requirements.txt
docs/
  WAYMO_EXTRACT_README.md
data/              # .gitignored; put TFRecords here
out/               # script outputs
```

Add to `.gitignore`:
```
data/*
out/*
*.tfrecord
*.tar
*.laz
```

---

## 3) Environment setup (Claude: generate exact commands)
- Create a **Python 3.10+ venv** and install:
  - `tensorflow` (CPU is fine; match the Waymo package version)
  - `waymo-open-dataset-tf-<matching TF version>`
  - `numpy`, `opencv-python`, `pillow`, `shapely`
  - Optional for LAZ: `pdal` CLI available on PATH (installed system-wide)
- Confirm with:
  ```bash
  python -c "import tensorflow as tf; print(tf.__version__)"
  python -c "import waymo_open_dataset as w; print('ok')"
  ```

> **Note:** Claude should select the correct `waymo-open-dataset` wheel for our TF version.

---

## 4) Scripts to implement

### A) `tools/extract_lidar_to_ply.py`
**Purpose:** Iterate frames in a TFRecord and write **PLY** for each frame’s LiDAR (both returns, all lidars).  
**CLI:**
```
python tools/extract_lidar_to_ply.py \
  --tfrecord data/segment-XXXX.tfrecord \
  --out out/ply_frames \
  --every 1              # export every Nth frame
```
**Behavior:**
- Uses `frame_utils.parse_range_image_and_camera_projection` + `convert_range_image_to_point_cloud`.
- Concatenates returns (ri_index 0 + 1) from all lidars.
- Writes ASCII PLY with **x y z**. (If intensity is easy to attach, include as property `intensity`).
- If `--to-laz` is set and `pdal` exists, run `pdal translate frame_xxx.ply frame_xxx.laz`.

**Acceptance:**
- First three frames produce `frame_000000.ply`, `frame_000001.ply`, `frame_000002.ply` (and `.laz` when requested).

---

### B) `tools/make_bev_intensity.py`
**Purpose:** Produce a **BEV intensity PNG** per frame (or a single composite) suitable for our Spring **/overlay** endpoint.  
**CLI:**
```
python tools/make_bev_intensity.py \
  --tfrecord data/segment-XXXX.tfrecord \
  --out out/bev \
  --res 0.20 \
  --extent -80 80 -80 80 \
  --frames 0..99         # range or 'all'
  --composite false      # if true, accumulate all frames into one PNG
```
**Behavior:**
- Extract **XYZ + intensity** (first return by default; flag to include max over returns).
- Rasterize into a grid: **resolution** (m/px) and **extent** (xmin xmax ymin ymax).
- Reducer: `max` intensity per cell (flag for `mean` as an option).
- Normalize to 8‑bit and save PNG.
- Write `metadata.json` with `{{ "resolution_m_per_px": 0.20, "ppm": 5.0, "extent": [xmin,xmax,ymin,ymax] }}`.

**Acceptance:**
- Creates `frame_000000.png` and `metadata.json`. PPM equals `1 / res`.

---

### C) (Optional) `tools/extract_camera_pngs.py`
**Purpose:** Save 5 camera images per frame to PNG for cross‑checking or training RGB models.  
**CLI:**
```
python tools/extract_camera_pngs.py \
  --tfrecord data/segment-XXXX.tfrecord \
  --out out/camera_pngs \
  --every 5
```
**Behavior:** Iterate frames; for each `Frame.images`, decode and write `cam<name>_<frame>.png`.

---

## 5) Example usage (end‑to‑end)

```bash
# 1) Export a few point clouds
python tools/extract_lidar_to_ply.py --tfrecord data/segment-0000.tfrecord --out out/ply --every 10

# 2) Make BEV intensity PNGs at 0.20 m/px
python tools/make_bev_intensity.py --tfrecord data/segment-0000.tfrecord --out out/bev --res 0.20 --extent -80 80 -80 80 --frames 0..50

# 3) Send a BEV to Spring polygonizer
curl -X POST "http://localhost:8080/api/polygons/overlay" \
  -F "data=@out/bev/frame_000010.png" -o overlay.png
```

---

## 6) Implementation notes (Claude: follow these)
- Prefer **TOP lidar** for BEV unless otherwise specified; allow a flag to include all lidars.
- Intensity channel is available in Waymo range images; ensure proper decoding and flattening.
- For BEV normalization, use OpenCV `normalize(..., 0, 255, NORM_MINMAX)` → `uint8`.
- Keep functions small: `read_frames`, `points_with_intensity`, `rasterize_bev`, `save_png_with_meta`.
- Add `--composite` mode (accumulate multiple frames using `np.maximum` reducer).
- Include simple progress logging and basic error handling.

---

## 7) README to generate (`docs/WAYMO_EXTRACT_README.md`)
- Environment steps.
- Command examples above.
- Notes on **ppm = 1 / resolution** for our downstream RAG/polygon metrics.
- Troubleshooting: TF/Waymo wheel mismatch; memory use; PDAL not found.

---

## 8) Definition of Done (DoD)
- All three scripts run against a sample TFRecord and produce expected outputs.
- `WAYMO_EXTRACT_README.md` documents setup and commands.
- Outputs are consumable by our Spring endpoints and match the expected format.
- CI step (optional): lint + a tiny smoke test on a 1‑frame TFRecord sample (if available).

---

## 9) Stretch goals (nice to have)
- Batch over a directory of TFRecords.  
- Save **GeoTIFF** instead of PNG (preserve scale/extent).  
- Add a flag to **accumulate N frames** into a higher‑quality BEV.  
- Simple unit tests for `rasterize_bev` and metadata.

---

## 10) Hand‑off back to Spring
- Spring already accepts PNG via `/api/polygons/overlay`.  
- For LAZ/PLY: keep extracting tools here; polygonizer stays image‑based.  
- RAG remains unchanged (uses returned `ppm` in JSON/metadata).

---

**Please proceed and generate the scripts + README as specified above.**
