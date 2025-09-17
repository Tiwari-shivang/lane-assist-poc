#!/usr/bin/env python3
"""
Generate Bird's Eye View (BEV) intensity images from Waymo TFRecord files.

This script processes Waymo Open Dataset TFRecord files and creates BEV intensity
images suitable for road marking detection. The output format is compatible with
the Spring Boot polygon detection API.
"""

import argparse
import json
import os
import sys
from pathlib import Path
from typing import List, Tuple, Optional, Dict, Any

import cv2
import numpy as np
import tensorflow as tf
from tqdm import tqdm

try:
    from waymo_open_dataset import dataset_pb2 as open_dataset
    from waymo_open_dataset.utils import frame_utils
    from waymo_open_dataset.utils import transform_utils
    from waymo_open_dataset.utils import range_image_utils
except ImportError as e:
    print(f"Error importing Waymo Open Dataset: {e}")
    print("Please install: pip install waymo-open-dataset-tf-2-15-0")
    sys.exit(1)


def parse_args() -> argparse.Namespace:
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(
        description="Generate BEV intensity images from Waymo TFRecords"
    )
    parser.add_argument(
        "--tfrecord", 
        required=True, 
        help="Path to input TFRecord file"
    )
    parser.add_argument(
        "--out", 
        required=True, 
        help="Output directory for BEV images"
    )
    parser.add_argument(
        "--res", 
        type=float, 
        default=0.20, 
        help="Resolution in meters per pixel (default: 0.20)"
    )
    parser.add_argument(
        "--extent", 
        type=float, 
        nargs=4, 
        default=[-80, 80, -80, 80], 
        metavar=("XMIN", "XMAX", "YMIN", "YMAX"),
        help="BEV extent in meters: xmin xmax ymin ymax (default: -80 80 -80 80)"
    )
    parser.add_argument(
        "--frames", 
        default="all", 
        help="Frame range to process: 'all', 'N' (single frame), or 'N..M' (range)"
    )
    parser.add_argument(
        "--composite", 
        action="store_true", 
        help="Accumulate all frames into one composite PNG"
    )
    parser.add_argument(
        "--reducer", 
        choices=["max", "mean"], 
        default="max", 
        help="Intensity reducer per cell (default: max)"
    )
    parser.add_argument(
        "--lidar-name", 
        default="TOP", 
        choices=["TOP", "FRONT", "SIDE_LEFT", "SIDE_RIGHT", "REAR", "ALL"], 
        help="LiDAR sensor to use (default: TOP)"
    )
    parser.add_argument(
        "--normalize-method", 
        choices=["minmax", "percentile"], 
        default="minmax", 
        help="Intensity normalization method (default: minmax)"
    )
    
    return parser.parse_args()


def parse_frame_range(frames_str: str) -> Tuple[Optional[int], Optional[int]]:
    """
    Parse frame range specification.
    
    Args:
        frames_str: Frame specification ("all", "N", or "N..M")
    
    Returns:
        Tuple of (start_frame, end_frame) or (None, None) for all frames
    """
    if frames_str == "all":
        return None, None
    
    if ".." in frames_str:
        start_str, end_str = frames_str.split("..", 1)
        start_frame = int(start_str) if start_str else 0
        end_frame = int(end_str) if end_str else None
        return start_frame, end_frame
    else:
        frame_num = int(frames_str)
        return frame_num, frame_num


def extract_lidar_points_with_intensity(frame: open_dataset.Frame, 
                                       lidar_names: List[str]) -> Tuple[np.ndarray, np.ndarray]:
    """
    Extract LiDAR points with intensity from specified sensors.
    
    Args:
        frame: Waymo frame containing LiDAR data
        lidar_names: List of LiDAR sensor names to extract
    
    Returns:
        Tuple of (points, intensities) where:
        - points: Nx3 array of XYZ coordinates
        - intensities: Nx1 array of intensity values
    """
    lidar_name_to_enum = {
        "TOP": open_dataset.LaserName.TOP,
        "FRONT": open_dataset.LaserName.FRONT,
        "SIDE_LEFT": open_dataset.LaserName.SIDE_LEFT,
        "SIDE_RIGHT": open_dataset.LaserName.SIDE_RIGHT,
        "REAR": open_dataset.LaserName.REAR
    }
    
    # Parse range images and camera projections
    (range_images, camera_projections, _, range_image_top_pose) = (
        frame_utils.parse_range_image_and_camera_projection(frame)
    )
    
    points_all = []
    intensities_all = []
    
    for c in frame.context.laser_calibrations:
        if c.name in [lidar_name_to_enum[name] for name in lidar_names if name in lidar_name_to_enum]:
            # Get range images for this LiDAR
            range_image_1st = range_images[c.name][0]
            
            if range_image_1st is not None:
                # Extract points and intensity from first return
                points, intensity = frame_utils.convert_range_image_to_point_cloud(
                    frame, range_images, camera_projections, range_image_top_pose, c.name
                )
                
                if points is not None and points.shape[0] > 0:
                    points_all.append(points)
                    if intensity is not None:
                        intensities_all.append(intensity)
                    else:
                        # If no intensity, use default values
                        intensities_all.append(np.ones((points.shape[0],), dtype=np.float32))
    
    # Concatenate all points and intensities
    if points_all:
        all_points = np.concatenate(points_all, axis=0)
        all_intensities = np.concatenate(intensities_all, axis=0)
    else:
        all_points = np.empty((0, 3), dtype=np.float32)
        all_intensities = np.empty((0,), dtype=np.float32)
    
    return all_points, all_intensities


