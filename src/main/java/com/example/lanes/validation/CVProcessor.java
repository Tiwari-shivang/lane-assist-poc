package com.example.lanes.validation;

import com.example.lanes.core.LanePolygonService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Computer Vision Processor that bridges test requirements with actual LanePolygonService
 * Validates CV parameters according to implementation requirements
 */
@Component
public class CVProcessor {

    @Autowired(required = false)
    private LanePolygonService lanePolygonService;

    public CVProcessor() {
        // Allow instantiation without Spring context for testing
    }

    public ValidationResult validateRoadOverlap(Map<String, Object> params) {
        ValidationResult result = new ValidationResult();

        if (!params.containsKey("road_overlap_ratio") || params.get("road_overlap_ratio") == null) {
            throw new IllegalArgumentException("Missing road_overlap_ratio parameter");
        }

        double overlapRatio = getDoubleValue(params.get("road_overlap_ratio"));

        if (overlapRatio < 0) {
            result.setValid(false);
            result.setFailureReason("Negative overlap ratio");
            return result;
        }

        if (overlapRatio >= 0.6) {
            result.setValid(true);
        } else {
            result.setValid(false);
            result.setFailureReason("Overlap ratio below minimum");
        }

        return result;
    }

    public ValidationResult validateAreaFraction(Map<String, Object> params) {
        ValidationResult result = new ValidationResult();

        if (!params.containsKey("area_fraction") || params.get("area_fraction") == null) {
            throw new IllegalArgumentException("Missing area_fraction parameter");
        }

        double areaFraction = getDoubleValue(params.get("area_fraction"));

        if (areaFraction >= 0.001 && areaFraction <= 0.003) {
            result.setValid(true);
        } else {
            result.setValid(false);
            result.setFailureReason("Area fraction outside valid range");
        }

        return result;
    }

    public ValidationResult validateKernelSize(Map<String, Object> params) {
        ValidationResult result = new ValidationResult();

        int kernelSize = (Integer) params.get("kernel_size_px");
        String operation = (String) params.get("operation");

        if ("tophat".equals(operation)) {
            if (kernelSize >= 17 && kernelSize <= 31 && kernelSize % 2 == 1) {
                result.setValid(true);
            } else {
                result.setValid(false);
                result.setFailureReason("Invalid kernel size for tophat");
            }
        } else {
            result.setValid(true);
        }

        return result;
    }

    public ValidationResult validateMorphPipeline(Map<String, Object> params) {
        ValidationResult result = new ValidationResult();

        String[] operations = (String[]) params.get("operation_sequence");

        if (operations == null || operations.length == 0) {
            result.setValid(false);
            result.setFailureReason("Missing tophat operation");
            return result;
        }

        boolean hasTophat = false;
        for (String op : operations) {
            if ("tophat".equals(op)) {
                hasTophat = true;
                break;
            }
        }

        if (!hasTophat) {
            result.setValid(false);
            result.setFailureReason("Missing tophat operation");
            return result;
        }

        result.setValid(true);
        return result;
    }

    public ValidationResult validatePreprocessing(Map<String, Object> params) {
        ValidationResult result = new ValidationResult();

        String inputFormat = (String) params.get("input_format");
        boolean grayscale = params.get("grayscale_conversion") != null ?
                           (Boolean) params.get("grayscale_conversion") : false;

        if (!"BGR".equals(inputFormat)) {
            result.setValid(false);
            result.setFailureReason("Invalid input format");
            return result;
        }

        if (!grayscale) {
            result.setValid(false);
            result.setFailureReason("Grayscale conversion required");
            return result;
        }

        result.setValid(true);
        return result;
    }

    public ValidationResult validateThreshold(Map<String, Object> params) {
        ValidationResult result = new ValidationResult();

        String thresholdType = (String) params.get("threshold_type");

        if (thresholdType == null) {
            result.setValid(false);
            result.setFailureReason("Missing threshold type");
            return result;
        }

        if (!"binary".equals(thresholdType)) {
            result.setValid(false);
            result.setFailureReason("Invalid threshold type");
            return result;
        }

        int thresholdValue = (Integer) params.get("threshold_value");

        if (thresholdValue <= 0 || thresholdValue >= 255) {
            result.setValid(false);
            result.setFailureReason("Invalid threshold value");
            return result;
        }

        result.setValid(true);
        return result;
    }

    public ValidationResult validateAdaptiveThreshold(Map<String, Object> params) {
        ValidationResult result = new ValidationResult();

        String thresholdType = (String) params.get("threshold_type");

        if (!"adaptive".equals(thresholdType)) {
            result.setValid(false);
            result.setFailureReason("Invalid threshold type");
            return result;
        }

        int blockSize = (Integer) params.get("block_size");

        if (blockSize % 2 == 0) {
            result.setValid(false);
            result.setFailureReason("Block size must be odd");
            return result;
        }

        result.setValid(true);
        return result;
    }

