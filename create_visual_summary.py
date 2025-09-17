#!/usr/bin/env python3
"""
Create visual summary images for Waymo LiDAR polygon detection results.
"""

import cv2
import numpy as np
import json
import os
from pathlib import Path

def create_summary_image():
    """Create a comprehensive visual summary of the detection results."""
    
    # Create a large canvas for the summary
    canvas_width = 1600
    canvas_height = 1200
    canvas = np.ones((canvas_height, canvas_width, 3), dtype=np.uint8) * 255
    
    # Add title
    title = "Waymo LiDAR → Polygon Detection Results"
    cv2.putText(canvas, title, (50, 60), cv2.FONT_HERSHEY_SIMPLEX, 1.5, (0, 0, 0), 3)
    cv2.putText(canvas, "Lane-Assist Spring Boot Application", (50, 100), 
                cv2.FONT_HERSHEY_SIMPLEX, 0.8, (100, 100, 100), 1)
    
    # Load images
    try:
        # Original BEV image
        bev_img = cv2.imread('bev_output/frame_000000.png', cv2.IMREAD_GRAYSCALE)
        if bev_img is not None:
            bev_img_color = cv2.cvtColor(bev_img, cv2.COLOR_GRAY2BGR)
            bev_img_resized = cv2.resize(bev_img_color, (400, 400))
            # Add border
            bev_img_resized = cv2.copyMakeBorder(bev_img_resized, 2, 2, 2, 2, 
                                                cv2.BORDER_CONSTANT, value=(0, 0, 0))
            # Place on canvas
            canvas[150:554, 50:454] = bev_img_resized
            cv2.putText(canvas, "Input: BEV Intensity Image", (50, 140), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 0), 1)
            cv2.putText(canvas, "Resolution: 0.20 m/px", (50, 580), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.5, (50, 50, 50), 1)
            cv2.putText(canvas, "PPM: 5.0 pixels/meter", (50, 600), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.5, (50, 50, 50), 1)
        
        # Detection result with overlays
        result_img = cv2.imread('polygon_result_frame_000000.png')
        if result_img is not None:
            result_img_resized = cv2.resize(result_img, (400, 400))
            # Add border
            result_img_resized = cv2.copyMakeBorder(result_img_resized, 2, 2, 2, 2, 
                                                   cv2.BORDER_CONSTANT, value=(0, 0, 0))
            # Place on canvas
            canvas[150:554, 500:904] = result_img_resized
            cv2.putText(canvas, "Output: Polygon Detection", (500, 140), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 0), 1)
            cv2.putText(canvas, "Red overlays = detected markings", (500, 580), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.5, (200, 0, 0), 1)
        
        # Add arrow between images
        arrow_start = (454, 350)
        arrow_end = (500, 350)
        cv2.arrowedLine(canvas, arrow_start, arrow_end, (0, 150, 0), 3, tipLength=0.3)
        cv2.putText(canvas, "Polygon Detection", (435, 340), 
                   cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 150, 0), 1)
        
    except Exception as e:
        print(f"Error loading images: {e}")
    
    # Add detection results panel
    panel_y = 650
    cv2.rectangle(canvas, (50, panel_y), (900, panel_y + 300), (240, 240, 240), -1)
    cv2.rectangle(canvas, (50, panel_y), (900, panel_y + 300), (100, 100, 100), 2)
    
    cv2.putText(canvas, "Detection Results:", (70, panel_y + 30), 
                cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 0, 0), 2)
    
    # Load and display JSON results
    try:
        with open('polygon_data_frame_000000.json', 'r') as f:
            data = json.load(f)
            
        if data and len(data) > 0:
            polygon = data[0]
            
            # Display polygon information
            y_offset = panel_y + 60
            info_lines = [
                f"Type: {polygon.get('type', 'N/A')}",
                f"Area: {polygon.get('features', {}).get('area_m2', 0):.1f} m²",
                f"Length: {polygon.get('features', {}).get('length_m', 0):.1f} meters",
                f"Width: {polygon.get('features', {}).get('width_m', 0):.1f} meters",
                f"Classification: {', '.join(polygon.get('ruleIds', []))}",
                f"Points: {len(polygon.get('points', []))} vertices"
            ]
            
            for line in info_lines:
                cv2.putText(canvas, line, (90, y_offset), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 0), 1)
                y_offset += 30
                
            # Add success indicator
            cv2.circle(canvas, (850, panel_y + 150), 30, (0, 200, 0), -1)
            cv2.putText(canvas, "✓", (835, panel_y + 165), 
                       cv2.FONT_HERSHEY_SIMPLEX, 1.5, (255, 255, 255), 2)
            cv2.putText(canvas, "Detection Success", (790, panel_y + 210), 
                       cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 150, 0), 1)
    except Exception as e:
        cv2.putText(canvas, f"Error loading results: {e}", (70, panel_y + 60), 
                   cv2.FONT_HERSHEY_SIMPLEX, 0.6, (0, 0, 200), 1)
    
    # Add pipeline diagram on the right
    pipeline_x = 950
    pipeline_y = 150
    
    cv2.putText(canvas, "Processing Pipeline", (pipeline_x, pipeline_y - 10), 
                cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 0, 0), 2)
    
    # Pipeline steps
    steps = [
        ("1. Waymo TFRecord", (100, 100, 255)),
        ("2. LiDAR Point Cloud", (100, 150, 255)),
        ("3. BEV Generation", (100, 200, 255)),
        ("4. Spring Boot API", (0, 150, 0)),
        ("5. OpenCV Detection", (0, 180, 0)),
        ("6. Polygon Extraction", (0, 200, 0)),
        ("7. RAG Validation", (200, 150, 0)),
        ("8. Results Output", (200, 100, 0))
    ]
    
    for i, (step, color) in enumerate(steps):
        y_pos = pipeline_y + i * 60
        # Draw box
        cv2.rectangle(canvas, (pipeline_x, y_pos), (pipeline_x + 250, y_pos + 40), 
                     color, -1)
        cv2.rectangle(canvas, (pipeline_x, y_pos), (pipeline_x + 250, y_pos + 40), 
                     (0, 0, 0), 1)
        # Add text
        cv2.putText(canvas, step, (pipeline_x + 10, y_pos + 25), 
                   cv2.FONT_HERSHEY_SIMPLEX, 0.5, (255, 255, 255), 1)
        # Draw arrow
        if i < len(steps) - 1:
            cv2.arrowedLine(canvas, (pipeline_x + 125, y_pos + 40), 
                          (pipeline_x + 125, y_pos + 60), (50, 50, 50), 2, tipLength=0.3)
    
    # Add timestamp and metadata
    cv2.putText(canvas, "Generated: 2025-09-17", (50, canvas_height - 30), 
                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (100, 100, 100), 1)
    cv2.putText(canvas, "Spring Boot Polygon Detection System", (canvas_width - 350, canvas_height - 30), 
                cv2.FONT_HERSHEY_SIMPLEX, 0.5, (100, 100, 100), 1)
    
    return canvas

