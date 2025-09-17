#!/usr/bin/env python3
"""
Extract LiDAR point clouds from Waymo TFRecord files to PLY format.

This script processes Waymo Open Dataset TFRecord files and extracts per-frame
LiDAR point clouds, saving them as PLY files (ASCII format with x,y,z coordinates
and optional intensity values).
"""

import argparse
import os
import sys
import subprocess
from pathlib import Path
from typing import List, Tuple, Optional

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
        description="Extract LiDAR point clouds from Waymo TFRecords to PLY format"
    )
    parser.add_argument(
        "--tfrecord", 
        required=True, 
        help="Path to input TFRecord file"
    )
    parser.add_argument(
        "--out", 
        required=True, 
        help="Output directory for PLY files"
    )
    parser.add_argument(
        "--every", 
        type=int, 
        default=1, 
        help="Export every Nth frame (default: 1)"
    )
    parser.add_argument(
        "--to-laz", 
        action="store_true", 
        help="Convert PLY to LAZ using PDAL (requires PDAL in PATH)"
    )
    parser.add_argument(
        "--include-intensity", 
        action="store_true", 
        help="Include intensity values in PLY output"
    )
    parser.add_argument(
        "--lidar-name", 
        default="TOP", 
        choices=["TOP", "FRONT", "SIDE_LEFT", "SIDE_RIGHT", "REAR"], 
        help="LiDAR sensor to extract (default: TOP)"
    )
    parser.add_argument(
        "--max-frames", 
        type=int, 
        help="Maximum number of frames to process"
    )
    
    return parser.parse_args()


def check_pdal_available() -> bool:
    """Check if PDAL is available in PATH."""
    try:
        result = subprocess.run(
            ["pdal", "--version"], 
            capture_output=True, 
            text=True, 
            timeout=10
        )
        return result.returncode == 0
    except (subprocess.TimeoutExpired, FileNotFoundError):
        return False


def extract_lidar_points(frame: open_dataset.Frame, 
                        lidar_name: str = "TOP", 
                        include_intensity: bool = False) -> Tuple[np.ndarray, Optional[np.ndarray]]:
    """
    Extract LiDAR points from a Waymo frame.
    
    Args:
        frame: Waymo frame containing LiDAR data
        lidar_name: Name of LiDAR sensor to extract
        include_intensity: Whether to extract intensity values
    
    Returns:
        Tuple of (points, intensities) where:
        - points: Nx3 array of XYZ coordinates
        - intensities: Nx1 array of intensity values (if requested)
    """
    # Get the LiDAR data for the specified sensor
    lidar_name_to_enum = {
        "TOP": open_dataset.LaserName.TOP,
        "FRONT": open_dataset.LaserName.FRONT,
        "SIDE_LEFT": open_dataset.LaserName.SIDE_LEFT,
        "SIDE_RIGHT": open_dataset.LaserName.SIDE_RIGHT,
        "REAR": open_dataset.LaserName.REAR
    }
    
    if lidar_name not in lidar_name_to_enum:
        raise ValueError(f"Unknown LiDAR name: {lidar_name}")
    
    lidar_enum = lidar_name_to_enum[lidar_name]
    
    # Parse range images and camera projections
    (range_images, camera_projections, _, range_image_top_pose) = (
        frame_utils.parse_range_image_and_camera_projection(frame)
    )
    
    # Convert range images to point clouds
    points_all = []
    intensities_all = []
    
    for c in frame.context.laser_calibrations:
        if c.name == lidar_enum:
            # Get range images for this LiDAR
            range_image_1st = range_images[c.name][0]
            range_image_2nd = range_images[c.name][1]
            
            if range_image_1st is not None:
                # First return
                points_1st, intensity_1st = frame_utils.convert_range_image_to_point_cloud(
                    frame, range_images, camera_projections, range_image_top_pose, c.name
                )
                points_all.append(points_1st)
                if include_intensity and intensity_1st is not None:
                    intensities_all.append(intensity_1st)
            
            if range_image_2nd is not None:
                # Second return
                points_2nd, intensity_2nd = frame_utils.convert_range_image_to_point_cloud(
                    frame, range_images, camera_projections, range_image_top_pose, c.name, ri_index=1
                )
                points_all.append(points_2nd)
                if include_intensity and intensity_2nd is not None:
                    intensities_all.append(intensity_2nd)
    
    # Concatenate all points
    if points_all:
        points = np.concatenate(points_all, axis=0)
        intensities = np.concatenate(intensities_all, axis=0) if intensities_all else None
    else:
        points = np.empty((0, 3))
        intensities = None
    
    return points, intensities


