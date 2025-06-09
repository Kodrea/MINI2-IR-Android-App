# PRD - TensorFlow Lite Super Resolution

## Goal
Enhance thermal camera image quality by applying real-time super resolution using TensorFlow Lite models to upscale 256x192 thermal images.

## TensorFlow Lite Overview & 2025 Best Practices

### Current TFLite Implementation (2025)
**Latest Approach**: TensorFlow Lite 2.15+ with GPU delegate support
- **Runtime**: `org.tensorflow:tensorflow-lite:2.15.0`
- **GPU Acceleration**: `org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4`
- **Support Library**: `org.tensorflow:tensorflow-lite-support:0.4.4`
- **Best Practice**: Use TFLite Task Library for common CV tasks
- **Performance**: GPU delegate provides 2-10x speedup on mobile GPUs

### Recommended Super Resolution Models for 256x192

1. **ESRGAN-Mobile** (Recommended)
   - **Input**: 256x192 → **Output**: 512x384 (2x) or 1024x768 (4x)
   - **Model Size**: ~15-25MB
   - **Performance**: ~50-100ms on Pixel 7 GPU
   - **Format**: Supports both RGB and grayscale
   - **Source**: Available in TensorFlow Hub

2. **Real-ESRGAN Mobile**
   - **Input**: Any resolution → **Output**: 2x or 4x upscale
   - **Model Size**: ~8-12MB (optimized for mobile)
   - **Performance**: ~30-60ms on modern Android devices
   - **Advantage**: Better artifact reduction for thermal-like images

3. **SRCNN-Lite**
   - **Input**: 256x192 → **Output**: 512x384
   - **Model Size**: ~2-5MB (very lightweight)
   - **Performance**: ~10-20ms (fastest option)
   - **Trade-off**: Lower quality but real-time capable

### Feasibility for Live Video on Pixel 7
**Answer: YES, but with considerations**

**Pixel 7 Capabilities**:
- **CPU**: Tensor G2 (optimized for ML)
- **GPU**: Mali-G710 MP7 (excellent TFLite GPU delegate support)
- **RAM**: 8GB (sufficient for model + video buffers)
- **Expected Performance**: 15-30 FPS with 2x super resolution

**Real-time Strategy**:
- Process every 2nd or 3rd frame to maintain smoothness
- Use GPU delegate for acceleration
- Implement frame skipping when processing takes too long
- Cache processed frames for smooth playback

## Development Strategy: Single Images First

### Why Start with Single Images?
1. **Debugging**: Easier to validate model accuracy
2. **Performance Baseline**: Measure processing time without video complexity
3. **Model Selection**: Compare different models on same test images
4. **Preprocessing Pipeline**: Perfect image preparation before video integration

### Test Image Pipeline
```kotlin
// Phase 1: Single image processing
Bitmap thermalImage = // 256x192 thermal image
Bitmap upscaledImage = superResolutionProcessor.process(thermalImage)
// Expected output: 512x384 or 1024x768
```

## Minimal Kotlin TFLite Pipeline

### Dependencies (build.gradle.kts)
```kotlin
dependencies {
    implementation "org.tensorflow:tensorflow-lite:2.15.0"
    implementation "org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4"
    implementation "org.tensorflow:tensorflow-lite-support:0.4.4"
}
```

