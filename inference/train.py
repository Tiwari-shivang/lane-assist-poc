#!/usr/bin/env python3
"""
Detectron2 Mask R-CNN training script for road markings
Usage: python train.py --dataset-path ./dataset --output-dir ./out
"""

import os
import argparse
from detectron2.config import get_cfg
from detectron2.engine import DefaultTrainer, default_argument_parser, default_setup, launch
from detectron2.data.datasets import register_coco_instances
from detectron2 import model_zoo
from detectron2.evaluation import COCOEvaluator
import logging

logger = logging.getLogger("detectron2")

class MarkingTrainer(DefaultTrainer):
    @classmethod
    def build_evaluator(cls, cfg, dataset_name, output_folder=None):
        if output_folder is None:
            output_folder = os.path.join(cfg.OUTPUT_DIR, "inference")
        return COCOEvaluator(dataset_name, output_dir=output_folder)

def setup_cfg(args):
    cfg = get_cfg()
    cfg.merge_from_file(model_zoo.get_config_file("COCO-InstanceSegmentation/mask_rcnn_R_50_FPN_3x.yaml"))
    cfg.merge_from_list(args.opts)
    
    # Model configuration
    cfg.MODEL.WEIGHTS = model_zoo.get_checkpoint_url("COCO-InstanceSegmentation/mask_rcnn_R_50_FPN_3x.yaml")
    cfg.MODEL.ROI_HEADS.NUM_CLASSES = 5  # lane_leg, junction_core, zebra_crossing, stop_line, arrow
    cfg.MODEL.ROI_HEADS.SCORE_THRESH_TEST = 0.5
    
    # Training configuration
    cfg.DATALOADER.NUM_WORKERS = 4
    cfg.SOLVER.IMS_PER_BATCH = 2
    cfg.SOLVER.BASE_LR = 0.00025
    cfg.SOLVER.MAX_ITER = 30000  # Adjust based on dataset size
    cfg.SOLVER.STEPS = (20000, 25000)
    cfg.SOLVER.GAMMA = 0.1
    cfg.SOLVER.WARMUP_ITERS = 1000
    cfg.SOLVER.CHECKPOINT_PERIOD = 5000
    
    # Input configuration for thin road markings
    cfg.INPUT.MIN_SIZE_TRAIN = (800, 1024, 1200)
    cfg.INPUT.MAX_SIZE_TRAIN = 1600
    cfg.INPUT.MIN_SIZE_TEST = 1024
    cfg.INPUT.MAX_SIZE_TEST = 1600
    cfg.INPUT.FORMAT = "BGR"
    
    # Data augmentation
    cfg.INPUT.RANDOM_FLIP = "horizontal"
    cfg.INPUT.BRIGHTNESS = [0.8, 1.2]
    cfg.INPUT.CONTRAST = [0.8, 1.2]
    cfg.INPUT.SATURATION = [0.8, 1.2]
    
    # Output directory
    cfg.OUTPUT_DIR = args.output_dir
    cfg.DATASETS.TRAIN = ("markings_train",)
    cfg.DATASETS.TEST = ("markings_val",)
    cfg.TEST.EVAL_PERIOD = 5000
    
    cfg.freeze()
    return cfg

def register_datasets(dataset_path):
    """Register COCO format datasets"""
    train_path = os.path.join(dataset_path, "train")
    val_path = os.path.join(dataset_path, "val")
    
    register_coco_instances(
        "markings_train", 
        {}, 
        os.path.join(train_path, "annotations.json"), 
        os.path.join(train_path, "images")
    )
    
    register_coco_instances(
        "markings_val", 
        {}, 
        os.path.join(val_path, "annotations.json"), 
        os.path.join(val_path, "images")
    )
    
    logger.info(f"Registered datasets from {dataset_path}")

def main(args):
    # Register datasets
    register_datasets(args.dataset_path)
    
    # Setup configuration
    cfg = setup_cfg(args)
    default_setup(cfg, args)
    
    # Create output directory
    os.makedirs(cfg.OUTPUT_DIR, exist_ok=True)
    
    # Save configuration
    with open(os.path.join(cfg.OUTPUT_DIR, "config.yaml"), "w") as f:
        f.write(cfg.dump())
    
    # Train
    trainer = MarkingTrainer(cfg)
    trainer.resume_or_load(resume=args.resume)
    
    if args.eval_only:
        res = trainer.test(cfg, trainer.model)
        return res
    
    trainer.train()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Train Mask R-CNN for road markings")
    parser.add_argument("--dataset-path", required=True, help="Path to COCO format dataset")
    parser.add_argument("--output-dir", default="./out", help="Output directory")
    parser.add_argument("--resume", action="store_true", help="Resume training")
    parser.add_argument("--eval-only", action="store_true", help="Evaluation only")
    parser.add_argument("--num-gpus", type=int, default=1, help="Number of GPUs")
    parser.add_argument("--opts", nargs=argparse.REMAINDER, default=[], help="Override config options")
    
    args = parser.parse_args()
    
    print(f"Command line arguments: {args}")
    
    launch(
        main,
        args.num_gpus,
        num_machines=1,
        machine_rank=0,
        dist_url="auto",
        args=(args,),
    )