def rasterize_bev(points: np.ndarray, 
                  intensities: np.ndarray, 
                  extent: List[float], 
                  resolution: float, 
                  reducer: str = "max") -> np.ndarray:
    """
    Rasterize 3D points into a 2D BEV intensity grid.
    
    Args:
        points: Nx3 array of XYZ coordinates
        intensities: Nx1 array of intensity values
        extent: [xmin, xmax, ymin, ymax] in meters
        resolution: Meters per pixel
        reducer: Intensity reduction method ("max" or "mean")
    
    Returns:
        2D intensity grid as numpy array
    """
    xmin, xmax, ymin, ymax = extent
    
    # Calculate grid dimensions
    width = int((xmax - xmin) / resolution)
    height = int((ymax - ymin) / resolution)
    
    # Initialize intensity grid
    intensity_grid = np.zeros((height, width), dtype=np.float32)
    count_grid = np.zeros((height, width), dtype=np.int32)
    
    if points.shape[0] == 0:
        return intensity_grid
    
    # Convert world coordinates to pixel coordinates
    x_coords = points[:, 0]
    y_coords = points[:, 1]
    
    # Filter points within extent
    valid_mask = (
        (x_coords >= xmin) & (x_coords < xmax) &
        (y_coords >= ymin) & (y_coords < ymax)
    )
    
    if not np.any(valid_mask):
        return intensity_grid
    
    valid_x = x_coords[valid_mask]
    valid_y = y_coords[valid_mask]
    valid_intensities = intensities[valid_mask]
    
    # Convert to pixel coordinates
    pixel_x = ((valid_x - xmin) / resolution).astype(np.int32)
    pixel_y = ((valid_y - ymin) / resolution).astype(np.int32)
    
    # Clamp to grid boundaries
    pixel_x = np.clip(pixel_x, 0, width - 1)
    pixel_y = np.clip(pixel_y, 0, height - 1)
    
    # Apply reducer
    if reducer == "max":
        # Use maximum intensity per cell
        for i in range(len(pixel_x)):
            px, py = pixel_x[i], pixel_y[i]
            intensity_grid[py, px] = max(intensity_grid[py, px], valid_intensities[i])
            count_grid[py, px] += 1
    elif reducer == "mean":
        # Use mean intensity per cell
        for i in range(len(pixel_x)):
            px, py = pixel_x[i], pixel_y[i]
            intensity_grid[py, px] += valid_intensities[i]
            count_grid[py, px] += 1
        
        # Calculate mean where count > 0
        nonzero_mask = count_grid > 0
        intensity_grid[nonzero_mask] /= count_grid[nonzero_mask]
    
    return intensity_grid


def normalize_intensity(intensity_grid: np.ndarray, method: str = "minmax") -> np.ndarray:
    """
    Normalize intensity values to 0-255 range for PNG output.
    
    Args:
        intensity_grid: 2D intensity array
        method: Normalization method ("minmax" or "percentile")
    
    Returns:
        Normalized 8-bit intensity array
    """
    if method == "minmax":
        # Min-max normalization
        min_val = np.min(intensity_grid)
        max_val = np.max(intensity_grid)
        
        if max_val > min_val:
            normalized = (intensity_grid - min_val) / (max_val - min_val) * 255.0
        else:
            normalized = np.zeros_like(intensity_grid)
    
    elif method == "percentile":
        # Percentile-based normalization (more robust to outliers)
        p1, p99 = np.percentile(intensity_grid[intensity_grid > 0], [1, 99])
        
        if p99 > p1:
            normalized = np.clip((intensity_grid - p1) / (p99 - p1) * 255.0, 0, 255)
        else:
            normalized = np.zeros_like(intensity_grid)
    
    return normalized.astype(np.uint8)


