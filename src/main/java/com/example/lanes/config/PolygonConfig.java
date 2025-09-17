package com.example.lanes.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "polygons")
public class PolygonConfig {
    private double ppm = 5.0;
    private double defaultPpm = 5.0;  // Default PPM for PNG-only processing
    private double minAreaFrac = 0.0015;
    private double epsilonFrac = 0.012;
    private double roadOverlapMin = 0.65;
    private Kernel kernel = new Kernel();
    private Tophat tophat = new Tophat();
    private Segmentation segmentation = new Segmentation();
    
    // Getter method for default PPM (PNG-only mode)
    public double getDefaultPpm() {
        return defaultPpm;
    }

    @Data
    public static class Kernel {
        private double lengthFrac = 0.08;
        private double thickFrac = 0.02;
    }

    @Data
    public static class Tophat {
        private int kernelPx = 21;
    }
    
    @Data
    public static class Segmentation {
        private boolean enabled = false;  // Turn on when MaskRCNN service is deployed
        private String url = "http://inference:8000/infer";
    }
}