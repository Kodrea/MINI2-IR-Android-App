# Saafke FSRCNN Quick Start Guide

## 🎯 **Goal**: Get FSRCNN working with 256x192 thermal images in Android

## 📁 **Available Pre-trained Models**

### **Direct Download Links**
```
Standard FSRCNN Models:
• FSRCNN_x2.pb  → https://github.com/Saafke/FSRCNN_Tensorflow/blob/master/models/FSRCNN_x2.pb
• FSRCNN_x3.pb  → https://github.com/Saafke/FSRCNN_Tensorflow/blob/master/models/FSRCNN_x3.pb
• FSRCNN_x4.pb  → https://github.com/Saafke/FSRCNN_Tensorflow/blob/master/models/FSRCNN_x4.pb

FSRCNN-Small Models (Faster):
• FSRCNN-small_x2.pb → https://github.com/Saafke/FSRCNN_Tensorflow/blob/master/models/FSRCNN-small_x2.pb
• FSRCNN-small_x3.pb → https://github.com/Saafke/FSRCNN_Tensorflow/blob/master/models/FSRCNN-small_x3.pb
• FSRCNN-small_x4.pb → https://github.com/Saafke/FSRCNN_Tensorflow/blob/master/models/FSRCNN-small_x4.pb
```

## 🥇 **Recommended Choice for Testing**

### **FSRCNN_x2.pb** ⭐ START HERE
- **Scale**: 2x upscaling (256x192 → 512x384)
- **Quality**: High accuracy
- **Size**: Moderate file size
- **Perfect for**: Initial validation with thermal images

### **Alternative: FSRCNN-small_x2.pb** ⚡ 
- **Scale**: 2x upscaling 
- **Quality**: Good (slightly lower than standard)
- **Speed**: Faster processing
- **Perfect for**: Real-time performance testing

## 🔧 **Step-by-Step Implementation**

### **Step 1: Download Model** 
```bash
# Download the x2 model for 2x super resolution
wget https://github.com/Saafke/FSRCNN_Tensorflow/raw/master/models/FSRCNN_x2.pb

# Or download FSRCNN-small for faster processing
wget https://github.com/Saafke/FSRCNN_Tensorflow/raw/master/models/FSRCNN-small_x2.pb
```

### **Step 2: Convert .pb to TensorFlow Lite**
```python
import tensorflow as tf

def convert_pb_to_tflite(pb_file_path, output_path):
    # Load .pb model
    converter = tf.lite.TFLiteConverter.from_saved_model(pb_file_path)
    
    # Optional: Optimize for mobile
    converter.optimizations = [tf.lite.Optimize.DEFAULT]
    
    # Convert
    tflite_model = converter.convert()
    
    # Save TFLite model
    with open(output_path, 'wb') as f:
        f.write(tflite_model)
    
    print(f"Converted {pb_file_path} to {output_path}")

# Convert FSRCNN x2 model
convert_pb_to_tflite('FSRCNN_x2.pb', 'fsrcnn_x2.tflite')
```

### **Step 3: Test Model Compatibility**
```python
import tensorflow as tf
import numpy as np
from PIL import Image

def test_model_with_256x192():
    # Load TFLite model
    interpreter = tf.lite.Interpreter(model_path='fsrcnn_x2.tflite')
    interpreter.allocate_tensors()
    
    # Get input details
    input_details = interpreter.get_input_details()
    output_details = interpreter.get_output_details()
    
    print("Input shape:", input_details[0]['shape'])
    print("Output shape:", output_details[0]['shape'])
    
    # Test with 256x192 thermal-like image
    test_image = np.random.rand(1, 192, 256, 1).astype(np.float32)  # Grayscale
    
    # Set input
    interpreter.set_tensor(input_details[0]['index'], test_image)
    
    # Run inference
    interpreter.invoke()
    
    # Get output
    output = interpreter.get_tensor(output_details[0]['index'])
    print("Output processed successfully:", output.shape)

# Test the model
test_model_with_256x192()
```

### **Step 4: Create Android Assets**
```
app/src/main/assets/models/
└── fsrcnn_x2.tflite
```

