# Phase 1 Detailed Plan - TFLite Super Resolution Model Research & Selection

## 🎯 Phase 1 Goals (Updated for Custom Model Strategy)
1. **Learn** TensorFlow Lite (now LiteRT) fundamentals
2. **Download** and test generic super resolution models (for technical validation only)
3. **Create** flexible test environment that supports custom model integration
4. **Build** model loading framework compatible with custom thermal models
5. **Measure** performance benchmarks and validate technical pipeline
6. **Prepare** for Phase 2 custom thermal model integration

**Important**: Phase 1 focuses on **technical validation**, not model quality. We'll use any working generic model to prove the LiteRT pipeline works, then integrate your custom thermal-trained models in Phase 2.

## 📚 Educational Foundation

### What is TensorFlow Lite / LiteRT?
**TensorFlow Lite** was recently rebranded to **LiteRT** (Lite Runtime) by Google in late 2024, but the technical implementation remains the same. Think of it as:

- **Purpose**: Run AI models on mobile devices efficiently
- **vs TensorFlow**: Desktop TensorFlow is huge (~500MB), LiteRT is tiny (~1-5MB)
- **File Format**: `.tflite` files (compressed, optimized models)
- **Speed**: Uses device GPU, specialized chips (like Pixel's Tensor chip)

### What is Super Resolution?
**Super Resolution** = Making images bigger while adding realistic detail (not just stretching pixels)

**Traditional Upscaling**:
```
Original: 256x192 → Stretched: 512x384
[Blurry, pixelated result]
```

**AI Super Resolution**:
```
Original: 256x192 → AI Model → Enhanced: 512x384
[Sharp, detailed result with inferred details]
```

### How Does the AI Model Work?
1. **Training**: Model learned from thousands of low-res → high-res image pairs
2. **Inference**: Given your 256x192 image, it predicts what the high-res version should look like
3. **Output**: 512x384 (2x) or 1024x768 (4x) enhanced image

### Android Development Concepts We'll Use

#### 1. **Dependencies** (build.gradle.kts)
Think of this like a shopping list for your app:
```kotlin
dependencies {
    implementation "com.google.ai.edge.litert:litert:1.0.0"  // NEW LiteRT name
}
```

#### 2. **Assets Folder**
Where you store the AI model file:
```
app/src/main/assets/
└── models/
    └── super_resolution.tflite
```

#### 3. **Bitmap Class**
Android's way to represent images in memory:
```kotlin
val image: Bitmap = // Your 256x192 thermal image
val width = image.width  // 256
val height = image.height // 192
```

#### 4. **ByteBuffer / FloatArray**
How AI models receive data (not directly as images):
```kotlin
// Model expects: [batch, height, width, channels]
// For 256x192 RGB: [1, 192, 256, 3]
val inputArray = FloatArray(1 * 192 * 256 * 3)
```

## 🗺️ Step-by-Step Implementation Plan

### Step 1: Research Current LiteRT Implementation (2025)
**Goal**: Verify the most up-to-date approach

**Tasks**:
1. **Read Official Documentation**:
   - Visit: https://ai.google.dev/edge/litert
   - Focus on: "Android Quickstart" section
   - Note: Any changes from TensorFlow Lite to LiteRT

2. **Check Dependency Names**:
   - ✅ Verify: Is it `com.google.ai.edge.litert:litert:1.0.0`?
   - ✅ Or still: `org.tensorflow:tensorflow-lite:2.15.0`?
   - ✅ GPU delegate: What's the current package name?

3. **Review Breaking Changes**:
   - Read: LiteRT migration guide
   - Note: Any API changes from TensorFlow Lite

**Learning Outcome**: You'll understand the current, non-deprecated way to use AI models on Android

**Verification Questions**:
- What's the correct dependency to add to build.gradle.kts?
- Are there any new initialization steps?
- Has the Interpreter class changed?

### Step 2: Understand Super Resolution Model Types
**Goal**: Learn what models exist and their trade-offs

**Model Categories**:

#### A. **ESRGAN (Enhanced Super Resolution GAN)**
- **What it is**: Uses AI "competition" between two networks
- **Strength**: Very high quality, realistic textures
- **Weakness**: Larger file size (15-30MB), slower processing
- **Best for**: High-quality results, not real-time

#### B. **SRCNN (Super Resolution CNN)**
- **What it is**: Simple convolutional neural network
- **Strength**: Fast processing, small file size (2-5MB)
- **Weakness**: Lower quality than ESRGAN
- **Best for**: Real-time applications

#### C. **Real-ESRGAN**
- **What it is**: ESRGAN optimized for real-world images
- **Strength**: Handles artifacts and noise well
- **Weakness**: Medium complexity
- **Best for**: Thermal images (which often have noise/artifacts)

**Tasks**:
1. **Visit TensorFlow Hub**: https://tfhub.dev/
2. **Search for**: "super resolution"
3. **Filter by**: "Lite" (for mobile-optimized models)
4. **Document findings**: Available models, input/output sizes, file sizes

**Learning Outcome**: You'll know what models are available and their characteristics

**Verification Questions**:
- Which models accept 256x192 input?
- What are the output resolutions (2x? 4x?)?
- Which models work with grayscale vs RGB?

### Step 3: Create Isolated Test Environment
**Goal**: Build a simple test app separate from your camera code

**Why Separate?**
- Avoids breaking existing working camera functionality
- Easier to debug AI-specific issues
- Can test with sample images before integrating

**Project Structure**:
```
app/src/main/
├── java/.../tflite_test/           # NEW package for testing
│   ├── TfLiteTestActivity.kt       # Simple test UI
│   └── SuperResolutionTester.kt    # Model testing class
├── assets/models/                  # AI model files
│   └── test_model.tflite
└── res/layout/
    └── activity_tflite_test.xml    # Simple test interface
```

**Test Activity Features**:
- Load sample thermal image (256x192)
- Run super resolution model
- Display before/after comparison
- Show processing time
- Log detailed information

**Tasks**:
1. **Create new Activity**: `TfLiteTestActivity.kt`
2. **Design simple layout**: Image views for before/after
3. **Add test button**: "Process Image"
4. **Create sample images**: 256x192 thermal-like test images

**Learning Outcome**: You'll have a safe testing environment

### Step 4: Download and Test Models
**Goal**: Actually get models working with your test images

**Model Testing Process**:

#### A. **Download Models**
1. **From TensorFlow Hub**:
   - Search for mobile super resolution models
   - Download `.tflite` files
   - Note input/output specifications

2. **Alternative Sources**:
   - GitHub repositories with pre-converted models
   - ONNX models converted to TensorFlow Lite
   - Custom models from research papers

#### B. **Model Information Extraction**
For each model, document:
```
Model: esrgan_mobile_2x.tflite
- Input Shape: [1, 192, 256, 3] (batch, height, width, channels)
- Output Shape: [1, 384, 512, 3] (2x resolution)
- Input Range: [0, 1] or [-1, 1] (normalization)
- File Size: 15.2 MB
- Expected Performance: ~80ms on Pixel 7
```

#### C. **Basic Model Loading Test**
```kotlin
// Test 1: Can we load the model?
val model = loadModelFromAssets("esrgan_mobile_2x.tflite")
val interpreter = Interpreter(model)

// Test 2: What are the input/output shapes?
val inputDetails = interpreter.getInputTensor(0)
Log.d("Model", "Input shape: ${inputDetails.shape().contentToString()}")
```

**Tasks**:
1. **Download 3 different models**: ESRGAN, SRCNN, Real-ESRGAN variants
2. **Test loading**: Verify each model loads without errors
3. **Extract specifications**: Document input/output requirements
4. **Create model comparison table**: Size, speed, quality trade-offs

**Learning Outcome**: You'll know which models are compatible with your needs

### Step 5: Understand Thermal Image Preprocessing
**Goal**: Learn how to convert thermal images for AI model input

**Thermal Image Characteristics**:
- **Format**: Usually grayscale (single channel) or false-color
- **Data Range**: Temperature values mapped to pixel intensities
- **Resolution**: 256x192 for your MINI2-256 camera
- **Color Space**: Might be grayscale, or RGB with thermal colormap

**Preprocessing Pipeline**:
```
Thermal Image (256x192) 
    ↓
Format Detection (Grayscale? RGB?)
    ↓
Channel Conversion (Gray→RGB if needed)
    ↓
Normalization ([0,255] → [0,1] or [-1,1])
    ↓
Array Conversion (Bitmap → FloatArray)
    ↓
Model Input Format [1, 192, 256, 3]
```

**Key Questions to Answer**:
1. **Input Format**: Do our thermal images come as Bitmap objects?
2. **Color Channels**: Are they grayscale or already RGB?
3. **Pixel Values**: Are they 0-255 integers or floating point?
4. **Model Requirements**: Does our chosen model expect RGB or accept grayscale?

**Tasks**:
1. **Capture sample thermal images** from your existing camera app
2. **Analyze image properties**: Format, channels, value ranges
3. **Create conversion functions**: Thermal → Model input format
4. **Test preprocessing**: Verify conversion works correctly

**Learning Outcome**: You'll understand how to prepare thermal data for AI processing

### Step 6: Implement Basic Model Inference
**Goal**: Get a working end-to-end pipeline

**Minimal Working Example**:
```kotlin
class SuperResolutionTester {
    private lateinit var interpreter: Interpreter
    
    fun initialize() {
        // Load model
        val model = loadModelFromAssets("test_model.tflite")
        interpreter = Interpreter(model)
    }
    
    fun processImage(inputBitmap: Bitmap): Bitmap {
        // 1. Convert bitmap to model input format
        val inputArray = preprocessBitmap(inputBitmap)
        
        // 2. Prepare output buffer
        val outputArray = createOutputBuffer()
        
        // 3. Run the model
        interpreter.run(inputArray, outputArray)
        
        // 4. Convert output back to bitmap
        return postprocessToBitmap(outputArray)
    }
}
```

**Tasks**:
1. **Implement model loading**: From assets folder
2. **Create preprocessing function**: Bitmap → FloatArray
3. **Set up inference**: Input → Model → Output
4. **Implement postprocessing**: FloatArray → Bitmap
5. **Test with sample image**: Verify it produces output

**Learning Outcome**: You'll have a working super resolution pipeline

### Step 7: Performance Benchmarking
**Goal**: Measure if models meet real-time requirements

**Metrics to Measure**:
1. **Processing Time**: How long does inference take?
2. **Memory Usage**: How much RAM does the model use?
3. **Quality Assessment**: Visual comparison of results
4. **Device Impact**: Does it affect other app functions?

**Benchmarking Code**:
```kotlin
fun benchmarkModel(testImages: List<Bitmap>) {
    val processingTimes = mutableListOf<Long>()
    
    testImages.forEach { image ->
        val startTime = System.currentTimeMillis()
        val result = processImage(image)
        val endTime = System.currentTimeMillis()
        
        processingTimes.add(endTime - startTime)
    }
    
    val averageTime = processingTimes.average()
    Log.d("Benchmark", "Average processing time: ${averageTime}ms")
}
```

**Performance Targets**:
- **Real-time Goal**: <100ms processing for 30 FPS video
- **Acceptable Goal**: <200ms for 15 FPS video
- **Memory Goal**: <50MB additional RAM usage

**Tasks**:
1. **Create test image set**: 10-20 sample thermal images
2. **Measure processing times**: For each model candidate
3. **Test memory usage**: Monitor RAM consumption
4. **Visual quality assessment**: Compare outputs side-by-side
5. **Document results**: Create comparison table

**Learning Outcome**: You'll know which model best fits your performance requirements

## 🔍 Verification Checkpoints

After each step, answer these questions:

### Step 1 Verification:
- [ ] What's the current LiteRT dependency name?
- [ ] Are there any breaking changes from TensorFlow Lite?
- [ ] What's the recommended initialization approach?

### Step 2 Verification:
- [ ] How many suitable super resolution models did you find?
- [ ] What are their input/output size requirements?
- [ ] Which models support grayscale input?

### Step 3 Verification:
- [ ] Can you run the test activity without errors?
- [ ] Does the basic UI display images correctly?
- [ ] Is the project structure clean and separate?

### Step 4 Verification:
- [ ] How many models successfully loaded?
- [ ] What are the exact input/output specifications?
- [ ] Which models seem most promising?

### Step 5 Verification:
- [ ] What format do your thermal images use?
- [ ] Can you successfully convert thermal → model input?
- [ ] Do the converted values look reasonable?

### Step 6 Verification:
- [ ] Does the basic inference pipeline work?
- [ ] Do you get output images (even if quality is poor)?
- [ ] Are there any error messages or crashes?

### Step 7 Verification:
- [ ] What are the processing times for each model?
- [ ] Which model offers the best speed/quality trade-off?
- [ ] Is real-time processing feasible?

## 📋 Deliverables for Phase 1

At the end of Phase 1, you should have:

1. **Model Comparison Table**:
   ```
   | Model | Size | Speed | Quality | Thermal Compatibility |
   |-------|------|-------|---------|---------------------|
   | ESRGAN-Mobile | 15MB | 80ms | High | Good |
   | SRCNN-Lite | 3MB | 20ms | Medium | Excellent |
   | Real-ESRGAN | 10MB | 50ms | High | Excellent |
   ```

2. **Working Test Application**: Simple app that can process thermal images

3. **Performance Benchmarks**: Detailed measurements on your Pixel 7

4. **Technical Documentation**: What you learned about model requirements

5. **Recommendation**: Which model to use for Phase 2 integration

## 🎓 Learning Outcomes

By completing Phase 1, you'll understand:

- **LiteRT/TensorFlow Lite fundamentals**: How mobile AI works
- **Model selection criteria**: Speed vs quality trade-offs
- **Android development basics**: Activities, layouts, dependencies
- **Image processing concepts**: Bitmaps, arrays, preprocessing
- **Performance considerations**: Real-time constraints and optimization
- **Thermal image characteristics**: How they differ from regular photos

This foundation will make Phase 2 (integration with your camera app) much smoother and less error-prone.

---
**Created**: 2025-01-08  
**Estimated Duration**: 2-3 days  
**Prerequisites**: Android Studio setup, basic Kotlin knowledge  
**Next Phase**: Integration with existing camera application