### Minimal Implementation
```kotlin
class SuperResolutionProcessor {
    private lateinit var interpreter: Interpreter
    
    fun initialize(context: Context) {
        // Load model from assets
        val model = loadModelFile(context, "super_resolution_model.tflite")
        
        // Configure with GPU delegate
        val options = Interpreter.Options().apply {
            addDelegate(GpuDelegate())
            setNumThreads(4)
        }
        
        interpreter = Interpreter(model, options)
    }
    
    fun process(inputBitmap: Bitmap): Bitmap {
        // 1. Preprocess: Convert bitmap to float array
        val inputArray = preprocessBitmap(inputBitmap)
        
        // 2. Prepare output buffer
        val outputArray = Array(1) { Array(outputHeight) { Array(outputWidth) { FloatArray(3) } } }
        
        // 3. Run inference
        interpreter.run(inputArray, outputArray)
        
        // 4. Postprocess: Convert back to bitmap
        return postprocessTobitmap(outputArray)
    }
    
    private fun preprocessBitmap(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        // Resize to model input size if needed
        val resized = Bitmap.createScaledBitmap(bitmap, 256, 192, true)
        
        // Convert to float array, normalize to [0,1] or [-1,1]
        val inputArray = Array(1) { Array(192) { Array(256) { FloatArray(3) } } }
        
        for (y in 0 until 192) {
            for (x in 0 until 256) {
                val pixel = resized.getPixel(x, y)
                // Normalize pixel values (model-dependent)
                inputArray[0][y][x][0] = (pixel and 0xFF) / 255.0f // R
                inputArray[0][y][x][1] = ((pixel shr 8) and 0xFF) / 255.0f // G  
                inputArray[0][y][x][2] = ((pixel shr 16) and 0xFF) / 255.0f // B
            }
        }
        
        return inputArray
    }
}
```

## Image Preprocessing for 256x192 Thermal Images

### Input Format Considerations
**Thermal Image Characteristics**:
- **Resolution**: 256x192 pixels
- **Color Format**: Typically grayscale (single channel) or false-color mapped
- **Value Range**: Temperature values mapped to 0-255 or floating point
- **Pixel Format**: Usually 8-bit or 16-bit per pixel

### Preprocessing Steps
1. **Format Conversion**: Thermal data → RGB/Grayscale bitmap
2. **Normalization**: Pixel values to [0,1] or [-1,1] range (model-dependent)
3. **Channel Handling**: Convert grayscale to RGB if model requires 3 channels
4. **Size Validation**: Ensure exact 256x192 input (some models are size-sensitive)

### Grayscale vs RGB Model Input

**Grayscale Models** (Recommended for thermal):
- **Advantages**: Smaller model size, faster processing, thermal-appropriate
- **Input**: Single channel (H×W×1)
- **Models**: SRCNN variants, specialized thermal super resolution
- **Preprocessing**: Direct grayscale → model input

**RGB Models** (More common):
- **Advantages**: More pre-trained models available, better general performance
- **Input**: Three channels (H×W×3)
- **Conversion**: Duplicate grayscale to RGB channels or apply false color mapping
- **Preprocessing**: Grayscale → RGB → model input

```kotlin
// Convert thermal grayscale to RGB for model input
fun thermalToRGB(thermalBitmap: Bitmap): Bitmap {
    val rgb = Bitmap.createBitmap(256, 192, Bitmap.Config.ARGB_8888)
    
    for (y in 0 until 192) {
        for (x in 0 until 256) {
            val gray = thermalBitmap.getPixel(x, y) and 0xFF
            val rgbPixel = Color.rgb(gray, gray, gray) // Duplicate to RGB
            rgb.setPixel(x, y, rgbPixel)
        }
    }
    
    return rgb
}
```

## Key Classes / Files
### Android/Kotlin Files
- `CameraActivity.kt` - Add super resolution toggle and processing controls
- `SuperResolutionProcessor.kt` - NEW - TFLite model management and processing
- `ThermalImageProcessor.kt` - NEW - Thermal-specific preprocessing and postprocessing
- `IrcmdManager.kt` - Integration with camera parameter adjustments

### Native/C++ Files
- `uvc_manager.h` - Add frame callback for super resolution processing
- `uvc_manager.cpp` - Provide processed frame output option
- `image_utils.h` - NEW - Image format conversion utilities
- `image_utils.cpp` - NEW - Efficient bitmap operations

### UI/Resources
- `activity_camera.xml` - Add super resolution toggle and quality settings
- `dialog_sr_settings.xml` - NEW - Super resolution model and quality options
- `strings.xml` - Super resolution related strings

