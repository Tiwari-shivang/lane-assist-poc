#!/bin/bash

# MaskRCNN Integration Setup Script for Lane-Assist
# This script helps set up the MaskRCNN inference environment

set -e

echo "🚀 Setting up MaskRCNN Integration for Lane-Assist"
echo "=================================================="

# Check if Docker is installed
if ! command -v docker &> /dev/null; then
    echo "❌ Docker is not installed. Please install Docker first."
    exit 1
fi

# Check if Docker Compose is installed
if ! command -v docker-compose &> /dev/null; then
    echo "❌ Docker Compose is not installed. Please install Docker Compose first."
    exit 1
fi

# Create necessary directories
echo "📁 Creating directories..."
mkdir -p inference/out
mkdir -p dataset/train/images
mkdir -p dataset/train/annotations
mkdir -p dataset/val/images
mkdir -p dataset/val/annotations
mkdir -p data

echo "✅ Directories created"

# Check if Python is available for local development
if command -v python3 &> /dev/null; then
    echo "🐍 Python3 is available for local development"
    
    read -p "Do you want to set up a Python virtual environment for local development? (y/n): " setup_venv
    
    if [[ $setup_venv == "y" || $setup_venv == "Y" ]]; then
        echo "📦 Setting up Python virtual environment..."
        cd inference
        python3 -m venv venv
        source venv/bin/activate
        pip install --upgrade pip
        
        echo "📥 Installing Python dependencies..."
        pip install -r requirements.txt
        
        echo "✅ Python environment set up successfully"
        echo "To activate: cd inference && source venv/bin/activate"
        cd ..
    fi
else
    echo "⚠️  Python3 not found. You'll need to use Docker for inference service."
fi

# Download sample COCO annotation template
echo "📋 Creating sample annotation template..."
cat > dataset/train/annotations.json << 'EOF'
{
  "info": {
    "description": "Road Markings Dataset",
    "version": "1.0",
    "year": 2025,
    "contributor": "Lane-Assist",
    "date_created": "2025-09-17"
  },
  "licenses": [
    {
      "id": 1,
      "name": "Attribution-NonCommercial",
      "url": "http://creativecommons.org/licenses/by-nc/2.0/"
    }
  ],
  "categories": [
    {"id": 1, "name": "lane_leg", "supercategory": "marking"},
    {"id": 2, "name": "junction_core", "supercategory": "marking"},
    {"id": 3, "name": "zebra_crossing", "supercategory": "marking"},
    {"id": 4, "name": "stop_line", "supercategory": "marking"},
    {"id": 5, "name": "arrow", "supercategory": "marking"}
  ],
  "images": [],
  "annotations": []
}
EOF

cp dataset/train/annotations.json dataset/val/annotations.json

echo "✅ Sample annotation templates created"

# Create environment file for Docker
echo "🔧 Creating environment configuration..."
cat > .env << 'EOF'
# MaskRCNN Inference Service
INFERENCE_SERVICE_URL=http://localhost:8000

# Spring Boot Configuration
SPRING_PROFILES_ACTIVE=default
SERVER_PORT=8090

# Database Configuration (optional)
MYSQL_ROOT_PASSWORD=password
MYSQL_DATABASE=lane_assist
EOF

echo "✅ Environment configuration created"

# Build Docker images
echo "🏗️  Building Docker images..."
read -p "Do you want to build Docker images now? This may take several minutes. (y/n): " build_images

if [[ $build_images == "y" || $build_images == "Y" ]]; then
    echo "Building inference service image..."
    docker build -t lane-assist-inference ./inference
    
    echo "Building Spring Boot application image..."
    docker build -f Dockerfile.spring -t lane-assist-app .
    
    echo "✅ Docker images built successfully"
else
    echo "⏩ Skipping Docker image build. You can build later with:"
    echo "   docker-compose build"
fi

echo ""
echo "🎉 MaskRCNN Integration Setup Complete!"
echo "======================================"
echo ""
echo "Next Steps:"
echo "1. 📸 Add training images to dataset/train/images/ and dataset/val/images/"
echo "2. 🏷️  Create annotations using CVAT, Labelme, or similar tools"
echo "3. 🧠 Train your model: cd inference && python train.py --dataset-path ../dataset"
echo "4. 🚀 Start services: docker-compose up"
echo ""
echo "Quick Start (with demo):"
echo "- Start inference service: cd inference && uvicorn serve:app --host 0.0.0.0 --port 8000"
echo "- Start Spring Boot app: ./mvnw spring-boot:run"
echo "- Test health: curl http://localhost:8090/api/polygons/maskrcnn/health"
echo ""
echo "Documentation:"
echo "- See README_MaskRCNN.md for detailed instructions"
echo "- API endpoints: http://localhost:8090/swagger-ui.html (if Swagger is configured)"
echo ""
echo "For support, check the logs and health endpoints!"