def create_comparison_grid():
    """Create a grid comparing multiple frames."""
    
    # Create canvas for 2x3 grid
    grid_width = 1200
    grid_height = 900
    grid = np.ones((grid_height, grid_width, 3), dtype=np.uint8) * 255
    
    # Add title
    cv2.putText(grid, "Multi-Frame Detection Results", (50, 40), 
                cv2.FONT_HERSHEY_SIMPLEX, 1.2, (0, 0, 0), 2)
    
    # Image dimensions
    img_width = 380
    img_height = 380
    margin = 20
    
    # Process 3 frames (top row: BEV, bottom row: detection)
    for i in range(3):
        x_pos = margin + i * (img_width + margin)
        
        # Top row - BEV images
        bev_path = f'bev_output/frame_{i:06d}.png'
        if os.path.exists(bev_path):
            bev_img = cv2.imread(bev_path, cv2.IMREAD_GRAYSCALE)
            if bev_img is not None:
                bev_img_color = cv2.cvtColor(bev_img, cv2.COLOR_GRAY2BGR)
                bev_resized = cv2.resize(bev_img_color, (img_width, img_height))
                y_pos = 80
                grid[y_pos:y_pos+img_height, x_pos:x_pos+img_width] = bev_resized
                cv2.putText(grid, f"Frame {i:03d} Input", (x_pos + 10, y_pos - 5), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 1)
        
        # Bottom row - detection results
        result_path = f'polygon_result_frame_{i:06d}.png'
        if os.path.exists(result_path):
            result_img = cv2.imread(result_path)
            if result_img is not None:
                result_resized = cv2.resize(result_img, (img_width, img_height))
                y_pos = 80 + img_height + 40
                grid[y_pos:y_pos+img_height, x_pos:x_pos+img_width] = result_resized
                cv2.putText(grid, f"Frame {i:03d} Detection", (x_pos + 10, y_pos - 5), 
                           cv2.FONT_HERSHEY_SIMPLEX, 0.5, (0, 0, 0), 1)
                
                # Add arrow
                arrow_y = 80 + img_height + 20
                cv2.arrowedLine(grid, (x_pos + img_width//2, arrow_y - 15), 
                              (x_pos + img_width//2, arrow_y + 5), 
                              (0, 150, 0), 2, tipLength=0.3)
    
    return grid

def main():
    """Generate all visual summaries."""
    
    print("🎨 Creating visual summary images...")
    
    # Create main summary
    summary_img = create_summary_image()
    cv2.imwrite('response-images/01_main_summary.png', summary_img)
    print("✅ Created: response-images/01_main_summary.png")
    
    # Create comparison grid
    grid_img = create_comparison_grid()
    cv2.imwrite('response-images/02_frame_comparison.png', grid_img)
    print("✅ Created: response-images/02_frame_comparison.png")
    
    # Copy the best detection result as showcase
    try:
        best_result = cv2.imread('polygon_result_frame_000000.png')
        if best_result is not None:
            cv2.imwrite('response-images/03_best_detection_result.png', best_result)
            print("✅ Created: response-images/03_best_detection_result.png")
    except:
        pass
    
    print("\n📁 All visual responses saved to: response-images/")
    print("View these images to see the complete detection results!")

if __name__ == "__main__":
    main()