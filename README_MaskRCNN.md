# MaskRCNN Integration for Lane-Assist

This document describes the integration of Mask R-CNN (Detectron2) for instance segmentation of road markings in the lane-assist application.

## Overview

The system combines traditional computer vision techniques with deep learning (Mask R-CNN) to detect and classify road markings from LiDAR intensity images. It supports:

- **Classes**: lane_leg, junction_core, zebra_crossing, stop_line, arrow
- **Input**: LAZ/LAS files (converted to intensity PNG) or direct PNG/TIF images
- **Output**: Polygon JSON + overlay images with red markings
- **Validation**: RAG (Retrieval-Augmented Generation) rules for European road standards

## Architecture

```
┌─────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   LiDAR     │    │    Spring Boot   │    │   FastAPI       │
│ (.laz/.las) │───▶│   Application    │───▶│  MaskRCNN       │
│             │    │                  │    │  Inference      │
└─────────────┘    └──────────────────┘    └─────────────────┘
                            │
                            ▼
                   ┌─────────────────┐
                   │  Polygon JSON   │
                   │  + Overlay PNG  │
                   │  + RAG Rules    │
                   └─────────────────┘
```

## Quick Start

### 1. Start Services

```bash
# Build and start all services
docker-compose up --build

# Or start individually
cd inference && uvicorn serve:app --host 0.0.0.0 --port 8000
./mvnw spring-boot:run
```

### 2. API Endpoints

#### MaskRCNN Inference Only
```bash
curl -X POST "http://localhost:8090/api/polygons/maskrcnn" \
  -F "data=@sample.laz" \
  -F "resolution=0.20" \
  -F "min_area=200"
```

#### MaskRCNN with Overlay
```bash
curl -X POST "http://localhost:8090/api/polygons/maskrcnn/overlay" \
  -F "data=@sample.laz" \
  -F "resolution=0.20" \
  -F "validate_rules=true" \
  --output overlay.png
```

#### Health Check
```bash
curl http://localhost:8090/api/polygons/maskrcnn/health
```

## Training Your Own Model

### 1. Prepare Dataset

```bash
# Create COCO format dataset
dataset/
  train/
    images/
      intensity_001.png
      intensity_002.png
      ...
    annotations.json
  val/
    images/
      intensity_val_001.png
      ...
    annotations.json
```

### 2. Train Model

```bash
cd inference
python train.py --dataset-path ./dataset --output-dir ./out
```

### 3. Update Model Path

Update `inference/serve.py`:
```python
model_path = "out/model_final.pth"  # Your trained model
```

## Configuration

### Spring Boot Configuration

```yaml
# application.yaml
inference:
  service:
    url: http://localhost:8000  # Inference service URL

polygons:
  ppm: 5.0                      # Pixels per meter for image input
  minAreaFrac: 0.0015          # Minimum polygon area threshold
```

### Inference Service Configuration

```python
# inference/serve.py
cfg.MODEL.ROI_HEADS.NUM_CLASSES = 5         # Number of classes
cfg.MODEL.ROI_HEADS.SCORE_THRESH_TEST = 0.5 # Confidence threshold
```

## Class Mapping

| Class ID | Class Name      | Description                    |
|----------|-----------------|--------------------------------|
| 0        | lane_leg        | Lane boundary markings         |
| 1        | junction_core   | Junction/intersection areas    |
| 2        | zebra_crossing  | Pedestrian crossings          |
| 3        | stop_line       | Stop line markings            |
| 4        | arrow           | Directional arrow markings    |

## API Response Format

### MaskRCNN Response

```json
{
  "polygons": [
    {
      "class_id": 0,
      "class_name": "lane_leg",
      "score": 0.95,
      "points": [[x1,y1], [x2,y2], ...],
      "area_px": 1500.0,
      "area_m2": 60.0,
      "perimeter_m": 25.5,
      "width_m": 0.15,
      "ppm": 5.0
    }
  ],
  "image_shape": [1024, 1024],
  "total_detections": 1,
  "model_info": {
    "score_threshold": 0.5,
    "num_classes": 5
  }
}
```

### Traditional Pipeline Response

```json
[
  {
    "id": "1",
    "type": "LANE_MARKING",
    "points": [[x1,y1], [x2,y2], ...],
    "area_m2": 60.0,
    "width_m": 0.15,
    "perimeter_m": 25.5,
    "appliedRules": ["EU_LANE_WIDTH_STANDARD"],
    "status": "VALID"
  }
]
```

## RAG Rules Integration

The system validates detected polygons against European road marking standards:

- **Width validation**: 0.08m - 0.20m for lane markings
- **Area threshold**: Minimum 0.1 m²
- **Aspect ratio**: For specific marking types
- **Stroke/gap patterns**: For dashed lines

## Performance Considerations

### Hardware Requirements

- **Training**: NVIDIA GPU with 8GB+ VRAM
- **Inference**: GPU recommended, CPU fallback available
- **Memory**: 4GB+ RAM for inference service

### Optimization Tips

1. **Batch Processing**: Use `/infer_batch` for multiple images
2. **Model Quantization**: Consider ONNX export for faster inference
3. **Image Preprocessing**: Optimal resolution is 1024x1024
4. **Caching**: Enable model caching in production

## Development

### Adding New Classes

1. Update class mapping in `inference/serve.py`:
```python
class_names = {
    0: "lane_leg",
    1: "junction_core", 
    2: "zebra_crossing",
    3: "stop_line",
    4: "arrow",
    5: "your_new_class"  # Add here
}
```

2. Update `cfg.MODEL.ROI_HEADS.NUM_CLASSES` in training script

3. Retrain model with new annotations

### Custom Rules

Add new validation rules in `src/main/resources/rules/`:

```yaml
- id: "custom_rule_1"
  tags: ["custom", "marking"]
  params:
    width_range_m: [0.05, 0.30]
  checks:
    - "width_m >= 0.05"
    - "width_m <= 0.30"
```

## Troubleshooting

### Common Issues

1. **Service Not Available**
   ```bash
   # Check inference service
   curl http://localhost:8000/health
   ```

2. **CUDA Out of Memory**
   - Reduce batch size in training
   - Use smaller input resolution
   - Enable gradient checkpointing

3. **Low Detection Accuracy**
   - Increase training iterations
   - Adjust score threshold
   - Add more training data

### Logs

```bash
# Spring Boot logs
docker-compose logs app

# Inference service logs  
docker-compose logs inference
```

## Monitoring

### Metrics

- **Detection count**: Number of polygons detected
- **Confidence scores**: Model confidence per detection
- **Processing time**: End-to-end latency
- **Rule compliance**: RAG validation results

### Health Checks

- `/health` - Inference service health
- `/actuator/health` - Spring Boot health
- Docker health checks enabled

## Production Deployment

### Scaling

```yaml
# docker-compose.override.yml
services:
  inference:
    deploy:
      replicas: 3
    ports:
      - "8000-8002:8000"
```

### Load Balancing

Use nginx or cloud load balancer to distribute requests across inference replicas.

### Model Versioning

- Tag models with training date and dataset hash
- Use blue-green deployment for model updates
- Monitor performance metrics after model updates

## Support

For issues and questions:
- Check logs first
- Verify service health endpoints
- Review configuration files
- Test with sample data

See main README.md for general application documentation.