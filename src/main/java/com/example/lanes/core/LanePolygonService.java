package com.example.lanes.core;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import org.bytedeco.javacpp.indexer.IntIndexer;
import org.bytedeco.javacpp.indexer.FloatRawIndexer;
import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.opencv.opencv_core.*;
import org.bytedeco.opencv.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_core.*;
import static org.bytedeco.opencv.global.opencv_imgproc.*;
import static org.bytedeco.opencv.global.opencv_imgcodecs.*;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class LanePolygonService {

  @Value("${lanes.minAreaFrac:0.00005}") private double minAreaFrac;  // Very small
  @Value("${lanes.maxAreaFrac:0.05}") private double maxAreaFrac;    // Not too large

  public OverlayResult process(byte[] imageBytes) {
    // Decode image
    Mat buf = new Mat(1, imageBytes.length, CV_8U);
    buf.data().put(imageBytes);
    Mat img = imdecode(buf, IMREAD_COLOR);
    if (img == null || img.empty()) throw new IllegalArgumentException("Invalid image");

    int H = img.rows(), W = img.cols();
    double minArea = minAreaFrac * H * W;
    double maxArea = maxAreaFrac * H * W;

    List<OverlayResult.Polygon> polysOut = new ArrayList<>();
    Mat vis = img.clone();

    // Convert to grayscale
    Mat gray = new Mat();
    cvtColor(img, gray, COLOR_BGR2GRAY);

    // AGGRESSIVE WHITE DETECTION - Multiple approaches combined
    
    // 1. Direct bright thresholding - multiple levels
    Mat bright1 = new Mat();
    threshold(gray, bright1, 140, 255, THRESH_BINARY);
    
    Mat bright2 = new Mat();  
    threshold(gray, bright2, 160, 255, THRESH_BINARY);
    
    Mat bright3 = new Mat();
    threshold(gray, bright3, 180, 255, THRESH_BINARY);

    // 2. CLAHE enhanced detection
    CLAHE clahe = createCLAHE(5.0, new Size(8, 8)); // Very high contrast
    Mat enhanced = new Mat();
    clahe.apply(gray, enhanced);
    
    Mat brightEnhanced = new Mat();
    threshold(enhanced, brightEnhanced, 150, 255, THRESH_BINARY);

    // 3. Multiple top-hat filters for different marking sizes
    Mat tophat1 = new Mat();
    morphologyEx(enhanced, tophat1, MORPH_TOPHAT, getStructuringElement(MORPH_RECT, new Size(7, 7)));
    
    Mat tophat2 = new Mat();
    morphologyEx(enhanced, tophat2, MORPH_TOPHAT, getStructuringElement(MORPH_RECT, new Size(15, 15)));
    
    Mat tophat3 = new Mat();
    morphologyEx(enhanced, tophat3, MORPH_TOPHAT, getStructuringElement(MORPH_RECT, new Size(25, 25)));
    
    Mat tophatCombined = new Mat();
    bitwise_or(tophat1, tophat2, tophatCombined);
    bitwise_or(tophatCombined, tophat3, tophatCombined);
    
    Mat tophatThresh = new Mat();
    threshold(tophatCombined, tophatThresh, 0, 255, THRESH_BINARY | THRESH_OTSU);

    // 4. HSV white detection with multiple ranges
    Mat combinedMask = new Mat();
    if (img.channels() == 3) {
      Mat hsv = new Mat();
      cvtColor(img, hsv, COLOR_BGR2HSV);
      
      // Very permissive white detection
      Mat hsvWhite1 = new Mat();
      Mat whiteLow1 = new Mat(new Scalar(0, 0, 150, 0));
      Mat whiteHigh1 = new Mat(new Scalar(180, 50, 255, 0));
      inRange(hsv, whiteLow1, whiteHigh1, hsvWhite1);
      
      // Strict white detection  
      Mat hsvWhite2 = new Mat();
      Mat whiteLow2 = new Mat(new Scalar(0, 0, 200, 0));
      Mat whiteHigh2 = new Mat(new Scalar(180, 30, 255, 0));
      inRange(hsv, whiteLow2, whiteHigh2, hsvWhite2);
      
      // Combine all masks
      bitwise_or(bright1, bright2, combinedMask);
      bitwise_or(combinedMask, bright3, combinedMask);
      bitwise_or(combinedMask, brightEnhanced, combinedMask);
      bitwise_or(combinedMask, tophatThresh, combinedMask);
      bitwise_or(combinedMask, hsvWhite1, combinedMask);
      bitwise_or(combinedMask, hsvWhite2, combinedMask);
    } else {
      // Grayscale image
      bitwise_or(bright1, bright2, combinedMask);
      bitwise_or(combinedMask, bright3, combinedMask);
      bitwise_or(combinedMask, brightEnhanced, combinedMask);
      bitwise_or(combinedMask, tophatThresh, combinedMask);
    }

    // Light cleanup - preserve small features
    Mat cleanKernel = getStructuringElement(MORPH_ELLIPSE, new Size(2, 2));
    morphologyEx(combinedMask, combinedMask, MORPH_OPEN, cleanKernel);

    // Find all contours - be very permissive
    MatVector contours = new MatVector();
    Mat hierarchy = new Mat();
    findContours(combinedMask, contours, hierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);

    // Process every contour with minimal filtering
    for (long i = 0; i < contours.size(); i++) {
      Mat c = contours.get(i);
      double area = contourArea(c);
      
      // Very permissive area filtering
      if (area < minArea || area > maxArea) continue;
      
      Rect boundingRect = boundingRect(c);
      
      // Very permissive size filtering - accept almost anything reasonable
      if (boundingRect.width() < 5 || boundingRect.height() < 5) continue;
      if (boundingRect.width() > W/2 || boundingRect.height() > H/2) continue;
      
      // Create tight bounding rectangle
      List<int[]> pts = new ArrayList<>();
      pts.add(new int[]{boundingRect.x(), boundingRect.y()});
      pts.add(new int[]{boundingRect.x() + boundingRect.width(), boundingRect.y()});
      pts.add(new int[]{boundingRect.x() + boundingRect.width(), boundingRect.y() + boundingRect.height()});
      pts.add(new int[]{boundingRect.x(), boundingRect.y() + boundingRect.height()});
      
      // Draw bright green rectangle
      rectangle(vis, new Point(boundingRect.x(), boundingRect.y()), 
               new Point(boundingRect.x() + boundingRect.width(), 
                        boundingRect.y() + boundingRect.height()), 
               new Scalar(0, 255, 0, 0), 2);
      
      polysOut.add(new OverlayResult.Polygon(pts, area));
    }

    // Additional edge-based detection for missed markings
    Mat edges = new Mat();
    Canny(enhanced, edges, 30, 100); // Very sensitive edge detection
    
    // Fill edges to create shapes
    Mat edgeKernel = getStructuringElement(MORPH_RECT, new Size(3, 3));
    dilate(edges, edges, edgeKernel);
    morphologyEx(edges, edges, MORPH_CLOSE, edgeKernel);
    
    MatVector edgeContours = new MatVector();
    Mat edgeHierarchy = new Mat();
    findContours(edges, edgeContours, edgeHierarchy, RETR_EXTERNAL, CHAIN_APPROX_SIMPLE);
    
    for (long i = 0; i < edgeContours.size(); i++) {
      Mat c = edgeContours.get(i);
      double area = contourArea(c);
      
      if (area < minArea * 0.5 || area > maxArea * 0.3) continue;
      
      Rect boundingRect = boundingRect(c);
      
      if (boundingRect.width() < 8 || boundingRect.height() < 8) continue;
      if (boundingRect.width() > W/3 || boundingRect.height() > H/3) continue;
      
      // Check if this is a duplicate (too close to existing)
      boolean isDuplicate = false;
      int centerX = boundingRect.x() + boundingRect.width()/2;
      int centerY = boundingRect.y() + boundingRect.height()/2;
      
      for (OverlayResult.Polygon existing : polysOut) {
        List<int[]> existingPts = existing.points();
        if (existingPts.size() >= 4) {
          int existingCenterX = (existingPts.get(0)[0] + existingPts.get(2)[0]) / 2;
          int existingCenterY = (existingPts.get(0)[1] + existingPts.get(2)[1]) / 2;
          double dist = Math.sqrt(Math.pow(centerX - existingCenterX, 2) + Math.pow(centerY - existingCenterY, 2));
          if (dist < 30) { // 30 pixel threshold
            isDuplicate = true;
            break;
          }
        }
      }
      
      if (!isDuplicate) {
        List<int[]> pts = new ArrayList<>();
        pts.add(new int[]{boundingRect.x(), boundingRect.y()});
        pts.add(new int[]{boundingRect.x() + boundingRect.width(), boundingRect.y()});
        pts.add(new int[]{boundingRect.x() + boundingRect.width(), boundingRect.y() + boundingRect.height()});
        pts.add(new int[]{boundingRect.x(), boundingRect.y() + boundingRect.height()});
        
        // Draw bright green rectangle
        rectangle(vis, new Point(boundingRect.x(), boundingRect.y()), 
                 new Point(boundingRect.x() + boundingRect.width(), 
                          boundingRect.y() + boundingRect.height()), 
                 new Scalar(0, 255, 0, 0), 2);
        
        polysOut.add(new OverlayResult.Polygon(pts, area));
      }
    }

    // Encode to PNG
    BytePointer pngData = new BytePointer();
    imencode(".png", vis, pngData);
    byte[] pngBytes = new byte[(int) pngData.capacity()];
    pngData.get(pngBytes);

    return new OverlayResult(polysOut, pngBytes);
  }
}