#!/usr/bin/env python3
"""
LAZ to PNG converter for LiDAR polygon detection pipeline.
This script converts LAZ files to intensity PNG images that can be processed by the lane detection system.
"""

import os
import sys
import subprocess
import tempfile

def convert_laz_to_png(laz_file, output_png, resolution=0.20):
    """
    Convert LAZ file to intensity PNG using PDAL and GDAL.
    
    Args:
        laz_file: Path to input LAZ file
        output_png: Path to output PNG file
        resolution: Resolution in meters per pixel
    """
    try:
        # Create temporary TIF file
        with tempfile.NamedTemporaryFile(suffix='.tif', delete=False) as tmp_tif:
            tif_path = tmp_tif.name
        
        # PDAL command to convert LAZ to intensity TIF
        pdal_cmd = [
            'pdal', 'translate', laz_file, tif_path, 'writers.gdal',
            '--writers.gdal.dimension=Intensity',
            '--writers.gdal.output_type=max',
            f'--writers.gdal.resolution={resolution}',
            '--writers.gdal.gdaldriver=GTiff',
            '--writers.gdal.nodata=0'
        ]
        
        print(f"Running PDAL: {' '.join(pdal_cmd)}")
        result = subprocess.run(pdal_cmd, capture_output=True, text=True)
        if result.returncode != 0:
            raise Exception(f"PDAL failed: {result.stderr}")
        
        # GDAL command to convert TIF to 8-bit PNG
        gdal_cmd = [
            'gdal_translate', '-of', 'PNG', '-ot', 'Byte', '-scale',
            tif_path, output_png
        ]
        
        print(f"Running GDAL: {' '.join(gdal_cmd)}")
        result = subprocess.run(gdal_cmd, capture_output=True, text=True)
        if result.returncode != 0:
            raise Exception(f"GDAL failed: {result.stderr}")
        
        # Clean up temporary TIF
        os.unlink(tif_path)
        
        print(f"Successfully converted {laz_file} to {output_png}")
        return True
        
    except Exception as e:
        print(f"Error converting LAZ file: {e}")
        return False

def check_dependencies():
    """Check if PDAL and GDAL are available."""
    try:
        subprocess.run(['pdal', '--version'], capture_output=True, check=True)
        subprocess.run(['gdalinfo', '--version'], capture_output=True, check=True)
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        return False

def main():
    if len(sys.argv) < 2:
        print("Usage: python3 process_laz.py <input.laz> [output.png] [resolution]")
        print("Example: python3 process_laz.py points.laz intensity.png 0.20")
        sys.exit(1)
    
    laz_file = sys.argv[1]
    output_png = sys.argv[2] if len(sys.argv) > 2 else 'intensity_output.png'
    resolution = float(sys.argv[3]) if len(sys.argv) > 3 else 0.20
    
    if not os.path.exists(laz_file):
        print(f"Error: LAZ file not found: {laz_file}")
        sys.exit(1)
    
    if not check_dependencies():
        print("Error: PDAL and GDAL are required but not available.")
        print("Install with: sudo apt-get install pdal gdal-bin")
        print("Or use Docker: docker run -v $(pwd):/data pdal/pdal:2.4-ubuntu")
        sys.exit(1)
    
    success = convert_laz_to_png(laz_file, output_png, resolution)
    if success:
        print(f"LAZ conversion completed: {output_png}")
        
        # Calculate ppm for reference
        ppm = 1.0 / resolution
        print(f"Pixels per meter (ppm): {ppm}")
        print(f"Use this PNG with the polygon detection API:")
        print(f"curl -X POST 'http://localhost:8090/api/polygons/overlay' -F 'data=@{output_png}' -o overlay_result.png")
    else:
        sys.exit(1)

if __name__ == "__main__":
    main()