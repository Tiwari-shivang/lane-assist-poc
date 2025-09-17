# Deep Learning in Spring: Mask R-CNN (Detectron2) Polygon Generator
*Updated: 2025-09-17*


This document is a **handoff-ready, step-by-step guide** to add a **Mask R-CNN** (Detectron2) instance‑segmentation pipeline to your **Spring Boot** polygon generator application. It covers training on **LiDAR-derived intensity images (BEV)** or **RGB**, exporting the model, and integrating inference into your Java service to produce **polygon overlays + JSON** (and still use your **RAG** rules for standards-aware validation).

---

## 0) TL;DR (what you’ll build)

- **Training**: Detectron2 **Mask R‑CNN** on COCO‑format annotations (classes: lane_leg, junction_core, zebra_crossing, stop_line, arrow, …).
- **Serving**: Two options
  1. **Python microservice (FastAPI)** wrapping Detectron2 → returns **polygons JSON**.
  2. **Java (ONNX Runtime)** to run an exported ONNX model → post‑process masks → polygons.
- **Spring endpoint**: accepts **LAZ/LAS or PNG/TIF**; if LAZ → rasterize to **intensity PNG** (PDAL/GDAL), send to inference, run **RAG** checks, draw **red polygons**, return **overlay.png** + polygon JSON.

---

## 1) Prerequisites

