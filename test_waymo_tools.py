#!/usr/bin/env python3
"""
Test script for Waymo extraction tools.
This verifies that the tools can process the provided TFRecord file.
"""

import os
import sys
import subprocess
from pathlib import Path

def test_dependencies():
    """Test that all required dependencies are available."""
    print("🔍 Testing dependencies...")
    
    try:
        import tensorflow as tf
        print(f"✅ TensorFlow: {tf.__version__}")
    except ImportError:
        print("❌ TensorFlow not found")
        return False
    
    try:
        import waymo_open_dataset
        print("✅ Waymo Open Dataset: Available")
    except ImportError:
        print("❌ Waymo Open Dataset not found")
        print("   Install with: pip install waymo-open-dataset-tf-2-15-0")
        return False
    
    try:
        import cv2
        print(f"✅ OpenCV: {cv2.__version__}")
    except ImportError:
        print("❌ OpenCV not found")
        return False
    
    try:
        import numpy as np
        print(f"✅ NumPy: {np.__version__}")
    except ImportError:
        print("❌ NumPy not found")
        return False
    
    return True

def test_tfrecord_file():
    """Test that the TFRecord file exists and is readable."""
    print("\n📁 Testing TFRecord file...")
    
    tfrecord_path = "data/individual_files_testing_segment-10084636266401282188_1120_000_1140_000_with_camera_labels.tfrecord"
    
    if not os.path.exists(tfrecord_path):
        print(f"❌ TFRecord file not found: {tfrecord_path}")
        return False
    
    file_size = os.path.getsize(tfrecord_path) / (1024 * 1024)  # MB
    print(f"✅ TFRecord file found: {file_size:.1f} MB")
    
    return True

def run_tool_test(script_name, args, expected_output_dir):
    """Run a tool with test arguments and check output."""
    print(f"\n🧪 Testing {script_name}...")
    
    # Create output directory
    os.makedirs(expected_output_dir, exist_ok=True)
    
    # Build command
    cmd = [sys.executable, f"tools/{script_name}"] + args
    
    print(f"Running: {' '.join(cmd)}")
    
    try:
        # Run the command
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=300  # 5 minute timeout
        )
        
        if result.returncode == 0:
            print(f"✅ {script_name} completed successfully")
            
            # Check if output files were created
            output_files = list(Path(expected_output_dir).glob("*"))
            print(f"   Created {len(output_files)} output files")
            return True
        else:
            print(f"❌ {script_name} failed with return code {result.returncode}")
            print(f"   stderr: {result.stderr}")
            return False
            
    except subprocess.TimeoutExpired:
        print(f"❌ {script_name} timed out after 5 minutes")
        return False
    except Exception as e:
        print(f"❌ {script_name} failed with exception: {e}")
        return False

def main():
    """Main test function."""
    print("🚀 Waymo Tools Test Suite")
    print("=" * 50)
    
    # Test 1: Dependencies
    if not test_dependencies():
        print("\n❌ Dependency test failed. Please install required packages.")
        sys.exit(1)
    
    # Test 2: TFRecord file
    if not test_tfrecord_file():
        print("\n❌ TFRecord file test failed.")
        sys.exit(1)
    
    # Test 3: Extract a few LiDAR frames
    test_success = True
    
    lidar_args = [
        "--tfrecord", "data/individual_files_testing_segment-10084636266401282188_1120_000_1140_000_with_camera_labels.tfrecord",
        "--out", "out/test_ply",
        "--every", "50",  # Every 50th frame for quick test
        "--max-frames", "2",  # Only 2 frames
        "--include-intensity"
    ]
    
    if not run_tool_test("extract_lidar_to_ply.py", lidar_args, "out/test_ply"):
        test_success = False
    
    # Test 4: Generate a few BEV images
    bev_args = [
        "--tfrecord", "data/individual_files_testing_segment-10084636266401282188_1120_000_1140_000_with_camera_labels.tfrecord",
        "--out", "out/test_bev",
        "--res", "0.20",
        "--extent", "-80", "80", "-80", "80",
        "--frames", "0..2",  # First 3 frames only
        "--lidar-name", "TOP"
    ]
    
    if not run_tool_test("make_bev_intensity.py", bev_args, "out/test_bev"):
        test_success = False
    
    # Test 5: Extract a few camera images
    camera_args = [
        "--tfrecord", "data/individual_files_testing_segment-10084636266401282188_1120_000_1140_000_with_camera_labels.tfrecord",
        "--out", "out/test_camera",
        "--every", "50",  # Every 50th frame
        "--max-frames", "1",  # Only 1 frame
        "--cameras", "FRONT"  # Only front camera
    ]
    
    if not run_tool_test("extract_camera_pngs.py", camera_args, "out/test_camera"):
        test_success = False
    
    # Summary
    print("\n" + "=" * 50)
    if test_success:
        print("🎉 All tests passed! Waymo tools are ready to use.")
        print("\nNext steps:")
        print("1. Review the output files in out/test_* directories")
        print("2. Use tools/make_bev_intensity.py to generate images for Spring Boot")
        print("3. Send BEV images to the polygon detection API")
    else:
        print("❌ Some tests failed. Please check the error messages above.")
        sys.exit(1)

if __name__ == "__main__":
    main()