# FSRCNN Super Resolution Implementation

## Overview
This document describes the implementation of TensorFlow Lite FSRCNN super resolution for thermal camera images in the MINI2 IR Android app. The goal is to enhance 256x192 thermal images to 512x384 using deep learning.

## Implementation Plan

### Phase 1: Raw Frame Capture ✅ COMPLETED
- **Goal**: Capture raw 256x192 YUYV thermal data instead of upscaled display images
- **Status**: Successfully implemented and tested
- **Key Components**:
  - Modified `UVCCamera::frameCallback()` to capture raw YUYV frames on demand
  - Added JNI bridge functions for frame access
  - Implemented thread-safe atomic capture mechanism
  - Added Kotlin wrapper functions for raw frame processing

### Phase 2: TensorFlow Lite Integration ✅ COMPLETED
- **Goal**: Load and run FSRCNN model on captured thermal data
- **Status**: Model loads and runs successfully, but output quality issues persist
- **Key Components**:
  - FSRCNN x2 model integration (`assets/models/fsrcnn_x2.tflite`)
  - TensorFlow Lite interpreter setup with 4 threads for Pixel 7 optimization
  - Input preprocessing: YUYV → Y-channel extraction → normalization
  - Output postprocessing: Model output → normalized bitmap

### Phase 3: Image Saving ✅ COMPLETED
- **Goal**: Save both original and enhanced images to gallery
- **Status**: Successfully implemented with proper permissions
- **Key Components**:
  - Android storage permissions (scoped storage for API 29+)
  - MediaStore integration for gallery saving
  - Timestamped filenames to prevent conflicts
  - Success/error handling with user feedback

## Current Implementation Status

### Working Components ✅
1. **Raw Frame Capture**: Successfully captures 256x192 YUYV thermal data
2. **Model Loading**: FSRCNN model loads without errors
3. **Inference Execution**: Model runs with ~168ms processing time
4. **Image Saving**: Both original and enhanced images save to gallery
5. **UI Integration**: "Capture & Enhance" button with progress feedback

### Persistent Issue ❌
**Enhanced Image Quality**: The FSRCNN output produces pixelated/blurry results that are worse than the original thermal image.

## Troubleshooting Attempts

### 1. Postprocessing Pipeline Issues
**Attempted Fixes**:
- Removed min/max normalization in favor of direct [0,1] clamping
- Added gamma correction (γ=0.8) for better contrast
- Implemented smart normalization based on output value ranges
- Replaced slow `setPixel()` calls with fast `setPixels()` array operations

**Result**: No significant improvement in image quality

### 2. Preprocessing Inconsistencies
**Problem Identified**: Original thermal image used contrast enhancement while FSRCNN input used simple normalization
**Attempted Fix**: Applied identical thermal contrast enhancement to both original display and FSRCNN input
**Result**: No significant improvement in image quality

### 3. Model-Data Mismatch Analysis
**Hypothesis**: FSRCNN was trained on natural RGB images, not thermal single-channel data
**Evidence**: 
- Model processes thermal Y-channel data but expects natural image characteristics
- Thermal images have different frequency content and noise patterns
- Limited dynamic range in thermal data vs. natural images

### 4. Performance Optimization
**Issues Fixed**:
- Replaced 196,608 individual `setPixel()` calls with single `setPixels()` operation
- Added proper array bounds checking
- Optimized memory allocation patterns

**Result**: Faster processing but no quality improvement

## Technical Details

### Model Specifications
- **Input**: [1, 192, 256, 1] - Single channel grayscale
- **Output**: [1, 384, 512, 1] - 2x upscaled single channel
- **Format**: TensorFlow Lite (.tflite)
- **Processing Time**: ~168ms on Pixel 7

### Data Pipeline
```
Raw YUYV (256x192) 
    ↓ Y-channel extraction
Thermal Y values (256x192)
    ↓ Contrast enhancement + normalization
FSRCNN Input [1,192,256,1]
    ↓ TensorFlow Lite inference
FSRCNN Output [1,384,512,1]
    ↓ Postprocessing + bitmap conversion
Enhanced Image (512x384)
```

### File Locations
- **Model**: `app/src/main/assets/models/fsrcnn_x2.tflite`
- **Main Implementation**: `app/src/main/java/com/example/ircmd_handle/CameraActivity.kt`
  - Lines 1288-1631: Super resolution implementation
  - Lines 1320-1368: Main capture and enhance function
  - Lines 1468-1610: FSRCNN processing pipeline
- **Native Code**: `app/src/main/cpp/`
  - `native-lib.cpp`: Lines 268-334 (JNI frame capture functions)
  - `uvc_manager.cpp`: Lines 383-399 (Raw frame capture logic)
  - `uvc_manager.h`: Lines 44-47, 78-84 (Capture member variables)

## Next Steps / Future Work

### Potential Solutions to Investigate
1. **Model Replacement**: 
   - Find FSRCNN model trained specifically on thermal/medical imagery
   - Try different super resolution architectures (EDSR, SRCNN, etc.)
   - Consider classical upscaling methods (bicubic, Lanczos) as baseline

2. **Preprocessing Improvements**:
   - Apply thermal-specific enhancements before FSRCNN
   - Experiment with different normalization strategies
   - Add noise reduction or edge enhancement preprocessing

3. **Model Training**:
   - Collect thermal image dataset for custom FSRCNN training
   - Fine-tune existing model on thermal data
   - Train thermal-specific super resolution model

4. **Alternative Approaches**:
   - Implement classical super resolution algorithms
   - Use multiple smaller enhancement steps
   - Combine multiple enhancement techniques

## Current State for Handoff
- **Raw frame capture**: Fully functional and tested
- **Model integration**: Complete but produces suboptimal results
- **Image saving**: Working with gallery integration
- **Performance**: Optimized for mobile deployment
- **Issue**: Model output quality remains pixelated/blurry compared to original

The foundation is solid - the issue appears to be model-data compatibility rather than implementation problems. The thermal camera pipeline successfully captures and processes raw data, but the FSRCNN model trained on natural images doesn't enhance thermal imagery effectively.