- **Spring Boot 3.x, Java 17** (your existing project).
- **Python 3.10+** on a training/serving box with CUDA (for GPU) or CPU.
- **Detectron2** (PyTorch), **OpenCV**, **Shapely**.
- For LiDAR: **PDAL** and **GDAL** to convert `.laz/.las` → **intensity BEV raster**.
- Optional: **Docker**/**docker-compose** for one‑command bring‑up.

---

## 2) Data preparation

### 2.1 If starting from LiDAR (.laz/.las)
1. **Rasterize intensity** (meters/pixel → BEV image):
   ```bash
   pdal translate aoi.laz intensity.tif writers.gdal \
     --writers.gdal.dimension=Intensity \
     --writers.gdal.output_type=max \
     --writers.gdal.resolution=0.20 \
     --writers.gdal.gdaldriver=GTiff \
     --writers.gdal.nodata=0
   gdal_translate -of PNG -ot Byte -scale intensity.tif intensity.png
   ```
2. Record **ppm (pixels per meter)**: `ppm = 1 / resolution`. Example: 0.20 m/px → `ppm = 5`.

### 2.2 Labels (COCO Instances format)
- Annotate instance masks or polygons for: `lane_leg`, `junction_core`, `zebra_crossing`, `stop_line`, `arrow`.
- Use **CVAT**, **Labelme**, or similar; export to **COCO** (polygons).
- Directory shape:
  ```
  dataset/
    train/images/*.png
    train/annotations.json  # COCO
    val/images/*.png
    val/annotations.json
  ```

---

## 3) Train Mask R‑CNN (Detectron2)

### 3.1 Install (training box)
```bash
python -m venv venv && source venv/bin/activate
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121  # choose your CUDA/CPU wheel
pip install opencv-python shapely pycocotools
pip install 'git+https://github.com/facebookresearch/detectron2.git'
```

### 3.2 Minimal training script (`train.py`)
```python
import os, detectron2
from detectron2.config import get_cfg
from detectron2.engine import DefaultTrainer
from detectron2.data.datasets import register_coco_instances
from detectron2 import model_zoo

# Register datasets
register_coco_instances("markings_train", {}, "dataset/train/annotations.json", "dataset/train/images")
register_coco_instances("markings_val", {}, "dataset/val/annotations.json", "dataset/val/images")

cfg = get_cfg()
cfg.merge_from_file(model_zoo.get_config_file("COCO-InstanceSegmentation/mask_rcnn_R_50_FPN_3x.yaml"))
cfg.MODEL.WEIGHTS = model_zoo.get_checkpoint_url("COCO-InstanceSegmentation/mask_rcnn_R_50_FPN_3x.yaml")
cfg.DATALOADER.NUM_WORKERS = 4
cfg.SOLVER.IMS_PER_BATCH = 2
cfg.SOLVER.BASE_LR = 0.00025
cfg.SOLVER.MAX_ITER = 30000               # adjust to your dataset
cfg.MODEL.ROI_HEADS.NUM_CLASSES = 5       # lane_leg, junction_core, zebra_crossing, stop_line, arrow
cfg.INPUT.MIN_SIZE_TRAIN = (800, 1024, 1200)  # multi-scale for thin structures
cfg.INPUT.MAX_SIZE_TRAIN = 1600
cfg.INPUT.MIN_SIZE_TEST = 1024
cfg.INPUT.FORMAT = "BGR"                 # if grayscale, stack to 3 channels
cfg.OUTPUT_DIR = "out"

os.makedirs(cfg.OUTPUT_DIR, exist_ok=True)
trainer = DefaultTrainer(cfg)
trainer.resume_or_load(resume=False)
trainer.train()
```

> **Tip for LiDAR intensity (grayscale):** load your PNG as 1‑channel and **stack** to 3 channels during loader/augmentations, or modify the backbone’s first conv to 1 channel.

### 3.3 Checkpoints
- After training, you’ll have `out/model_final.pth`. Keep `config.yaml` used for training.

---

## 4) Export & Serving Choices

You have **two** integration paths. Pick **A (microservice)** first—it’s simpler and production‑friendly. Use **B (ONNX in Java)** if you must keep everything in JVM.

### A) Python inference microservice (FastAPI)
- **Pros:** fastest to integrate; reuse Detectron2 directly; easy GPU support.
- **Cons:** another service to deploy.

**Install:**
```bash
pip install fastapi uvicorn shapely opencv-python torch torchvision detectron2
```

**`serve.py` (minimal):**
```python
import io, json, cv2, numpy as np
from fastapi import FastAPI, File, UploadFile
from fastapi.responses import JSONResponse
from detectron2.engine import DefaultPredictor
from detectron2.config import get_cfg
from detectron2 import model_zoo
from shapely.geometry import Polygon, mapping

app = FastAPI()

cfg = get_cfg()
cfg.merge_from_file(model_zoo.get_config_file(
    "COCO-InstanceSegmentation/mask_rcnn_R_50_FPN_3x.yaml"))
cfg.MODEL.WEIGHTS = "out/model_final.pth"   # your trained weights
cfg.MODEL.ROI_HEADS.NUM_CLASSES = 5
cfg.MODEL.ROI_HEADS.SCORE_THRESH_TEST = 0.5
predictor = DefaultPredictor(cfg)

@app.post("/infer")
async def infer(file: UploadFile = File(...), ppm: float = 5.0):
    img_bytes = await file.read()
    img_array = np.frombuffer(img_bytes, dtype=np.uint8)
    img = cv2.imdecode(img_array, cv2.IMREAD_GRAYSCALE)
    img = np.dstack([img]*3)   # stack grayscale to 3ch

    outputs = predictor(img)
    inst = outputs["instances"].to("cpu")
    masks = inst.pred_masks.numpy()
    classes = inst.pred_classes.numpy()
    scores = inst.scores.numpy()

    results = []
    for m, cls, score in zip(masks, classes, scores):
        m8 = (m.astype(np.uint8) * 255)
        cnts, _ = cv2.findContours(m8, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        for c in cnts:
            if cv2.contourArea(c) < 200: 
                continue
            eps = 0.012 * cv2.arcLength(c, True)
            approx = cv2.approxPolyDP(c, eps, True).reshape(-1,2)
            results.append({
               "class_id": int(cls),
               "score": float(score),
               "points": approx.tolist(),
               "area_px": float(cv2.contourArea(approx)),
               "ppm": ppm
            })
    return JSONResponse(content={"polygons": results})
```

**Run:**
```bash
uvicorn serve:app --host 0.0.0.0 --port 8000
```

**Docker (GPU optional):**
```dockerfile
FROM pytorch/pytorch:2.3.0-cuda12.1-cudnn8-runtime
RUN pip install fastapi uvicorn shapely opencv-python pycocotools \
    && pip install 'git+https://github.com/facebookresearch/detectron2.git'
WORKDIR /app
COPY out/model_final.pth /app/out/model_final.pth
COPY serve.py /app/serve.py
EXPOSE 8000
CMD ["uvicorn","serve:app","--host","0.0.0.0","--port","8000"]
```

### B) Java with ONNX Runtime (advanced)
- Export to **ONNX** (use `torch.onnx.export` with dynamic axes).
- In Java, load the model with **onnxruntime** and post‑process outputs to masks → polygons (OpenCV).

**Gradle dependency:**
```gradle
implementation "com.microsoft.onnxruntime:onnxruntime:1.18.0"
```

**Java sketch:**
```java
try (OrtEnvironment env = OrtEnvironment.getEnvironment();
     OrtSession.SessionOptions opts = new OrtSession.SessionOptions();
     OrtSession session = env.createSession("maskrcnn.onnx", opts)) {
   // Preprocess PNG -> float tensor [1,3,H,W] normalized like training
   // Run session.run(...) -> get boxes/masks tensors
   // Convert masks -> contours -> polygons (OpenCV) like your current code
}
```

> ONNX for Mask R‑CNN is feasible but export & post‑processing is more involved. Prefer **microservice A** unless JVM‑only is required.

---

## 5) Spring Boot integration

### 5.1 Endpoint flow
```
/api/polygons/overlay (multipart)
   ├── if file is .laz/.las → LidarPreprocessor (PDAL+GDAL) → intensity.png; ppm = 1 / resolution
   ├── else assume PNG/TIF (grayscale)
   ├── call inference microservice /infer?ppm=...
   ├── run RAG checks on returned polygons (widths, dash/gap, zebra periodicity)
   ├── draw red polygons on source image
   └── return overlay.png (and/or polygons JSON)
```

### 5.2 Java call to the microservice (WebClient)
```java
WebClient client = WebClient.builder().baseUrl("http://inference:8000").build();

public PolygonResponse infer(File png, double ppm) {
    return client.post().uri(uriBuilder -> uriBuilder
            .path("/infer").queryParam("ppm", ppm).build())
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(BodyInserters.fromMultipartData("file", new FileSystemResource(png)))
        .retrieve()
        .bodyToMono(PolygonResponse.class)
        .block();
}
```

**`PolygonResponse` DTO (example):**
```java
public record PolygonDTO(int class_id, double score, List<int[]> points, double area_px, double ppm) {}
public record PolygonResponse(List<PolygonDTO> polygons) {}
```

### 5.3 Overlay & JSON (existing code)
- Use your **OpenCV drawer** to render polygons in **red**.
- Emit your existing **JSON** schema and attach **rule_ids** from **RAG** validation.

---

## 6) Keep RAG in the loop

- **Pre‑guidance:** lane width from rules → expected band thickness → use for post‑filtering short/too‑wide polygons.
- **Post‑validation:** check `width_m`, `stroke/gap`, zebra period; assign **type** + **rule_ids**; log **why** a polygon passed/failed.

---

## 7) Docker Compose (Spring + Inference + PDAL/GDAL)

```yaml
version: "3.8"
services:
  inference:
    build: ./inference
    ports: ["8000:8000"]

  app:
    build: ./spring-app
    environment:
      INFER_URL: http://inference:8000/infer
    ports: ["8080:8080"]
    depends_on: [inference]
```

---

## 8) Testing checklist

- [ ] PDAL/GDAL path works: `.laz → intensity.png`.
- [ ] Inference returns polygons for a few samples.
- [ ] RAG passes/fails align with expectations (unit tests for each rule).
- [ ] Overlay PNG visually matches ground truth on a test set.
- [ ] Throughput test: N images/min on your target hardware.

---

## 9) Operational notes

- Save model **version** with training **config.yaml** and dataset hash.
- Log **ppm**, **resolution**, and applied rule suite version in outputs.
- For reproducibility, pin `torch`, `detectron2`, and CUDA versions.

---

## 10) Useful commands

```bash
# Train
python train.py

# Serve
uvicorn serve:app --host 0.0.0.0 --port 8000

# Spring (example)
./mvnw -DskipTests package && java -jar target/*.jar
```

---

## 11) Deliverables

- `train.py` + `out/model_final.pth` (and config).
- `serve.py` (FastAPI) containerized.
- Spring endpoint wiring + RAG validation.
- Example `intensity.png` → `overlay.png` + polygons JSON.
