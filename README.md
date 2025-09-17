# LiDAR Lane Polygon Detection System

A Spring Boot application for detecting lane markings and road polygons from LiDAR data and intensity images, compliant with European road standards (Vienna Convention, CEN EN 1436).

## 🚀 Quick Start

1. **Start the application:**
   ```bash
   mvn spring-boot:run
   ```

2. **Process images via API:**
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

## 📋 API Endpoints

### `/api/polygons/overlay` (POST)
- **Input**: Multipart form data with file field `data`
- **Output**: PNG image with red polygons overlaid
- **Parameters**: 
  - `resolution` (optional): For LAZ files, meters per pixel (default: 0.20)

### `/api/polygons` (POST)  
- **Input**: Multipart form data with file field `data`
- **Output**: JSON array of detected polygons with features and classifications

### `/api/polygons/debug` (POST)
- **Input**: Multipart form data with file field `data`
- **Output**: ZIP file containing intermediate processing frames:
  - `road.png` - Road surface mask
  - `marks.png` - Lane markings mask
  - `bands.png` - Polygon generation bands
  - `overlay.png` - Final overlay result

## 🗂️ Supported Input Formats

### LiDAR Point Clouds (LAZ/LAS)
Requires PDAL and GDAL installation:
```bash
# Ubuntu/Debian
sudo apt-get install pdal gdal-bin

# Process LAZ files with specified resolution
curl -X POST "http://localhost:8090/api/polygons/overlay?resolution=0.20" \
     -F "data=@points.laz" -o result.png
```

### Intensity Images (PNG/TIFF/JPEG)
Direct processing of pre-rasterized intensity images:
```bash
curl -X POST "http://localhost:8090/api/polygons/overlay" \
     -F "data=@intensity_image.png" -o result.png
```

## 🔧 System Architecture

```
Input (LAZ/LAS or PNG/TIFF/JPEG)
      └─ if LAZ/LAS: PDAL → Intensity GeoTIFF/PNG
             └─ gdal_translate (8-bit PNG, scaled)
                  └─ OpenCV pipeline → masks → bands → polygons
                       ├─ Overlay drawer → red polygons (PNG)
                       └─ Polygon JSON + rule classifications (RAG)
```

### Processing Pipeline
1. **Input Detection** → Automatic file type detection (LAZ vs image)
2. **Preprocessing** → LAZ to intensity raster (if needed)
3. **Road Detection** → Extract road surface areas using morphological operations
4. **Marking Detection** → Identify lane markings using top-hat filtering
5. **Band Generation** → Create anisotropic dilation bands
6. **Polygon Extraction** → Find contours and create polygons
7. **Classification** → Apply European road marking rules
8. **Output Generation** → PNG overlay + JSON data

## 📊 Detection Capabilities

- **Lane Markings**: Solid lines, dashed lines, edge lines
- **European Standards**: Vienna Convention, CEN EN 1436 compliance
- **Geometric Features**: Area, length, width measurements in real-world units
- **Rule Classification**: Automatic categorization based on road marking rules
- **Debug Visualization**: Intermediate processing steps for analysis

### Example Output
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

## 🛠️ Development

### Running Tests
```bash
mvn test
```

### Building
```bash
mvn clean package
```

### Configuration
Edit `src/main/resources/application.yaml` to adjust:
- Processing parameters (ppm, thresholds)
- OpenCV algorithm settings
- Rule engine configuration

## 📁 Project Structure

```
src/
├── main/java/com/example/lanes/
│   ├── api/           # REST controllers
│   ├── core/          # Core processing logic
│   ├── config/        # Configuration classes
│   ├── model/         # Data models
│   ├── rag/           # Rule engine
│   └── demo/          # Demo runner (optional)
└── test/              # Unit and integration tests
```

## 🔧 Dependencies

- **Java 17**
- **Spring Boot 3.x**
- **OpenCV** (via Bytedeco opencv-platform)
- **PDAL/GDAL** (for LAZ processing, optional)
- **Jackson** (YAML/JSON processing)

## 📄 License

This project implements European road marking detection standards and is intended for research and development purposes.

## 🐛 Troubleshooting

### LAZ Processing Issues
- Ensure PDAL and GDAL are installed and available on PATH
- Use the provided `process_laz.py` script for standalone LAZ conversion
- Test with PNG images first to verify the detection pipeline

### No Demo Output
- The application no longer requires specific input images
- Use the API endpoints directly to process your own images
- Demo mode will activate if standard image files are found

### Performance Issues
- Adjust OpenCV parameters in `application.yaml`
- Reduce image resolution for faster processing
- Enable debug mode to identify bottlenecks