    public ValidationResult validateContourParams(Map<String, Object> params) {
        ValidationResult result = new ValidationResult();

        String mode = (String) params.get("mode");

        if (!"RETR_EXTERNAL".equals(mode)) {
            result.setValid(false);
            result.setFailureReason("Invalid contour mode");
            return result;
        }

        int minArea = (Integer) params.get("min_area");
        int maxArea = (Integer) params.get("max_area");

        if (minArea >= maxArea) {
            result.setValid(false);
            result.setFailureReason("Invalid area range");
            return result;
        }

        result.setValid(true);
        return result;
    }

    public ValidationResult validatePolygonApproximation(Map<String, Object> params) {
        ValidationResult result = new ValidationResult();

        double epsilonFactor = getDoubleValue(params.get("epsilon_factor"));
        boolean closed = (Boolean) params.get("closed");

        if (epsilonFactor <= 0 || epsilonFactor >= 0.1) {
            result.setValid(false);
            result.setFailureReason("Invalid epsilon factor");
            return result;
        }

        if (!closed) {
            result.setValid(false);
            result.setFailureReason("Polygon must be closed");
            return result;
        }

        result.setValid(true);
        return result;
    }

    public ValidationResult validateLaneWidthAccuracy(Map<String, Object> params) {
        ValidationResult result = new ValidationResult();

        double detectedWidth = getDoubleValue(params.get("detected_width_m"));
        double expectedWidth = getDoubleValue(params.get("expected_width_m"));
        double tolerance = getDoubleValue(params.get("tolerance_m"));

        if (Math.abs(detectedWidth - expectedWidth) <= tolerance) {
            result.setValid(true);
        } else {
            result.setValid(false);
            result.setFailureReason("Lane width outside tolerance");
        }

        return result;
    }

    public ValidationResult validateCurvatureDetection(Map<String, Object> params) {
        ValidationResult result = new ValidationResult();

        double curvatureRadius = getDoubleValue(params.get("curvature_radius_m"));
        double minRadius = getDoubleValue(params.get("min_radius_m"));

        if (curvatureRadius >= minRadius) {
            result.setValid(true);
        } else {
            result.setValid(false);
            result.setFailureReason("Curvature radius below minimum");
        }

        return result;
    }

    public ValidationResult validateProcessingTime(Map<String, Object> params) {
        ValidationResult result = new ValidationResult();

        double processingTime = getDoubleValue(params.get("frame_processing_ms"));
        double maxAllowed = getDoubleValue(params.get("max_allowed_ms"));

        if (processingTime <= maxAllowed) {
            result.setValid(true);
        } else {
            result.setValid(false);
            result.setFailureReason("Processing time exceeds limit");
        }

        return result;
    }

    public ValidationResult validateAccuracyMetrics(Map<String, Object> params) {
        ValidationResult result = new ValidationResult();

        double precision = getDoubleValue(params.get("precision"));
        double recall = getDoubleValue(params.get("recall"));
        double f1Score = getDoubleValue(params.get("f1_score"));

        if (precision >= 0.95 && recall >= 0.95 && f1Score >= 0.95) {
            result.setValid(true);
        } else {
            result.setValid(false);
            result.setFailureReason("Accuracy metrics below threshold");
        }

        return result;
    }

    public ValidationResult validateWornMarkingDetection(Map<String, Object> params) {
        ValidationResult result = new ValidationResult();

        int visibilityPercentage = getIntValue(params.get("visibility_percentage"));
        double confidence = getDoubleValue(params.get("detection_confidence"));

        if (visibilityPercentage < 50) {
            result.setValid(false);
            result.setFailureReason("Visibility too low");
            return result;
        }

        if (confidence < 0.7) {
            result.setValid(false);
            result.setFailureReason("Confidence too low");
            return result;
        }

        result.setValid(true);
        return result;
    }

    public ValidationResult validateCVParameters(Map<String, Object> params) {
        ValidationResult result = new ValidationResult(true);

        // Validate road overlap if present
        if (params.containsKey("road_overlap_ratio")) {
            ValidationResult overlapResult = validateRoadOverlap(params);
            if (!overlapResult.isValid()) {
                return overlapResult;
            }
        }

        // Validate area fraction if present
        if (params.containsKey("area_fraction")) {
            ValidationResult areaResult = validateAreaFraction(params);
            if (!areaResult.isValid()) {
                return areaResult;
            }
        }

        return result;
    }

    // Helper methods
    private double getDoubleValue(Object value) {
        if (value instanceof Double) {
            return (Double) value;
        } else if (value instanceof Integer) {
            return ((Integer) value).doubleValue();
        } else if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return 0.0;
    }

    private int getIntValue(Object value) {
        if (value instanceof Integer) {
            return (Integer) value;
        } else if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return 0;
    }
}