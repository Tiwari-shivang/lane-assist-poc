# Waymo TFRecord → LiDAR/BEV Extraction + Spring Polygonizer Integration

This documentation describes tools for extracting LiDAR point clouds and Bird's Eye View (BEV) intensity images from Waymo Open Dataset TFRecord files for use with the Spring Boot polygon detection system.

## Overview

The extraction pipeline consists of three Python tools that process Waymo TFRecord files:

1. **`extract_lidar_to_ply.py`** - Extract LiDAR point clouds to PLY format
2. **`make_bev_intensity.py`** - Generate BEV intensity PNG images for polygon detection  
3. **`extract_camera_pngs.py`** - Extract camera images to PNG format (optional)

The BEV intensity images are designed to work directly with the Spring Boot `/api/polygons/overlay` endpoint for road marking detection.

## Environment Setup

### 1. Create Python Virtual Environment

```bash
# Navigate to the tools directory
cd tools

# Create virtual environment (Python 3.10+ recommended)
python3 -m venv waymo_env
source waymo_env/bin/activate  # On Windows: waymo_env\Scripts\activate

# Upgrade pip
pip install --upgrade pip
```

### 2. Install Dependencies

```bash
# Install required packages
pip install -r requirements.txt
```

### 3. Verify Installation

```bash
# Test TensorFlow installation
python -c "import tensorflow as tf; print(f'TensorFlow version: {tf.__version__}')"

# Test Waymo Open Dataset installation
python -c "import waymo_open_dataset as w; print('Waymo Open Dataset: OK')"

# Test other dependencies
python -c "import cv2, numpy as np, PIL; print('OpenCV, NumPy, PIL: OK')"
```

### 4. Optional: Install PDAL for LAZ Support

For PLY to LAZ conversion support:

```bash
# On Ubuntu/Debian
sudo apt-get install pdal

# On macOS with Homebrew
brew install pdal

# On Windows
# Download and install from: https://pdal.io/

# Verify PDAL installation
pdal --version
```

## Usage

### Extract LiDAR Point Clouds to PLY

Extract per-frame LiDAR point clouds from TFRecord files:

```bash
python tools/extract_lidar_to_ply.py \
  --tfrecord data/segment-10084636266401282188_1120_000_1140_000_with_camera_labels.tfrecord \
  --out out/ply_frames \
  --every 10 \
  --include-intensity \
  --lidar-name TOP
```

**Options:**
- `--tfrecord`: Path to input TFRecord file
- `--out`: Output directory for PLY files
- `--every N`: Extract every Nth frame (default: 1)
- `--include-intensity`: Include intensity values in PLY output
- `--lidar-name`: LiDAR sensor to extract (TOP, FRONT, SIDE_LEFT, SIDE_RIGHT, REAR)
- `--to-laz`: Convert PLY to LAZ using PDAL (requires PDAL)
- `--max-frames N`: Maximum number of frames to process

**Output:**
- `frame_000000.ply`, `frame_000010.ply`, etc.
- Optional `.laz` files if `--to-laz` is specified

### Generate BEV Intensity Images

Create Bird's Eye View intensity images suitable for polygon detection:

```bash
python tools/make_bev_intensity.py \
  --tfrecord data/segment-10084636266401282188_1120_000_1140_000_with_camera_labels.tfrecord \
  --out out/bev \
  --res 0.20 \
  --extent -80 80 -80 80 \
  --frames 0..50 \
  --lidar-name TOP
```

**Options:**
- `--tfrecord`: Path to input TFRecord file
- `--out`: Output directory for BEV images
- `--res`: Resolution in meters per pixel (default: 0.20)
- `--extent`: BEV extent as `xmin xmax ymin ymax` (default: -80 80 -80 80)
- `--frames`: Frame range - 'all', 'N' (single), or 'N..M' (range)
- `--composite`: Accumulate all frames into one composite PNG
- `--reducer`: Intensity reducer per cell - 'max' or 'mean' (default: max)
- `--lidar-name`: LiDAR sensor - TOP, FRONT, SIDE_LEFT, SIDE_RIGHT, REAR, or ALL
- `--normalize-method`: Normalization - 'minmax' or 'percentile'

**Output:**
- `frame_000000.png`, `frame_000001.png`, etc.
- `frame_000000_metadata.json` with PPM and extent information
- `processing_summary.json` with extraction details

### Extract Camera Images (Optional)

Extract camera images for cross-reference or RGB model training:

```bash
python tools/extract_camera_pngs.py \
  --tfrecord data/segment-10084636266401282188_1120_000_1140_000_with_camera_labels.tfrecord \
  --out out/camera_pngs \
  --every 5 \
  --cameras FRONT FRONT_LEFT FRONT_RIGHT
```

**Options:**
- `--tfrecord`: Path to input TFRecord file
- `--out`: Output directory for camera PNG files
- `--every N`: Extract every Nth frame (default: 1)
- `--cameras`: Camera sensors to extract (FRONT, FRONT_LEFT, FRONT_RIGHT, SIDE_LEFT, SIDE_RIGHT)
- `--max-frames N`: Maximum number of frames to process
- `--quality`: JPEG quality for PNG conversion (default: 95)

**Output:**
- Subdirectories: `front/`, `front_left/`, `front_right/`, `side_left/`, `side_right/`
- Files: `cam_front_000000.png`, `cam_front_left_000000.png`, etc.

## Integration with Spring Boot Polygon Detection

### End-to-End Example

