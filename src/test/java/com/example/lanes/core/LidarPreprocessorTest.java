package com.example.lanes.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class LidarPreprocessorTest {

    @TempDir
    Path tempDir;

    @Test
    public void testLazToPngWithMockFile() {
        File mockLaz = tempDir.resolve("test.laz").toFile();
        try {
            Files.write(mockLaz.toPath(), "mock laz content".getBytes());
        } catch (IOException e) {
            fail("Failed to create mock file");
        }

        // Since PDAL/GDAL may not be available, we expect this to fail
        assertThrows(IOException.class, () -> {
            LidarPreprocessor.lazToPng(mockLaz, 0.20);
        });
    }

    @Test
    public void testLazToPngWithInvalidFile() {
        File nonExistentFile = new File("non_existent.laz");
        
        assertThrows(IOException.class, () -> {
            LidarPreprocessor.lazToPng(nonExistentFile, 0.20);
        });
    }

    @Test
    public void testLazToPngWithDifferentResolutions() {
        File mockLaz = tempDir.resolve("test.laz").toFile();
        try {
            Files.write(mockLaz.toPath(), "mock laz content".getBytes());
        } catch (IOException e) {
            fail("Failed to create mock file");
        }

        double[] resolutions = {0.10, 0.20, 0.50};
        
        // Since PDAL/GDAL may not be available, we expect these to fail
        for (double resolution : resolutions) {
            assertThrows(IOException.class, () -> {
                LidarPreprocessor.lazToPng(mockLaz, resolution);
            });
        }
    }
}