### **Step 5: Android Implementation**
```kotlin
class FSRCNNProcessor {
    private lateinit var interpreter: Interpreter
    
    fun initialize(context: Context) {
        // Load model from assets
        val modelBuffer = loadModelFromAssets(context, "models/fsrcnn_x2.tflite")
        
        // Configure for performance
        val options = Interpreter.Options().apply {
            addDelegate(GpuDelegate())
            setNumThreads(4)
        }
        
        interpreter = Interpreter(modelBuffer, options)
        
        // Log model details
        val inputTensor = interpreter.getInputTensor(0)
        val outputTensor = interpreter.getOutputTensor(0)
        Log.d("FSRCNN", "Input shape: ${inputTensor.shape().contentToString()}")
        Log.d("FSRCNN", "Output shape: ${outputTensor.shape().contentToString()}")
    }
    
    fun enhanceThermalImage(thermalBitmap: Bitmap): Bitmap {
        // Preprocess: 256x192 thermal → model input format
        val inputArray = preprocessThermalBitmap(thermalBitmap)
        
        // Prepare output buffer for 2x resolution
        val outputArray = Array(1) { Array(384) { Array(512) { FloatArray(1) } } }
        
        // Run FSRCNN inference
        interpreter.run(inputArray, outputArray)
        
        // Convert back to bitmap
        return postprocessToThermalBitmap(outputArray)
    }
    
    private fun preprocessThermalBitmap(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        // Ensure correct size
        val resized = if (bitmap.width != 256 || bitmap.height != 192) {
            Bitmap.createScaledBitmap(bitmap, 256, 192, true)
        } else {
            bitmap
        }
        
        // Convert to grayscale float array
        val inputArray = Array(1) { Array(192) { Array(256) { FloatArray(1) } } }
        
        for (y in 0 until 192) {
            for (x in 0 until 256) {
                val pixel = resized.getPixel(x, y)
                val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                inputArray[0][y][x][0] = gray / 255.0f  // Normalize to [0,1]
            }
        }
        
        return inputArray
    }
    
    private fun postprocessToThermalBitmap(output: Array<Array<Array<FloatArray>>>): Bitmap {
        val height = output[0].size  // 384
        val width = output[0][0].size  // 512
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = (output[0][y][x][0] * 255).toInt().coerceIn(0, 255)
                val pixel = Color.rgb(value, value, value)  // Grayscale
                bitmap.setPixel(x, y, pixel)
            }
        }
        
        return bitmap
    }
    
    private fun loadModelFromAssets(context: Context, filename: String): ByteBuffer {
        val assetFileDescriptor = context.assets.openFd(filename)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
```

## 🧪 **Testing Strategy**

### **Phase 1: Desktop Validation**
1. Download FSRCNN_x2.pb
2. Convert to TensorFlow Lite
3. Test with sample 256x192 images
4. Verify input/output dimensions

### **Phase 2: Android Integration**
1. Add TFLite model to Android assets
2. Implement FSRCNNProcessor class
3. Test with sample thermal images
4. Measure performance on Pixel 7

### **Phase 3: Real Thermal Testing**
1. Capture 256x192 thermal images from your camera
2. Process through FSRCNN
3. Compare original vs enhanced images
4. Validate quality improvement

## ⚡ **Expected Results**

### **Input → Output**
- **256x192 thermal image** → **512x384 enhanced image**
- **Processing time**: ~15-25ms on Pixel 7
- **Quality**: Sharper edges, enhanced details
- **Memory**: ~10-15MB usage

### **Success Criteria**
- ✅ Model loads without errors
- ✅ Processes 256x192 images correctly
- ✅ Outputs 512x384 enhanced images
- ✅ No crashes during continuous use
- ✅ Visible quality improvement

## 🚀 **Next Steps**

1. **Download FSRCNN_x2.pb** from GitHub
2. **Test conversion** to TensorFlow Lite
3. **Verify compatibility** with 256x192 input
4. **Create Android test app** with model integration

This approach gives you the fastest path from download to working Android implementation!

---
**Model Source**: Saafke/FSRCNN_Tensorflow  
**Recommended**: FSRCNN_x2.pb for 2x super resolution  
**Target**: 256x192 → 512x384 thermal image enhancement  
**Timeline**: 1-2 days to working Android implementation