def write_ply(points: np.ndarray, 
              output_path: str, 
              intensities: Optional[np.ndarray] = None) -> None:
    """
    Write points to PLY file in ASCII format.
    
    Args:
        points: Nx3 array of XYZ coordinates
        output_path: Path to output PLY file
        intensities: Optional Nx1 array of intensity values
    """
    num_points = points.shape[0]
    
    with open(output_path, 'w') as f:
        # PLY header
        f.write("ply\n")
        f.write("format ascii 1.0\n")
        f.write(f"element vertex {num_points}\n")
        f.write("property float x\n")
        f.write("property float y\n")
        f.write("property float z\n")
        
        if intensities is not None:
            f.write("property float intensity\n")
        
        f.write("end_header\n")
        
        # Write vertex data
        for i in range(num_points):
            x, y, z = points[i]
            if intensities is not None:
                intensity = intensities[i] if i < len(intensities) else 0.0
                f.write(f"{x:.6f} {y:.6f} {z:.6f} {intensity:.6f}\n")
            else:
                f.write(f"{x:.6f} {y:.6f} {z:.6f}\n")


def convert_ply_to_laz(ply_path: str) -> bool:
    """
    Convert PLY file to LAZ using PDAL.
    
    Args:
        ply_path: Path to input PLY file
    
    Returns:
        True if conversion successful, False otherwise
    """
    laz_path = ply_path.replace('.ply', '.laz')
    
    try:
        result = subprocess.run([
            "pdal", "translate", ply_path, laz_path
        ], capture_output=True, text=True, timeout=60)
        
        if result.returncode == 0:
            print(f"  Converted to LAZ: {laz_path}")
            return True
        else:
            print(f"  PDAL conversion failed: {result.stderr}")
            return False
            
    except (subprocess.TimeoutExpired, FileNotFoundError) as e:
        print(f"  PDAL conversion error: {e}")
        return False


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
    
    # Check PDAL availability if requested
    if args.to_laz and not check_pdal_available():
        print("Warning: PDAL not found in PATH. LAZ conversion will be skipped.")
        args.to_laz = False
    
    # Process TFRecord
    dataset = tf.data.TFRecordDataset(args.tfrecord, compression_type='')
    
    print(f"Processing TFRecord: {args.tfrecord}")
    print(f"Output directory: {output_dir}")
    print(f"LiDAR sensor: {args.lidar_name}")
    print(f"Every {args.every} frames")
    print(f"Include intensity: {args.include_intensity}")
    print(f"Convert to LAZ: {args.to_laz}")
    print()
    
    frame_count = 0
    processed_count = 0
    
    for data in tqdm(dataset, desc="Processing frames"):
        # Parse frame
        frame = open_dataset.Frame()
        frame.ParseFromString(bytearray(data.numpy()))
        
        # Check if we should process this frame
        if frame_count % args.every != 0:
            frame_count += 1
            continue
        
        # Check max frames limit
        if args.max_frames and processed_count >= args.max_frames:
            break
        
        try:
            # Extract LiDAR points
            points, intensities = extract_lidar_points(
                frame, 
                args.lidar_name, 
                args.include_intensity
            )
            
            if points.shape[0] == 0:
                print(f"Warning: No points found in frame {frame_count}")
                frame_count += 1
                continue
            
            # Generate output filename
            output_filename = f"frame_{frame_count:06d}.ply"
            output_path = output_dir / output_filename
            
            # Write PLY file
            write_ply(points, str(output_path), intensities)
            
            print(f"Frame {frame_count}: {points.shape[0]} points -> {output_filename}")
            
            # Convert to LAZ if requested
            if args.to_laz:
                convert_ply_to_laz(str(output_path))
            
            processed_count += 1
            
        except Exception as e:
            print(f"Error processing frame {frame_count}: {e}")
        
        frame_count += 1
    
    print(f"\nProcessing complete!")
    print(f"Processed {processed_count} frames from {frame_count} total frames")
    print(f"Output files saved to: {output_dir}")


if __name__ == "__main__":
    main()