```bash
# 1. Generate BEV intensity images
python tools/make_bev_intensity.py \
  --tfrecord data/segment-10084636266401282188_1120_000_1140_000_with_camera_labels.tfrecord \
  --out out/bev \
  --res 0.20 \
  --extent -80 80 -80 80 \
  --frames 0..10

# 2. Start Spring Boot application (in another terminal)
cd /path/to/lane-assist
./mvnw spring-boot:run

# 3. Send BEV image to polygon detection endpoint
curl -X POST "http://localhost:8090/api/polygons/overlay" \
  -F "data=@out/bev/frame_000005.png" \
  -o detected_polygons.png

# 4. Get polygon JSON data
curl -X POST "http://localhost:8090/api/polygons" \
  -F "data=@out/bev/frame_000005.png" \
  -H "Accept: application/json" > polygons.json
```

### Important Notes for Spring Integration

1. **PPM (Pixels Per Meter)**: The Spring endpoint uses `ppm = 1 / resolution`. For `--res 0.20`, PPM = 5.0
2. **Image Format**: Spring expects grayscale PNG images with intensity values
3. **Coordinate System**: BEV images use vehicle-centric coordinates (X=forward, Y=left)
4. **Resolution**: 0.20 m/px (PPM=5.0) is recommended for road marking detection

### MaskRCNN Integration

For enhanced detection using MaskRCNN:

```bash
# Send to MaskRCNN endpoint for instance segmentation
curl -X POST "http://localhost:8090/api/polygons/maskrcnn/overlay" \
  -F "data=@out/bev/frame_000005.png" \
  -F "min_area=200" \
  -o maskrcnn_overlay.png
```

## Metadata Format

Each BEV image includes a JSON metadata file with:

```json
{
  "resolution_m_per_px": 0.20,
  "ppm": 5.0,
  "extent": [-80, 80, -80, 80],
  "image_shape": [800, 800],
  "description": "BEV intensity image from Waymo LiDAR data"
}
```

This metadata is crucial for the Spring Boot application to correctly interpret spatial measurements and apply RAG (Retrieval-Augmented Generation) rules for European road marking standards.

## Troubleshooting

### Common Issues

1. **TensorFlow/Waymo Version Mismatch**
   ```bash
   # Check TensorFlow version
   python -c "import tensorflow as tf; print(tf.__version__)"
   
   # Install matching Waymo package
   pip install waymo-open-dataset-tf-2-15-0  # For TF 2.15
   ```

2. **Memory Usage**
   - Large TFRecord files may require significant RAM
   - Use `--every N` to process fewer frames
   - Use `--max-frames` to limit processing

3. **PDAL Not Found**
   ```bash
   # Check PDAL installation
   pdal --version
   
   # If not installed, PLY to LAZ conversion will be skipped
   ```

4. **Empty BEV Images**
   - Check `--extent` parameter - may be too small/large
   - Verify LiDAR data exists in the TFRecord
   - Try different `--lidar-name` options

5. **Spring Boot Connection Issues**
   ```bash
   # Check if Spring Boot is running
   curl http://localhost:8090/actuator/health
   
   # Check polygon endpoint
   curl http://localhost:8090/api/polygons/health
   ```

### Performance Tips

1. **Batch Processing**: Process multiple TFRecord files in parallel
2. **Resolution Selection**: Lower resolution (larger `--res` values) for faster processing
3. **Frame Sampling**: Use `--every N` for quick previews
4. **Composite Mode**: Use `--composite` for area overview images

## Directory Structure

After processing, your directory structure will look like:

```
lane-assist/
├── data/
│   └── segment-*.tfrecord
├── out/
│   ├── ply_frames/
│   │   ├── frame_000000.ply
│   │   └── frame_000000.laz (if --to-laz)
│   ├── bev/
│   │   ├── frame_000000.png
│   │   ├── frame_000000_metadata.json
│   │   └── processing_summary.json
│   └── camera_pngs/
│       ├── front/
│       ├── front_left/
│       └── ...
└── tools/
    ├── extract_lidar_to_ply.py
    ├── make_bev_intensity.py
    ├── extract_camera_pngs.py
    └── requirements.txt
```

## Advanced Usage

### Composite BEV Images

Create high-quality composite images from multiple frames:

```bash
python tools/make_bev_intensity.py \
  --tfrecord data/segment-*.tfrecord \
  --out out/bev \
  --res 0.10 \
  --extent -120 120 -120 120 \
  --frames all \
  --composite \
  --reducer max
```

### Multi-LiDAR Processing

Include all LiDAR sensors for comprehensive coverage:

```bash
python tools/make_bev_intensity.py \
  --tfrecord data/segment-*.tfrecord \
  --out out/bev \
  --res 0.20 \
  --extent -80 80 -80 80 \
  --lidar-name ALL
```

### High-Resolution Road Marking Analysis

For detailed road marking analysis:

```bash
python tools/make_bev_intensity.py \
  --tfrecord data/segment-*.tfrecord \
  --out out/bev_hires \
  --res 0.05 \
  --extent -40 40 -40 40 \
  --frames 0..100 \
  --normalize-method percentile
```

## Support and Development

For issues and enhancements:

1. Check this README for troubleshooting steps
2. Verify environment setup and dependencies
3. Test with provided sample TFRecord file
4. Review Spring Boot polygon detection logs

The tools are designed to integrate seamlessly with the existing Spring Boot polygon detection pipeline while providing flexibility for various use cases and analysis requirements.