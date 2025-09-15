package com.example.lanes.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "lanes")
public class LaneConfig {
    private boolean useVision = false;
    private double ppm = 5.0;
    private double minAreaFrac = 0.0015;
    private double epsilonFrac = 0.012;
    private double roadOverlapMin = 0.65;
    private boolean debugFrames = true;
    
    private Kernel kernel = new Kernel();
    private Tophat tophat = new Tophat();
    
    public static class Kernel {
        private double lengthFrac = 0.08;
        private double thickFrac = 0.02;
        
        public double getLengthFrac() { return lengthFrac; }
        public void setLengthFrac(double lengthFrac) { this.lengthFrac = lengthFrac; }
        public double getThickFrac() { return thickFrac; }
        public void setThickFrac(double thickFrac) { this.thickFrac = thickFrac; }
    }
    
    public static class Tophat {
        private int kernelPx = 21;
        
        public int getKernelPx() { return kernelPx; }
        public void setKernelPx(int kernelPx) { this.kernelPx = kernelPx; }
    }
    
    public boolean isUseVision() { return useVision; }
    public void setUseVision(boolean useVision) { this.useVision = useVision; }
    public double getPpm() { return ppm; }
    public void setPpm(double ppm) { this.ppm = ppm; }
    public double getMinAreaFrac() { return minAreaFrac; }
    public void setMinAreaFrac(double minAreaFrac) { this.minAreaFrac = minAreaFrac; }
    public double getEpsilonFrac() { return epsilonFrac; }
    public void setEpsilonFrac(double epsilonFrac) { this.epsilonFrac = epsilonFrac; }
    public double getRoadOverlapMin() { return roadOverlapMin; }
    public void setRoadOverlapMin(double roadOverlapMin) { this.roadOverlapMin = roadOverlapMin; }
    public boolean isDebugFrames() { return debugFrames; }
    public void setDebugFrames(boolean debugFrames) { this.debugFrames = debugFrames; }
    public Kernel getKernel() { return kernel; }
    public void setKernel(Kernel kernel) { this.kernel = kernel; }
    public Tophat getTophat() { return tophat; }
    public void setTophat(Tophat tophat) { this.tophat = tophat; }
}