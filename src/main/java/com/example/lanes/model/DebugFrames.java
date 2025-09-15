package com.example.lanes.model;

public record DebugFrames(
    byte[] roadMask,
    byte[] marksMask,
    byte[] bandsMask,
    byte[] overlayImage
) {}