### Model Assets
- `assets/models/esrgan_mobile_2x.tflite` - 2x super resolution model
- `assets/models/esrgan_mobile_4x.tflite` - 4x super resolution model (optional)

### Other Files
- `build.gradle.kts` - Add TensorFlow Lite dependencies
- `CMakeLists.txt` - Native image processing libraries if needed

## Steps
### Phase 1: Model Research & Selection
- [ ] Download and test ESRGAN-Mobile model from TensorFlow Hub
- [ ] Test Real-ESRGAN Mobile model for thermal image compatibility
- [ ] Benchmark SRCNN-Lite for real-time performance requirements
- [ ] Create test dataset from existing 256x192 thermal captures
- [ ] Validate model outputs with ground truth higher resolution images

### Phase 2: Single Image Processing Pipeline
- [ ] Implement `SuperResolutionProcessor` class with basic TFLite integration
- [ ] Create thermal image preprocessing pipeline (grayscale → RGB conversion)
- [ ] Add model loading and GPU delegate configuration
- [ ] Build test UI for single image processing and result comparison
- [ ] Optimize preprocessing and postprocessing for performance

### Phase 3: Live Video Integration
- [ ] Extend UVC manager to provide frame callbacks for processing
- [ ] Implement frame queue system for async super resolution processing
- [ ] Add real-time performance monitoring and frame skipping logic
- [ ] Create dual-view display (original vs super resolution)
- [ ] Add user controls for enabling/disabling super resolution

## Testing
- [ ] Unit tests for image preprocessing functions
- [ ] Model accuracy tests on thermal image dataset
- [ ] Performance benchmarks on Pixel 7 (CPU vs GPU delegate)
- [ ] Memory usage validation during continuous processing
- [ ] Real-time video processing stress tests

## Acceptance Criteria
- [ ] 2x super resolution processing completes in <50ms on Pixel 7
- [ ] Visual quality improvement is noticeable on thermal images
- [ ] Live video maintains >15 FPS with super resolution enabled
- [ ] Memory usage remains stable during extended processing
- [ ] User can toggle super resolution on/off without app restart
- [ ] Processed images maintain thermal data characteristics

## Dependencies
- TensorFlow Lite 2.15+ runtime and GPU delegate
- Pre-trained super resolution model (ESRGAN-Mobile or similar)
- Sufficient device memory for model and image buffers
- GPU acceleration support on target devices

## Risks & Mitigation
- **Risk**: Model processing too slow for real-time video
  - **Mitigation**: Start with single images, use GPU delegate, implement frame skipping
- **Risk**: Super resolution may introduce artifacts in thermal images
  - **Mitigation**: Test multiple models, validate against ground truth, provide quality settings
- **Risk**: Large model files may impact app size
  - **Mitigation**: Use model quantization, optional download of models, multiple model sizes
- **Risk**: Thermal color characteristics may be lost in RGB conversion
  - **Mitigation**: Preserve original thermal data, provide comparison view, test grayscale models

## Learning Resources for TFLite (2025)
1. **Official Documentation**: [TensorFlow Lite Guide](https://www.tensorflow.org/lite/guide)
2. **Android Integration**: [TFLite Android Quickstart](https://www.tensorflow.org/lite/guide/android)
3. **GPU Delegate**: [GPU acceleration guide](https://www.tensorflow.org/lite/performance/gpu)
4. **Model Hub**: [TensorFlow Hub](https://tfhub.dev/) for pre-trained models
5. **Super Resolution**: [Image super resolution models](https://tfhub.dev/s?q=super%20resolution)

## Claude Status
Created comprehensive PRD with TFLite implementation guidance, model recommendations, and thermal image processing considerations.

---
**Created**: 2025-01-08  
**Last Updated**: 2025-01-08  
**Assignee**: Claude  
**Status**: Planned