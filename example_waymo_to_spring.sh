#!/bin/bash

# Example: Waymo TFRecord to Spring Boot Polygon Detection
# This script demonstrates the complete workflow from TFRecord to polygon detection

set -e

echo "🔄 Waymo TFRecord → Spring Boot Polygon Detection"
echo "================================================"

# Configuration
TFRECORD_FILE="data/individual_files_testing_segment-10084636266401282188_1120_000_1140_000_with_camera_labels.tfrecord"
OUTPUT_DIR="out/example_workflow"
SPRING_URL="http://localhost:8090"

# Check if TFRecord exists
if [ ! -f "$TFRECORD_FILE" ]; then
    echo "❌ TFRecord file not found: $TFRECORD_FILE"
    echo "   Please ensure the file is in the data/ directory"
    exit 1
fi

# Create output directory
mkdir -p "$OUTPUT_DIR"

echo "📁 Input: $TFRECORD_FILE"
echo "📁 Output: $OUTPUT_DIR"
echo ""

# Step 1: Generate BEV intensity images
echo "1️⃣  Generating BEV intensity images..."
python tools/make_bev_intensity.py \
    --tfrecord "$TFRECORD_FILE" \
    --out "$OUTPUT_DIR/bev" \
    --res 0.20 \
    --extent -80 80 -80 80 \
    --frames 0..5 \
    --lidar-name TOP

if [ $? -eq 0 ]; then
    echo "✅ BEV images generated successfully"
else
    echo "❌ Failed to generate BEV images"
    exit 1
fi

# Step 2: Check if Spring Boot is running
echo ""
echo "2️⃣  Checking Spring Boot service..."
if curl -s "$SPRING_URL/actuator/health" > /dev/null 2>&1; then
    echo "✅ Spring Boot is running at $SPRING_URL"
else
    echo "⚠️  Spring Boot is not running at $SPRING_URL"
    echo "   Please start the Spring Boot application:"
    echo "   ./mvnw spring-boot:run"
    echo ""
    echo "   You can still review the generated BEV images in:"
    echo "   $OUTPUT_DIR/bev/"
    exit 0
fi

# Step 3: Send images to polygon detection
echo ""
echo "3️⃣  Sending BEV images to polygon detection..."

# Create results directory
mkdir -p "$OUTPUT_DIR/polygon_results"

# Process each BEV image
for bev_image in "$OUTPUT_DIR"/bev/frame_*.png; do
    if [ -f "$bev_image" ]; then
        filename=$(basename "$bev_image" .png)
        echo "   Processing: $filename"
        
        # Traditional polygon detection
        curl -s -X POST "$SPRING_URL/api/polygons/overlay" \
            -F "data=@$bev_image" \
            -o "$OUTPUT_DIR/polygon_results/${filename}_overlay.png"
        
        # Get polygon JSON
        curl -s -X POST "$SPRING_URL/api/polygons" \
            -F "data=@$bev_image" \
            -H "Accept: application/json" \
            > "$OUTPUT_DIR/polygon_results/${filename}_polygons.json"
        
        # Try MaskRCNN if available
        if curl -s "$SPRING_URL/api/polygons/maskrcnn/health" | grep -q "healthy"; then
            echo "     + MaskRCNN detection available"
            curl -s -X POST "$SPRING_URL/api/polygons/maskrcnn/overlay" \
                -F "data=@$bev_image" \
                -F "min_area=200" \
                -o "$OUTPUT_DIR/polygon_results/${filename}_maskrcnn_overlay.png"
        fi
    fi
done

echo "✅ Polygon detection complete"

# Step 4: Generate summary
echo ""
echo "4️⃣  Generating summary..."

# Count generated files
bev_count=$(find "$OUTPUT_DIR/bev" -name "frame_*.png" | wc -l)
overlay_count=$(find "$OUTPUT_DIR/polygon_results" -name "*_overlay.png" | wc -l)
json_count=$(find "$OUTPUT_DIR/polygon_results" -name "*_polygons.json" | wc -l)

# Create summary report
cat > "$OUTPUT_DIR/processing_summary.md" << EOF
# Waymo TFRecord Processing Summary

## Input
- **TFRecord File**: \`$TFRECORD_FILE\`
- **Processing Time**: $(date)

## Generated Files
- **BEV Images**: $bev_count files in \`bev/\`
- **Polygon Overlays**: $overlay_count files in \`polygon_results/\`
- **Polygon JSON**: $json_count files in \`polygon_results/\`

## Usage with Spring Boot
1. **BEV Images**: Ready for polygon detection at resolution 0.20 m/px (PPM: 5.0)
2. **Polygon Detection**: Traditional CV + RAG validation applied
3. **Overlay Images**: Red polygons drawn on detected road markings

## Next Steps
1. Review the generated overlay images to validate detection quality
2. Check polygon JSON files for detected marking coordinates and classifications  
3. Adjust BEV generation parameters if needed:
   - \`--res\`: Change resolution (lower = higher detail)
   - \`--extent\`: Adjust area coverage
   - \`--lidar-name\`: Use different LiDAR sensors

## Files Location
\`\`\`
$OUTPUT_DIR/
├── bev/                     # BEV intensity images + metadata
├── polygon_results/         # Detection results
└── processing_summary.md    # This summary
\`\`\`
EOF

echo "📋 Summary report: $OUTPUT_DIR/processing_summary.md"

# Final output
echo ""
echo "🎉 Workflow Complete!"
echo "=============================="
echo "📊 Generated $bev_count BEV images from Waymo TFRecord"
echo "🎯 Processed $overlay_count images through polygon detection"
echo "📁 Results saved in: $OUTPUT_DIR/"
echo ""
echo "Review your results:"
echo "- BEV images: $OUTPUT_DIR/bev/"
echo "- Polygon overlays: $OUTPUT_DIR/polygon_results/"
echo "- Processing summary: $OUTPUT_DIR/processing_summary.md"
echo ""
echo "To process more frames or adjust parameters, see:"
echo "docs/WAYMO_EXTRACT_README.md"