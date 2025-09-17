#!/usr/bin/env python3
"""
Demo BEV generator that creates synthetic road marking images for testing.
This simulates the output of Waymo LiDAR processing for demonstration purposes.
"""

import os
import json
import numpy as np
import cv2
from pathlib import Path

def create_synthetic_road_scene(width=800, height=800, resolution=0.20):
    """Create a synthetic BEV image with realistic road markings."""
    
    # Create blank image
    image = np.zeros((height, width), dtype=np.uint8)
    
    # Parameters
    center_x, center_y = width // 2, height // 2
    lane_width_pixels = int(3.5 / resolution)  # 3.5m lane width
    marking_width_pixels = int(0.15 / resolution)  # 15cm marking width
    
    # Add road surface (low intensity background)
    cv2.rectangle(image, (0, 0), (width, height), 30, -1)
    
    # Add lane markings (horizontal lines)
    for i in range(3):  # 3 lanes
        y_pos = center_y + (i - 1) * lane_width_pixels
        if 0 <= y_pos < height:
            # Dashed center line
            if i == 1:  # Center line
                for x in range(0, width, 60):  # Dashed pattern
                    cv2.rectangle(image, 
                                (x, y_pos - marking_width_pixels//2), 
                                (x + 30, y_pos + marking_width_pixels//2), 
                                200, -1)
            else:  # Solid side lines
                cv2.rectangle(image, 
                            (0, y_pos - marking_width_pixels//2), 
                            (width, y_pos + marking_width_pixels//2), 
                            180, -1)
    
    # Add some crosswalk/zebra crossing
    zebra_start_x = center_x - 100
    zebra_end_x = center_x + 100
    zebra_y_start = center_y - lane_width_pixels * 2
    zebra_y_end = center_y + lane_width_pixels * 2
    
    stripe_width = int(0.6 / resolution)  # 60cm stripes
    for x in range(zebra_start_x, zebra_end_x, stripe_width * 2):
        cv2.rectangle(image, 
                    (x, zebra_y_start), 
                    (x + stripe_width, zebra_y_end), 
                    220, -1)
    
    # Add stop line
    stop_line_y = center_y + lane_width_pixels * 3
    stop_line_width = int(0.3 / resolution)  # 30cm width
    if stop_line_y < height:
        cv2.rectangle(image, 
                    (center_x - 200, stop_line_y), 
                    (center_x + 200, stop_line_y + stop_line_width), 
                    250, -1)
    
    # Add arrow marking
    arrow_center_x = center_x
    arrow_center_y = center_y + lane_width_pixels
    arrow_size = 40
    
    # Simple arrow shape
    arrow_points = np.array([
        [arrow_center_x, arrow_center_y - arrow_size],  # Top point
        [arrow_center_x - arrow_size//2, arrow_center_y],  # Left point
        [arrow_center_x - arrow_size//4, arrow_center_y],  # Left inner
        [arrow_center_x - arrow_size//4, arrow_center_y + arrow_size//2],  # Left bottom
        [arrow_center_x + arrow_size//4, arrow_center_y + arrow_size//2],  # Right bottom
        [arrow_center_x + arrow_size//4, arrow_center_y],  # Right inner
        [arrow_center_x + arrow_size//2, arrow_center_y],  # Right point
    ], np.int32)
    
    cv2.fillPoly(image, [arrow_points], 240)
    
    # Add some noise to make it more realistic
    noise = np.random.normal(0, 5, image.shape).astype(np.int8)
    image = np.clip(image.astype(np.int16) + noise, 0, 255).astype(np.uint8)
    
    return image

def generate_demo_frames(output_dir="bev_output", num_frames=5, resolution=0.20):
    """Generate multiple demo BEV frames."""
    
    print(f"🚀 Generating Demo BEV Images")
    print("=" * 50)
    print(f"📁 Output directory: {output_dir}")
    print(f"🎯 Resolution: {resolution} m/px (PPM: {1.0/resolution:.1f})")
    print(f"📊 Frames to generate: {num_frames}")
    
    # Create output directory
    os.makedirs(output_dir, exist_ok=True)
    
    # Extent and metadata
    extent = [-80, 80, -80, 80]  # xmin, xmax, ymin, ymax
    width = int((extent[1] - extent[0]) / resolution)
    height = int((extent[3] - extent[2]) / resolution)
    
    generated_frames = []
    
    for frame_idx in range(num_frames):
        print(f"Generating frame {frame_idx:06d}...")
        
        # Create synthetic BEV image
        bev_image = create_synthetic_road_scene(width, height, resolution)
        
        # Add some variation between frames
        if frame_idx > 0:
            # Slight shift to simulate vehicle movement
            shift_x = frame_idx * 2
            shift_y = 0
            M = np.float32([[1, 0, shift_x], [0, 1, shift_y]])
            bev_image = cv2.warpAffine(bev_image, M, (width, height))
        
        # Save image
        output_path = os.path.join(output_dir, f"frame_{frame_idx:06d}.png")
        cv2.imwrite(output_path, bev_image)
        
        # Save metadata
        metadata = {
            "resolution_m_per_px": resolution,
            "ppm": 1.0 / resolution,
            "extent": extent,
            "image_shape": [height, width],
            "description": "Demo BEV intensity image with synthetic road markings"
        }
        
        metadata_path = os.path.join(output_dir, f"frame_{frame_idx:06d}_metadata.json")
        with open(metadata_path, 'w') as f:
            json.dump(metadata, f, indent=2)
        
        generated_frames.append({
            "frame": frame_idx,
            "output_file": f"frame_{frame_idx:06d}.png",
            "image_shape": [height, width]
        })
        
        print(f"✅ Generated: frame_{frame_idx:06d}.png ({width}x{height})")
    
    # Save processing summary
    summary = {
        "input_source": "Synthetic demo data (simulating Waymo TFRecord)",
        "output_directory": output_dir,
        "resolution_m_per_px": resolution,
        "ppm": 1.0 / resolution,
        "extent": extent,
        "processed_frames": len(generated_frames),
        "frames": generated_frames
    }
    
    summary_path = os.path.join(output_dir, "processing_summary.json")
    with open(summary_path, 'w') as f:
        json.dump(summary, f, indent=2)
    
    print(f"\n✅ Generation complete!")
    print(f"📊 Generated {len(generated_frames)} demo BEV images")
    print(f"📋 Summary: {summary_path}")
    
    return generated_frames

def main():
    """Main function."""
    
    print("🎨 Demo BEV Generator for Lane Detection Testing")
    print("=" * 60)
    print("This generates synthetic BEV images simulating Waymo LiDAR data")
    print("for testing the Spring Boot polygon detection system.")
    print("")
    
    # Generate demo frames
    frames = generate_demo_frames(
        output_dir="bev_output",
        num_frames=5,
        resolution=0.20
    )
    
    if frames:
        print(f"\n🎉 Success! Generated {len(frames)} demo BEV images.")
        print(f"📁 Output files in: bev_output/")
        print("\nNext steps:")
        print("1. Review generated BEV images")
        print("2. Start Spring Boot application:")
        print("   ./mvnw spring-boot:run")
        print("3. Send images to polygon detection:")
        print("   curl -X POST \"http://localhost:8090/api/polygons/overlay\" \\")
        print("        -F \"data=@bev_output/frame_000000.png\" \\")
        print("        -o polygon_result.png")
        return True
    else:
        print("❌ Failed to generate demo images.")
        return False

if __name__ == "__main__":
    success = main()
    exit(0 if success else 1)