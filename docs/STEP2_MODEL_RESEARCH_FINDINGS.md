# Step 2: Super Resolution Model Research Findings

## 🎯 Research Focus: Technical Validation Models

**Purpose**: Find working super resolution models for LiteRT pipeline validation (quality is secondary - we'll use custom thermal models for final quality)

## 📊 Available Models for Phase 1 Testing

### 1. **ESRGAN (Enhanced Super-Resolution GAN)** ⭐ RECOMMENDED

#### **Official TensorFlow Implementation**
- **Source**: TensorFlow Hub (`tfhub.dev/captain-pool/esrgan-tf2/1`)
- **File Size**: ~5 MB
- **Input Shape**: `[1, 50, 50, 3]` (Batch, Height, Width, RGB)
- **Output Shape**: `[1, 200, 200, 3]` (4x upscaling)
- **Format**: RGB only (3 channels required)
- **Download**: Kaggle (`kaggle.com/models/kaggle/esrgan-tf2/TfLite/esrgan-tf2/1`)

#### **Compatibility Assessment**
✅ **LiteRT Compatible**: Official TensorFlow Lite model  
❌ **Size Limitation**: Fixed 50x50 input (doesn't match our 256x192)  
❌ **RGB Only**: Requires 3-channel input (thermal is usually grayscale)  
✅ **Well Documented**: Extensive Android examples available  

#### **Why Use for Testing**
- Official TensorFlow support
- Proven Android integration
- Good for learning LiteRT pipeline
- Fast conversion for custom models later

### 2. **Real-ESRGAN Mobile** 🚀 HIGH PERFORMANCE

#### **Mobile-Optimized Implementation**
- **Source**: Qualcomm AI Hub, GitHub releases
- **File Size**: ~67 MB (RealESRGAN_x4plus.pth → needs TFLite conversion)
- **Input Shape**: `[1, 128, 128, 3]` (optimized for mobile)
- **Output Shape**: 4x upscaling
- **Performance**: 67ms on Samsung Galaxy S23 Ultra
- **Parameters**: 16.7M parameters

#### **Compatibility Assessment**
⚠️ **Conversion Required**: PyTorch → TensorFlow Lite conversion needed  
❌ **Size Mismatch**: 128x128 input doesn't match our 256x192  
✅ **Mobile Optimized**: Excellent Pixel 7 performance expected  
❌ **RGB Requirement**: 3-channel input needed  

#### **Why Consider**
- Excellent mobile performance
- Real-world image optimization (better for thermal artifacts)
- Recent 2024 updates

### 3. **SRCNN (Simple CNN)** 🎯 LIGHTWEIGHT OPTION

#### **Characteristics**
- **Architecture**: Simple convolutional layers only
- **File Size**: ~2-5 MB (estimated)
- **Processing**: Very fast inference
- **Flexibility**: Can handle various input sizes
- **Quality**: Lower than ESRGAN but acceptable

#### **Compatibility Assessment**
✅ **Size Flexible**: Can process different input dimensions  
✅ **Lightweight**: Fast processing for real-time testing  
⚠️ **Manual Implementation**: Need to find/create TFLite version  
✅ **Simpler Pipeline**: Easier debugging and learning  

#### **Why Use for Testing**
- Flexible input sizes
- Fast processing for pipeline testing
- Simple architecture for understanding
- Good baseline for custom model comparison

## 🔧 Technical Constraints Analysis

### **Major Challenge: Input Size Mismatch**
**Our Requirement**: 256x192 thermal images  
**Available Models**: 50x50, 128x128, or dynamic sizing  

**Solutions**:
1. **Preprocessing Resize**: 256x192 → Model Input Size → Resize Output
2. **Model Conversion**: Convert existing models to accept 256x192
3. **Find Flexible Models**: Use models that accept variable input sizes

### **Channel Format Challenge**
**Our Data**: Grayscale thermal images (1 channel)  
**Most Models**: RGB input (3 channels required)  

**Solutions**:
1. **Channel Duplication**: Grayscale → RGB (duplicate channels)
2. **Find Grayscale Models**: Search for single-channel models
3. **Custom Preprocessing**: Convert thermal → pseudo-RGB

## 🎯 Recommended Models for Phase 1 Testing

### **Primary Choice: ESRGAN (Official TensorFlow)**
**Why**: 
- ✅ Official LiteRT support and documentation
- ✅ Proven Android integration examples
- ✅ Good for learning pipeline fundamentals
- ✅ Fast model loading and inference testing

**Adaptation Strategy**:
```kotlin
// Preprocessing pipeline for ESRGAN testing
fun adaptThermalForESRGAN(thermal256x192: Bitmap): Bitmap {
    // 1. Resize: 256x192 → 50x50
    val resized = Bitmap.createScaledBitmap(thermal256x192, 50, 50, true)
    
    // 2. Convert: Grayscale → RGB
    val rgb = convertGrayscaleToRGB(resized)
    
    return rgb
}

// Postprocessing for display
fun adaptESRGANOutput(output200x200: Bitmap): Bitmap {
    // Resize back to target resolution if needed
    return Bitmap.createScaledBitmap(output200x200, 512, 384, true)
}
```

### **Secondary Choice: Custom SRCNN Implementation**
**Why**:
- ✅ More flexible with input sizes
- ✅ Lighter weight for faster iteration
- ✅ Better match for thermal image characteristics

**Implementation Strategy**:
```python
# Convert custom SRCNN to TFLite
def convert_srcnn_to_tflite():
    # Train or load SRCNN with 256x192 input capability
    model = create_srcnn_model(input_shape=(192, 256, 1))  # Grayscale
    
    # Convert to TensorFlow Lite
    converter = tf.lite.TFLiteConverter.from_keras_model(model)
    tflite_model = converter.convert()
    
    return tflite_model
```

## 📋 Model Download Sources

### **Immediately Available Models**
1. **ESRGAN TFLite**:
   - Kaggle: `kaggle.com/models/kaggle/esrgan-tf2/TfLite/esrgan-tf2/1`
   - Direct download: ~5MB .tflite file

2. **Real-ESRGAN Weights**:
   - GitHub: `github.com/xinntao/Real-ESRGAN/releases`
   - Kaggle: `kaggle.com/datasets/djokester/real-esrgan-weights`
   - Requires PyTorch → TFLite conversion

### **Android Example Code**
- **Official TensorFlow Examples**: 
  - `github.com/tensorflow/examples/tree/master/lite/examples/super_resolution/android`
- **Community Examples**:
  - `github.com/tumuyan/ESRGAN-Android-TFLite-Demo`

## 🔍 Input/Output Compatibility Analysis

### **Current Thermal Camera Data**
- **Resolution**: 256x192 pixels
- **Format**: Likely grayscale or false-color RGB
- **Data Type**: 8-bit or 16-bit thermal values
- **Color Space**: Temperature-mapped intensities

### **Model Requirements Mapping**
| Model | Input Required | Our Data | Adaptation Needed |
|-------|---------------|----------|-------------------|
| ESRGAN | 50x50x3 RGB | 256x192x1 Gray | Resize + RGB conversion |
| Real-ESRGAN | 128x128x3 RGB | 256x192x1 Gray | Resize + RGB conversion |
| SRCNN | Flexible | 256x192x1 Gray | Minimal (if grayscale model) |

### **Preprocessing Pipeline Template**
```kotlin
class ThermalImagePreprocessor {
    fun preprocessForModel(
        thermalBitmap: Bitmap,
        targetWidth: Int,
        targetHeight: Int,
        requiresRGB: Boolean
    ): FloatArray {
        
        // Step 1: Resize to model input size
        val resized = Bitmap.createScaledBitmap(
            thermalBitmap, targetWidth, targetHeight, true
        )
        
        // Step 2: Handle channel conversion
        val processed = if (requiresRGB && isGrayscale(resized)) {
            convertGrayscaleToRGB(resized)
        } else {
            resized
        }
        
        // Step 3: Convert to model input format
        return bitmapToFloatArray(processed)
    }
}
```

## 🚀 Next Steps for Step 3

### **Download and Test Priority**
1. **First**: ESRGAN official TFLite model (easiest integration)
2. **Second**: Find or create SRCNN TFLite model (better flexibility)
3. **Third**: Convert Real-ESRGAN if needed (highest quality)

### **Testing Strategy**
1. **Basic Loading**: Can we load the model in LiteRT?
2. **Size Validation**: Do input/output dimensions match expectations?
3. **Simple Inference**: Can we run a test image through the model?
4. **Performance Measurement**: What's the processing time on Pixel 7?

### **Success Criteria for Phase 1**
- ✅ At least one model loads successfully in LiteRT
- ✅ Can process 256x192 thermal images (with adaptation)
- ✅ Generates output images (quality not critical)
- ✅ Processing time measured and documented
- ✅ Framework ready for custom thermal model integration

## 📊 Expected Performance Targets

### **Pixel 7 Performance Estimates**
- **ESRGAN (5MB)**: ~30-50ms processing time
- **Real-ESRGAN (67MB)**: ~60-80ms processing time  
- **SRCNN (2-5MB)**: ~10-20ms processing time

### **Memory Usage Estimates**
- **Model Loading**: 5-70MB RAM (depending on model)
- **Input Buffer**: ~1MB (256x192x4 bytes)
- **Output Buffer**: ~4MB (512x384x4 bytes)
- **Total**: ~10-75MB additional RAM usage

---
**Research Date**: January 8, 2025  
**Status**: ✅ Complete - Models identified for technical validation  
**Recommendation**: Start with ESRGAN official TFLite model  
**Next Step**: Step 3 - Create isolated test environment