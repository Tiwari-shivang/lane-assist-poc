package com.example.lanes.service;

import com.example.lanes.dto.MaskRCNNResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

@Service
public class MaskRCNNInferenceService {
    
    private static final Logger logger = LoggerFactory.getLogger(MaskRCNNInferenceService.class);
    
    private final WebClient webClient;
    private final String inferenceUrl;
    
    public MaskRCNNInferenceService(
            @Value("${inference.service.url:http://localhost:8000}") String baseUrl) {
        this.inferenceUrl = baseUrl;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(16 * 1024 * 1024)) // 16MB buffer
                .build();
        
        logger.info("MaskRCNN Inference Service initialized with URL: {}", baseUrl);
    }
    
    public MaskRCNNResponse inferPolygons(File imageFile, double ppm) {
        return inferPolygons(imageFile, ppm, 200);
    }
    
    public MaskRCNNResponse inferPolygons(File imageFile, double ppm, int minArea) {
        try {
            logger.info("Sending inference request for file: {}, ppm: {}, minArea: {}", 
                       imageFile.getName(), ppm, minArea);
            
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", new FileSystemResource(imageFile));
            
            MaskRCNNResponse response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path("/infer")
                            .queryParam("ppm", ppm)
                            .queryParam("min_area", minArea)
                            .build())
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(parts))
                    .retrieve()
                    .bodyToMono(MaskRCNNResponse.class)
                    .timeout(Duration.ofMinutes(2))
                    .block();
            
            logger.info("Received {} polygons from inference service", 
                       response != null ? response.totalDetections() : 0);
            
            return response;
            
        } catch (WebClientResponseException e) {
            logger.error("HTTP error calling inference service: {} - {}", 
                        e.getStatusCode(), e.getResponseBodyAsString());
            throw new InferenceServiceException("HTTP error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            logger.error("Error calling inference service", e);
            throw new InferenceServiceException("Failed to call inference service", e);
        }
    }
    
    /**
     * Get segmentation mask from MaskRCNN service for PNG fusion
     * Returns probability mask as PNG bytes for fusion with classical markings
     */
    public Optional<byte[]> getSegmentationMask(byte[] pngBytes) {
        try {
            // Create temporary file for the service
            File tempFile = File.createTempFile("seg_", ".png");
            Files.write(tempFile.toPath(), pngBytes);
            
            MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
            parts.add("file", new FileSystemResource(tempFile));
            
            // Call segmentation endpoint (assuming it returns mask_png_b64)
            Map<String, Object> response = webClient.post()
                    .uri("/segment")  // Different endpoint for pure segmentation
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(BodyInserters.fromMultipartData(parts))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .timeout(Duration.ofSeconds(30))
                    .block();
            
            tempFile.delete();
            
            if (response != null && response.containsKey("mask_png_b64")) {
                String maskB64 = (String) response.get("mask_png_b64");
                byte[] maskBytes = Base64.getDecoder().decode(maskB64);
                return Optional.of(maskBytes);
            }
            
            return Optional.empty();
            
        } catch (Exception e) {
            logger.warn("Failed to get segmentation mask: {}", e.getMessage());
            return Optional.empty();
        }
    }
    
    public boolean isServiceHealthy() {
        try {
            String response = webClient.get()
                    .uri("/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(10))
                    .block();
            
            return response != null && response.contains("healthy");
        } catch (Exception e) {
            logger.warn("Health check failed for inference service: {}", e.getMessage());
            return false;
        }
    }
    
    public static class InferenceServiceException extends RuntimeException {
        public InferenceServiceException(String message) {
            super(message);
        }
        
        public InferenceServiceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}