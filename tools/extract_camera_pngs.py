#!/usr/bin/env python3
"""
Extract camera images from Waymo TFRecord files to PNG format.

This script processes Waymo Open Dataset TFRecord files and extracts camera
images from all 5 cameras per frame, saving them as PNG files for cross-checking
or training RGB models.
"""

import argparse
import os
import sys
from pathlib import Path
from typing import Dict, List

import numpy as np
import tensorflow as tf
from PIL import Image
from tqdm import tqdm

try:
    from waymo_open_dataset import dataset_pb2 as open_dataset
except ImportError as e:
    print(f"Error importing Waymo Open Dataset: {e}")
    print("Please install: pip install waymo-open-dataset-tf-2-15-0")
    sys.exit(1)


def parse_args() -> argparse.Namespace:
    """Parse command line arguments."""
    parser = argparse.ArgumentParser(
        description="Extract camera images from Waymo TFRecords to PNG format"
    )
    parser.add_argument(
        "--tfrecord", 
        required=True, 
        help="Path to input TFRecord file"
    )
    parser.add_argument(
        "--out", 
        required=True, 
        help="Output directory for camera PNG files"
    )
    parser.add_argument(
        "--every", 
        type=int, 
        default=1, 
        help="Extract every Nth frame (default: 1)"
    )
    parser.add_argument(
        "--cameras", 
        nargs="+", 
        choices=["FRONT", "FRONT_LEFT", "FRONT_RIGHT", "SIDE_LEFT", "SIDE_RIGHT"], 
        default=["FRONT", "FRONT_LEFT", "FRONT_RIGHT", "SIDE_LEFT", "SIDE_RIGHT"],
        help="Camera sensors to extract (default: all)"
    )
    parser.add_argument(
        "--max-frames", 
        type=int, 
        help="Maximum number of frames to process"
    )
    parser.add_argument(
        "--quality", 
        type=int, 
        default=95, 
        help="JPEG quality for PNG conversion (default: 95)"
    )
    
    return parser.parse_args()


def get_camera_name_mapping() -> Dict[int, str]:
    """Get mapping from camera enum values to readable names."""
    return {
        open_dataset.CameraName.FRONT: "FRONT",
        open_dataset.CameraName.FRONT_LEFT: "FRONT_LEFT", 
        open_dataset.CameraName.FRONT_RIGHT: "FRONT_RIGHT",
        open_dataset.CameraName.SIDE_LEFT: "SIDE_LEFT",
        open_dataset.CameraName.SIDE_RIGHT: "SIDE_RIGHT"
    }


def extract_camera_images(frame: open_dataset.Frame, 
                         camera_names: List[str]) -> Dict[str, np.ndarray]:
    """
    Extract camera images from a Waymo frame.
    
    Args:
        frame: Waymo frame containing camera data
        camera_names: List of camera names to extract
    
    Returns:
        Dictionary mapping camera names to image arrays
    """
    camera_name_mapping = get_camera_name_mapping()
    reverse_mapping = {v: k for k, v in camera_name_mapping.items()}
    
    extracted_images = {}
    
    for image in frame.images:
        camera_enum = image.name
        camera_name = camera_name_mapping.get(camera_enum, f"UNKNOWN_{camera_enum}")
        
        if camera_name in camera_names:
            # Decode the image
            image_data = tf.io.decode_jpeg(image.image)
            image_array = image_data.numpy()
            
            extracted_images[camera_name] = image_array
    
    return extracted_images


def save_camera_image(image_array: np.ndarray, 
                     output_path: str) -> None:
    """
    Save camera image array as PNG file.
    
    Args:
        image_array: Image array (H, W, C)
        output_path: Path to output PNG file
    """
    # Convert to PIL Image and save as PNG
    if image_array.dtype != np.uint8:
        # Normalize to uint8 if needed
        if image_array.max() <= 1.0:
            image_array = (image_array * 255).astype(np.uint8)
        else:
            image_array = image_array.astype(np.uint8)
    
    # Handle different channel formats
    if len(image_array.shape) == 3:
        if image_array.shape[2] == 3:
            # RGB image
            pil_image = Image.fromarray(image_array, 'RGB')
        elif image_array.shape[2] == 1:
            # Grayscale image
            pil_image = Image.fromarray(image_array.squeeze(), 'L')
        else:
            # Unknown format, treat as RGB
            pil_image = Image.fromarray(image_array[:, :, :3], 'RGB')
    else:
        # Grayscale image
        pil_image = Image.fromarray(image_array, 'L')
    
    # Save as PNG
    pil_image.save(output_path, 'PNG', optimize=True)


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
    
    # Create subdirectories for each camera
    for camera_name in args.cameras:
        camera_dir = output_dir / camera_name.lower()
        camera_dir.mkdir(exist_ok=True)
    
    print(f"Processing TFRecord: {args.tfrecord}")
    print(f"Output directory: {output_dir}")
    print(f"Cameras: {args.cameras}")
    print(f"Every {args.every} frames")
    print()
    
    # Process TFRecord
    dataset = tf.data.TFRecordDataset(args.tfrecord, compression_type='')
    
    frame_count = 0
    processed_count = 0
    image_counts = {camera: 0 for camera in args.cameras}
    
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
            # Extract camera images
            camera_images = extract_camera_images(frame, args.cameras)
            
            if not camera_images:
                print(f"Warning: No camera images found in frame {frame_count}")
                frame_count += 1
                continue
            
            # Save each camera image
            for camera_name, image_array in camera_images.items():
                # Generate output filename
                camera_dir = output_dir / camera_name.lower()
                output_filename = f"cam_{camera_name.lower()}_{frame_count:06d}.png"
                output_path = camera_dir / output_filename
                
                # Save image
                save_camera_image(image_array, str(output_path))
                image_counts[camera_name] += 1
            
            print(f"Frame {frame_count}: {len(camera_images)} camera images saved")
            processed_count += 1
            
        except Exception as e:
            print(f"Error processing frame {frame_count}: {e}")
        
        frame_count += 1
    
    print(f"\nProcessing complete!")
    print(f"Processed {processed_count} frames from {frame_count} total frames")
    print("Images per camera:")
    for camera_name, count in image_counts.items():
        print(f"  {camera_name}: {count} images")
    print(f"Output files saved to: {output_dir}")
    
    # Create a summary file
    summary_path = output_dir / "extraction_summary.txt"
    with open(summary_path, 'w') as f:
        f.write(f"Camera Image Extraction Summary\n")
        f.write(f"===============================\n\n")
        f.write(f"TFRecord file: {args.tfrecord}\n")
        f.write(f"Frames processed: {processed_count}\n")
        f.write(f"Total frames: {frame_count}\n")
        f.write(f"Every {args.every} frames\n\n")
        f.write(f"Images per camera:\n")
        for camera_name, count in image_counts.items():
            f.write(f"  {camera_name}: {count} images\n")
        f.write(f"\nOutput directory structure:\n")
        for camera_name in args.cameras:
            f.write(f"  {camera_name.lower()}/\n")


if __name__ == "__main__":
    main()