#!/bin/bash

# Waymo Tools Environment Setup Script
# This script sets up the Python environment for Waymo TFRecord processing

set -e

echo "🚀 Setting up Waymo Tools Environment"
echo "====================================="

# Check if Python 3 is available
if ! command -v python3 &> /dev/null; then
    echo "❌ Python 3 is not installed. Please install Python 3.10+ first."
    exit 1
fi

PYTHON_VERSION=$(python3 -c "import sys; print('.'.join(map(str, sys.version_info[:2])))")
echo "🐍 Found Python $PYTHON_VERSION"

# Create virtual environment in tools directory
echo "📦 Creating virtual environment..."
cd tools
python3 -m venv waymo_env

# Activate virtual environment
echo "🔌 Activating virtual environment..."
source waymo_env/bin/activate

# Upgrade pip
echo "⬆️  Upgrading pip..."
pip install --upgrade pip

# Install requirements
echo "📥 Installing Python packages..."
pip install -r requirements.txt

echo ""
echo "✅ Environment setup complete!"
echo ""
echo "To use the Waymo tools:"
echo "1. Activate the environment:"
echo "   cd tools && source waymo_env/bin/activate"
echo ""
echo "2. Test the installation:"
echo "   cd .. && python test_waymo_tools.py"
echo ""
echo "3. Generate BEV images:"
echo "   python tools/make_bev_intensity.py --tfrecord data/your_file.tfrecord --out out/bev --res 0.20"
echo ""
echo "4. Send to Spring Boot polygon detection:"
echo "   curl -X POST \"http://localhost:8090/api/polygons/overlay\" -F \"data=@out/bev/frame_000000.png\" -o result.png"
echo ""

# Optional: Test dependencies
read -p "Do you want to test the installation now? (y/n): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "🧪 Testing installation..."
    
    # Test TensorFlow
    echo -n "Testing TensorFlow... "
    if python -c "import tensorflow as tf; print(f'v{tf.__version__}')" 2>/dev/null; then
        echo "✅"
    else
        echo "❌"
    fi
    
    # Test Waymo Open Dataset
    echo -n "Testing Waymo Open Dataset... "
    if python -c "import waymo_open_dataset; print('OK')" 2>/dev/null; then
        echo "✅"
    else
        echo "❌"
    fi
    
    # Test OpenCV
    echo -n "Testing OpenCV... "
    if python -c "import cv2; print(f'v{cv2.__version__}')" 2>/dev/null; then
        echo "✅"
    else
        echo "❌"
    fi
    
    echo ""
    echo "🎉 Installation test complete!"
fi

# Deactivate virtual environment
deactivate

echo ""
echo "Environment is ready! Run the commands above to get started."