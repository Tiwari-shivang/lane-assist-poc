#!/usr/bin/env python3
"""
Generate a synthetic LiDAR intensity image of a European road with standard lane markings.
Based on European road marking standards (Vienna Convention, CEN EN 1436).
"""

import cv2
import numpy as np
import os

def create_european_road_lidar_image():
    # Image dimensions (typical LiDAR resolution)
    width, height = 1024, 768

    # Create base road surface (asphalt intensity)
    image = np.full((height, width), 40, dtype=np.uint8)  # Dark asphalt

    # Add noise to simulate LiDAR intensity variation
    noise = np.random.normal(0, 8, (height, width))
    image = np.clip(image + noise, 0, 255).astype(np.uint8)

    # Road layout parameters
    road_width = 700  # pixels
    road_start_x = (width - road_width) // 2
    road_end_x = road_start_x + road_width

    # Lane parameters (European standard: 3.5m lanes)
    lane_width = road_width // 2  # Two lanes
    center_line_x = road_start_x + lane_width

    # European standard lane markings (high retroreflectivity)
    marking_intensity = 220  # Bright markings for LiDAR

    # 1. Center line (solid white - European standard)
    # Width: 10-15cm (Vienna Convention)
    center_line_width = 6
    cv2.rectangle(image,
                  (center_line_x - center_line_width//2, 0),
                  (center_line_x + center_line_width//2, height),
                  marking_intensity, -1)

    # 2. Left edge line (solid white)
    left_edge_x = road_start_x + 20
    edge_line_width = 8
    cv2.rectangle(image,
                  (left_edge_x - edge_line_width//2, 0),
                  (left_edge_x + edge_line_width//2, height),
                  marking_intensity, -1)

    # 3. Right edge line (solid white)
    right_edge_x = road_end_x - 20
    cv2.rectangle(image,
                  (right_edge_x - edge_line_width//2, 0),
                  (right_edge_x + edge_line_width//2, height),
                  marking_intensity, -1)

    # 4. Dashed guidance lines (European standard: 3m line, 9m gap)
    dash_length = 40
    gap_length = 120
    dash_width = 4

    # Left lane guidance (between left edge and center)
    left_guide_x = road_start_x + lane_width // 2
    y = 50
    while y < height - dash_length:
        cv2.rectangle(image,
                     (left_guide_x - dash_width//2, y),
                     (left_guide_x + dash_width//2, y + dash_length),
                     marking_intensity, -1)
        y += dash_length + gap_length

    # Right lane guidance (between center and right edge)
    right_guide_x = road_start_x + lane_width + lane_width // 2
    y = 80  # Offset for visual variety
    while y < height - dash_length:
        cv2.rectangle(image,
                     (right_guide_x - dash_width//2, y),
                     (right_guide_x + dash_width//2, y + dash_length),
                     marking_intensity, -1)
        y += dash_length + gap_length

    # 5. European road symbols/arrows
    # Add forward arrows (European standard)
    arrow_points = np.array([
        [center_line_x - 60, height//2],
        [center_line_x - 60, height//2 + 40],
        [center_line_x - 40, height//2 + 40],
        [center_line_x - 50, height//2 + 60],
        [center_line_x - 30, height//2 + 40],
        [center_line_x - 10, height//2 + 40],
        [center_line_x - 10, height//2],
        [center_line_x - 30, height//2],
        [center_line_x - 40, height//2 - 20],
        [center_line_x - 40, height//2]
    ], np.int32)

    cv2.fillPoly(image, [arrow_points], marking_intensity)

    # Mirror arrow for right lane
    arrow_points_right = arrow_points.copy()
    arrow_points_right[:, 0] += 120
    cv2.fillPoly(image, [arrow_points_right], marking_intensity)

    # 6. Add some road surface texture and wear patterns
    # Simulate tire tracks
    for i in range(2):
        track_x = road_start_x + (i + 0.3) * lane_width
        for y in range(0, height, 20):
            cv2.ellipse(image,
                       (int(track_x), y),
                       (8, 4), 0, 0, 360,
                       35, -1)  # Slightly darker tire tracks

    # 7. Add European-style crosswalk marking at bottom
    crosswalk_y = height - 80
    stripe_width = 8
    stripe_gap = 8
    for x in range(road_start_x, road_end_x, stripe_width + stripe_gap):
        cv2.rectangle(image,
                     (x, crosswalk_y),
                     (x + stripe_width, crosswalk_y + 40),
                     marking_intensity, -1)

    # Add some intensity variation to make it more realistic
    kernel = cv2.getStructuringElement(cv2.MORPH_ELLIPSE, (3, 3))
    image = cv2.morphologyEx(image, cv2.MORPH_CLOSE, kernel)

    # Final noise and blur to simulate LiDAR characteristics
    image = cv2.GaussianBlur(image, (3, 3), 0.5)

    return image

def main():
    # Generate the LiDAR image
    lidar_image = create_european_road_lidar_image()

    # Save to project root
    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(script_dir, "input_lidar_road.png")

    success = cv2.imwrite(output_path, lidar_image)
    if success:
        print(f"SUCCESS: LiDAR road image generated successfully: {output_path}")
        print("Image specifications:")
        print(f"  - Dimensions: {lidar_image.shape[1]}x{lidar_image.shape[0]} pixels")
        print(f"  - Format: Grayscale PNG (simulating LiDAR intensity)")
        print(f"  - European road standards applied:")
        print(f"    * Solid white center line (10-15cm width)")
        print(f"    * Solid edge lines")
        print(f"    * Dashed guidance lines (3m line, 9m gap)")
        print(f"    * Direction arrows")
        print(f"    * European-style crosswalk markings")
        print(f"    * Retroreflective intensity values (~220)")
    else:
        print("ERROR: Failed to save LiDAR image")
        return False

    return True

if __name__ == "__main__":
    main()