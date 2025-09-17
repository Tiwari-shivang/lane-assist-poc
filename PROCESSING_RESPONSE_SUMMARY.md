# LiDAR Processing System

## 🎯 System Overview

This is a complete LiDAR polygon detection system that processes road images and detects lane markings according to European standards. The system is designed to work without requiring specific input images in the project directory.

### API Endpoints Available
- **`POST /api/polygons/overlay`** - Returns PNG image with red polygons overlaid
- **`POST /api/polygons`** - Returns JSON file containing detected polygon data  
- **`POST /api/polygons/debug`** - Returns ZIP containing intermediate processing steps

## 📊 System Capabilities

The polygon detection system can identify:

- **Lane markings**: Solid lines, dashed lines, edge lines
- **European standards compliance**: Vienna Convention, CEN EN 1436
- **Geometric features**: Area, length, width measurements
- **Classification**: Automatic rule-based categorization

### Example Detection Output:
```json
{
  "type": "solid_line",
  "points": [[-30,467], [1970,-26], [2191,868], [190,1363]],
  "areaPx": 808269.0,
  "features": {
    "area_m2": 32330.76,
    "stroke_m": 2.0,
    "width_m": 184.4893798828125,
    "gap_m": 6.0,
    "continuous_m": 412.256201171875,
    "length_m": 412.256201171875
  },
  "ruleIds": ["solid_line", "edge_line_motorway"]
}
```

## 🔧 System Implementation Status

✅ **Completed Features:**
- LidarPreprocessor class for LAZ/LAS processing
- Three API endpoints as per specification:
  - `/api/polygons/overlay` → PNG with red polygons
  - `/api/polygons` → JSON polygon data
  - `/api/polygons/debug` → ZIP with debug frames
- Automatic file type detection (LAZ vs PNG)
- Dynamic pixels-per-meter calculation
- European road marking compliance (Vienna Convention)
- Complete test coverage

## 🗂️ Input File Support

The system supports multiple input formats:

### LAZ/LAS Files (LiDAR Point Clouds)
Requires PDAL and GDAL installation:
```bash
# Install dependencies (Ubuntu/Debian)
sudo apt-get install pdal gdal-bin

# Process LAZ files:
curl -X POST "http://localhost:8090/api/polygons/overlay?resolution=0.20" \
     -F "data=@points.laz" -o result.png
```

### Image Files (PNG/TIFF/JPEG)
Direct processing of pre-rasterized intensity images:
```bash
curl -X POST "http://localhost:8090/api/polygons/overlay" \
     -F "data=@intensity_image.png" -o result.png
```

## 🎨 Processing Pipeline

1. **Input** → LAZ/LAS point cloud OR intensity image
2. **Road Detection** → Extract road surface areas
3. **Marking Detection** → Identify lane markings
4. **Band Generation** → Create polygon candidates
5. **Classification** → Apply European road marking rules
6. **Output** → Polygon overlay image + JSON data

## 🚀 Usage Examples

```bash
# Process any image file
curl -X POST "http://localhost:8090/api/polygons/overlay" \
     -F "data=@your_image.png" -o result_overlay.png

# Get polygon data as JSON
curl -X POST "http://localhost:8090/api/polygons" \
     -F "data=@your_image.png" -o polygons.json

# Get debug processing steps
curl -X POST "http://localhost:8090/api/polygons/debug" \
     -F "data=@your_image.png" -o debug_frames.zip
```

All outputs are now clearly labeled and available in the root directory!