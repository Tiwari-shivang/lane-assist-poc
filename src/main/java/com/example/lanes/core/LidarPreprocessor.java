package com.example.lanes.core;

import java.io.File;
import java.io.IOException;

public class LidarPreprocessor {
    
    public record PreResult(File tif, File png) {}
    
    public static PreResult lazToPng(File laz, double resolution) throws IOException, InterruptedException {
        File tif = File.createTempFile("pdal_", ".tif");
        File png = File.createTempFile("pdal_", ".png");
        
        String[] pdal = new String[] {
            "pdal", "translate", laz.getAbsolutePath(), tif.getAbsolutePath(), "writers.gdal",
            "--writers.gdal.dimension=Intensity",
            "--writers.gdal.output_type=max",
            "--writers.gdal.resolution=" + resolution,
            "--writers.gdal.gdaldriver=GTiff",
            "--writers.gdal.nodata=0"
        };
        run(pdal);
        
        String[] gdal = new String[] {
            "gdal_translate", "-of", "PNG", "-ot", "Byte", "-scale",
            tif.getAbsolutePath(), png.getAbsolutePath()
        };
        run(gdal);
        
        return new PreResult(tif, png);
    }
    
    private static void run(String[] cmd) throws IOException, InterruptedException {
        Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (var in = p.getInputStream()) {
            in.transferTo(System.out);
        }
        if (p.waitFor() != 0) {
            throw new IOException("Command failed: " + String.join(" ", cmd));
        }
    }
}