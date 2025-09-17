import io
import json
import cv2
import numpy as np
import logging
from fastapi import FastAPI, File, UploadFile, HTTPException
from fastapi.responses import JSONResponse
from detectron2.engine import DefaultPredictor
from detectron2.config import get_cfg
from detectron2 import model_zoo
from shapely.geometry import Polygon, mapping
import torch

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="Road Markings Instance Segmentation", version="1.0.0")

cfg = get_cfg()
cfg.merge_from_file(model_zoo.get_config_file(
    "COCO-InstanceSegmentation/mask_rcnn_R_50_FPN_3x.yaml"))

model_path = "out/model_final.pth"
try:
    cfg.MODEL.WEIGHTS = model_path
    cfg.MODEL.ROI_HEADS.NUM_CLASSES = 5  # lane_leg, junction_core, zebra_crossing, stop_line, arrow
    cfg.MODEL.ROI_HEADS.SCORE_THRESH_TEST = 0.5
    cfg.INPUT.MIN_SIZE_TEST = 1024
    cfg.INPUT.FORMAT = "BGR"
    
    predictor = DefaultPredictor(cfg)
    logger.info("Detectron2 model loaded successfully")
except Exception as e:
    logger.warning(f"Could not load trained model: {e}. Using pre-trained COCO model for demo.")
    cfg.MODEL.WEIGHTS = model_zoo.get_checkpoint_url("COCO-InstanceSegmentation/mask_rcnn_R_50_FPN_3x.yaml")
    cfg.MODEL.ROI_HEADS.NUM_CLASSES = 80  # COCO classes
    predictor = DefaultPredictor(cfg)

class_names = {
    0: "lane_leg",
    1: "junction_core", 
    2: "zebra_crossing",
    3: "stop_line",
    4: "arrow"
}

@app.get("/health")
async def health_check():
    return {"status": "healthy", "model_loaded": True}

@app.post("/infer")
async def infer(file: UploadFile = File(...), ppm: float = 5.0, min_area: int = 200):
    """
    Perform instance segmentation on uploaded image
    
    Args:
        file: Image file (PNG, JPG, etc.)
        ppm: Pixels per meter for spatial calculations
        min_area: Minimum contour area to consider
    
    Returns:
        JSON with detected polygons and metadata
    """
    try:
        img_bytes = await file.read()
        img_array = np.frombuffer(img_bytes, dtype=np.uint8)
        img = cv2.imdecode(img_array, cv2.IMREAD_COLOR)
        
        if img is None:
            raise HTTPException(status_code=400, detail="Invalid image format")
        
        # Handle grayscale images by stacking to 3 channels
        if len(img.shape) == 2:
            img = np.dstack([img]*3)
        elif img.shape[2] == 1:
            img = np.dstack([img[:,:,0]]*3)
        
        logger.info(f"Processing image: {img.shape}, ppm: {ppm}")
        
        # Run inference
        outputs = predictor(img)
        instances = outputs["instances"].to("cpu")
        
        if len(instances) == 0:
            return JSONResponse(content={"polygons": [], "image_shape": img.shape[:2]})
        
        masks = instances.pred_masks.numpy()
        classes = instances.pred_classes.numpy()
        scores = instances.scores.numpy()
        
        results = []
        for mask, cls, score in zip(masks, classes, scores):
            # Convert mask to contours
            mask_uint8 = (mask.astype(np.uint8) * 255)
            contours, _ = cv2.findContours(mask_uint8, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
            
            for contour in contours:
                area = cv2.contourArea(contour)
                if area < min_area:
                    continue
                
                # Approximate polygon
                epsilon = 0.012 * cv2.arcLength(contour, True)
                approx = cv2.approxPolyDP(contour, epsilon, True).reshape(-1, 2)
                
                # Convert to real-world measurements
                area_m2 = area / (ppm * ppm)
                perimeter_m = cv2.arcLength(approx, True) / ppm
                
                # Calculate width estimation (for lane markings)
                width_m = area_m2 / (perimeter_m / 2) if perimeter_m > 0 else 0
                
                polygon_data = {
                    "class_id": int(cls),
                    "class_name": class_names.get(int(cls), f"class_{cls}"),
                    "score": float(score),
                    "points": approx.tolist(),
                    "area_px": float(area),
                    "area_m2": float(area_m2),
                    "perimeter_m": float(perimeter_m),
                    "width_m": float(width_m),
                    "ppm": ppm
                }
                
                results.append(polygon_data)
        
        response_data = {
            "polygons": results,
            "image_shape": img.shape[:2],
            "total_detections": len(results),
            "model_info": {
                "score_threshold": cfg.MODEL.ROI_HEADS.SCORE_THRESH_TEST,
                "num_classes": cfg.MODEL.ROI_HEADS.NUM_CLASSES
            }
        }
        
        logger.info(f"Detected {len(results)} polygons")
        return JSONResponse(content=response_data)
        
    except Exception as e:
        logger.error(f"Inference error: {str(e)}")
        raise HTTPException(status_code=500, detail=f"Inference failed: {str(e)}")

@app.post("/infer_batch")
async def infer_batch(files: list[UploadFile] = File(...), ppm: float = 5.0):
    """
    Batch inference for multiple images
    """
    results = []
    for i, file in enumerate(files):
        try:
            result = await infer(file, ppm)
            results.append({
                "file_index": i,
                "filename": file.filename,
                "result": result
            })
        except Exception as e:
            results.append({
                "file_index": i,
                "filename": file.filename,
                "error": str(e)
            })
    
    return JSONResponse(content={"batch_results": results})

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=8000)