def save_png_with_metadata(intensity_image: np.ndarray, 
                          output_path: str, 
                          extent: List[float], 
                          resolution: float) -> None:
    """
    Save intensity image as PNG with accompanying metadata JSON.
    
    Args:
        intensity_image: 2D intensity array (uint8)
        output_path: Path for PNG file
        extent: [xmin, xmax, ymin, ymax] in meters
        resolution: Meters per pixel
    """
    # Save PNG
    cv2.imwrite(output_path, intensity_image)
    
    # Create metadata
    ppm = 1.0 / resolution
    metadata = {
        "resolution_m_per_px": resolution,
        "ppm": ppm,
        "extent": extent,
        "image_shape": intensity_image.shape,
        "description": "BEV intensity image from Waymo LiDAR data"
    }
    
    # Save metadata JSON
    metadata_path = output_path.replace('.png', '_metadata.json')
    with open(metadata_path, 'w') as f:
        json.dump(metadata, f, indent=2)


def main():
    """Main execution function."""
    args = parse_args()
    
    # Validate inputs
    if not os.path.exists(args.tfrecord):
        print(f"Error: TFRecord file not found: {args.tfrecord}")
        sys.exit(1)
    
    # Create output directory
    output_dir = Path(args.out)
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # Parse frame range
    start_frame, end_frame = parse_frame_range(args.frames)
    
    # Determine LiDAR sensors to use
    if args.lidar_name == "ALL":
        lidar_names = ["TOP", "FRONT", "SIDE_LEFT", "SIDE_RIGHT", "REAR"]
    else:
        lidar_names = [args.lidar_name]
    
    print(f"Processing TFRecord: {args.tfrecord}")
    print(f"Output directory: {output_dir}")
    print(f"Resolution: {args.res} m/px (PPM: {1.0/args.res:.1f})")
    print(f"Extent: {args.extent}")
    print(f"LiDAR sensors: {lidar_names}")
    print(f"Reducer: {args.reducer}")
    print(f"Composite mode: {args.composite}")
    print()
    
    # Initialize composite accumulator if needed
    composite_grid = None
    
    # Process TFRecord
    dataset = tf.data.TFRecordDataset(args.tfrecord, compression_type='')
    
    frame_count = 0
    processed_count = 0
    
    for data in tqdm(dataset, desc="Processing frames"):
        # Parse frame
        frame = open_dataset.Frame()
        frame.ParseFromString(bytearray(data.numpy()))
        
        # Check frame range
        if start_frame is not None and frame_count < start_frame:
            frame_count += 1
            continue
        if end_frame is not None and frame_count > end_frame:
            break
        
        try:
            # Extract LiDAR points with intensity
            points, intensities = extract_lidar_points_with_intensity(frame, lidar_names)
            
            if points.shape[0] == 0:
                print(f"Warning: No points found in frame {frame_count}")
                frame_count += 1
                continue
            
            # Rasterize to BEV
            intensity_grid = rasterize_bev(
                points, intensities, args.extent, args.res, args.reducer
            )
            
            if args.composite:
                # Accumulate for composite
                if composite_grid is None:
                    composite_grid = intensity_grid.copy()
                else:
                    if args.reducer == "max":
                        composite_grid = np.maximum(composite_grid, intensity_grid)
                    else:  # mean
                        composite_grid = (composite_grid + intensity_grid) / 2.0
            else:
                # Save individual frame
                normalized_image = normalize_intensity(intensity_grid, args.normalize_method)
                output_filename = f"frame_{frame_count:06d}.png"
                output_path = output_dir / output_filename
                
                save_png_with_metadata(
                    normalized_image, str(output_path), args.extent, args.res
                )
                
                print(f"Frame {frame_count}: {points.shape[0]} points -> {output_filename}")
            
            processed_count += 1
            
        except Exception as e:
            print(f"Error processing frame {frame_count}: {e}")
        
        frame_count += 1
    
    # Save composite if requested
    if args.composite and composite_grid is not None:
        normalized_image = normalize_intensity(composite_grid, args.normalize_method)
        output_filename = "composite.png"
        output_path = output_dir / output_filename
        
        save_png_with_metadata(
            normalized_image, str(output_path), args.extent, args.res
        )
        
        print(f"Composite: {processed_count} frames -> {output_filename}")
    
    print(f"\nProcessing complete!")
    print(f"Processed {processed_count} frames")
    print(f"Output files saved to: {output_dir}")
    
    # Create a summary metadata file
    summary_path = output_dir / "processing_summary.json"
    summary = {
        "tfrecord_file": args.tfrecord,
        "frames_processed": processed_count,
        "total_frames": frame_count,
        "resolution_m_per_px": args.res,
        "ppm": 1.0 / args.res,
        "extent": args.extent,
        "lidar_sensors": lidar_names,
        "reducer": args.reducer,
        "composite_mode": args.composite,
        "normalize_method": args.normalize_method
    }
    
    with open(summary_path, 'w') as f:
        json.dump(summary, f, indent=2)


if __name__ == "__main__":
    main()