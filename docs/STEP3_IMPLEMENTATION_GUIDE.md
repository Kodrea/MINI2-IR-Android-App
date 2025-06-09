# Step 3: FSRCNN Implementation Guide

## 🚀 **Let's Build the FSRCNN Super Resolution Pipeline**

Follow these steps in order to get FSRCNN working with your 256x192 thermal images.

## 📥 **Step 1: Download the FSRCNN Model**

### **Download FSRCNN_x2.pb**
```bash
# Create a working directory
mkdir fsrcnn_thermal_test
cd fsrcnn_thermal_test

# Download the pre-trained FSRCNN x2 model (2x super resolution)
wget https://github.com/Saafke/FSRCNN_Tensorflow/raw/master/models/FSRCNN_x2.pb

# Verify download
ls -la FSRCNN_x2.pb
```

**Expected Result**: You should have `FSRCNN_x2.pb` file (~2-5MB)

## 🔄 **Step 2: Convert to TensorFlow Lite**

### **Create Conversion Script**
Create a file called `convert_to_tflite.py`:

```python
import tensorflow as tf
import numpy as np

def convert_fsrcnn_to_tflite():
    print("Converting FSRCNN_x2.pb to TensorFlow Lite...")
    
    try:
        # Load the .pb model
        converter = tf.lite.TFLiteConverter.from_saved_model('FSRCNN_x2.pb')
        
        # Optimize for mobile
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        
        # Convert to TFLite
        tflite_model = converter.convert()
        
        # Save TFLite model
        with open('fsrcnn_x2.tflite', 'wb') as f:
            f.write(tflite_model)
        
        print("✅ Conversion successful!")
        print(f"✅ Created: fsrcnn_x2.tflite ({len(tflite_model)} bytes)")
        
        return True
        
    except Exception as e:
        print(f"❌ Conversion failed: {e}")
        return False

def test_tflite_model():
    print("\nTesting TFLite model compatibility...")
    
    try:
        # Load TFLite model
        interpreter = tf.lite.Interpreter(model_path='fsrcnn_x2.tflite')
        interpreter.allocate_tensors()
        
        # Get input/output details
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()
        
        print("📋 Model Details:")
        print(f"  Input shape: {input_details[0]['shape']}")
        print(f"  Input dtype: {input_details[0]['dtype']}")
        print(f"  Output shape: {output_details[0]['shape']}")
        print(f"  Output dtype: {output_details[0]['dtype']}")
        
        # Test with 256x192 input (thermal resolution)
        test_input = np.random.rand(1, 192, 256, 1).astype(np.float32)
        print(f"\n🧪 Testing with thermal resolution: {test_input.shape}")
        
        # Set input tensor
        interpreter.set_tensor(input_details[0]['index'], test_input)
        
        # Run inference
        interpreter.invoke()
        
        # Get output
        output = interpreter.get_tensor(output_details[0]['index'])
        print(f"✅ Output shape: {output.shape}")
        print(f"✅ Output range: [{output.min():.3f}, {output.max():.3f}]")
        
        # Verify 2x upscaling
        if output.shape[1] == 384 and output.shape[2] == 512:
            print("✅ Perfect! 256x192 → 512x384 (2x super resolution)")
        else:
            print(f"⚠️ Unexpected output size: {output.shape}")
        
        return True
        
    except Exception as e:
        print(f"❌ Test failed: {e}")
        return False

if __name__ == "__main__":
    # Step 1: Convert model
    if convert_fsrcnn_to_tflite():
        # Step 2: Test compatibility
        test_tflite_model()
    else:
        print("❌ Cannot proceed with testing due to conversion failure")
```

### **Run the Conversion**
```bash
# Make sure you have TensorFlow installed
pip install tensorflow

# Run the conversion script
python convert_to_tflite.py
```

**Expected Output**:
```
Converting FSRCNN_x2.pb to TensorFlow Lite...
✅ Conversion successful!
✅ Created: fsrcnn_x2.tflite (XXXX bytes)

Testing TFLite model compatibility...
📋 Model Details:
  Input shape: [1, None, None, 1]
  Input dtype: <class 'numpy.float32'>
  Output shape: [1, None, None, 1]
  Output dtype: <class 'numpy.float32'>

🧪 Testing with thermal resolution: (1, 192, 256, 1)
✅ Output shape: (1, 384, 512, 1)
✅ Output range: [0.xxx, 1.xxx]
✅ Perfect! 256x192 → 512x384 (2x super resolution)
```

## 🏗️ **Step 3: Create Android Test Project**

### **Add Dependencies to build.gradle.kts (app)**
```kotlin
dependencies {
    // Existing dependencies...
    
    // LiteRT (TensorFlow Lite) dependencies
    implementation("com.google.android.gms:play-services-tflite-java:16.4.0")
    implementation("com.google.android.gms:play-services-tflite-support:16.4.0")
    
    // Or direct LiteRT (alternative)
    // implementation("com.google.ai.edge.litert:litert:1.0.1")
    // implementation("com.google.ai.edge.litert:litert-gpu:1.0.1")
}
```

### **Create Assets Directory Structure**
```
app/src/main/assets/
└── models/
    └── fsrcnn_x2.tflite
```

Copy your `fsrcnn_x2.tflite` file to `app/src/main/assets/models/`

### **Create Test Activity**
Create `app/src/main/java/com/example/ircmd_handle/TfLiteTestActivity.kt`:

```kotlin
package com.example.ircmd_handle

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

class TfLiteTestActivity : AppCompatActivity() {
    
    private lateinit var interpreter: Interpreter
    private lateinit var originalImageView: ImageView
    private lateinit var enhancedImageView: ImageView
    private lateinit var statusTextView: TextView
    private lateinit var processButton: Button
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tflite_test)
        
        initializeViews()
        initializeModel()
    }
    
    private fun initializeViews() {
        originalImageView = findViewById(R.id.originalImageView)
        enhancedImageView = findViewById(R.id.enhancedImageView)
        statusTextView = findViewById(R.id.statusTextView)
        processButton = findViewById(R.id.processButton)
        
        processButton.setOnClickListener {
            testSuperResolution()
        }
    }
    
    private fun initializeModel() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val modelBuffer = loadModelFromAssets("models/fsrcnn_x2.tflite")
                
                val options = Interpreter.Options().apply {
                    setNumThreads(4)
                    // Add GPU delegate if available
                    // addDelegate(GpuDelegate())
                }
                
                interpreter = Interpreter(modelBuffer, options)
                
                // Log model details
                val inputTensor = interpreter.getInputTensor(0)
                val outputTensor = interpreter.getOutputTensor(0)
                
                withContext(Dispatchers.Main) {
                    statusTextView.text = """
                        ✅ FSRCNN Model Loaded Successfully
                        
                        📋 Model Details:
                        • Input: ${inputTensor.shape().contentToString()}
                        • Output: ${outputTensor.shape().contentToString()}
                        • Data Type: ${inputTensor.dataType()}
                        
                        Ready to test with 256x192 thermal images!
                    """.trimIndent()
                    
                    processButton.isEnabled = true
                }
                
            } catch (e: Exception) {
                Log.e("TfLiteTest", "Model loading failed", e)
                withContext(Dispatchers.Main) {
                    statusTextView.text = "❌ Model loading failed: ${e.message}"
                }
            }
        }
    }
    
    private fun testSuperResolution() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withContext(Dispatchers.Main) {
                    statusTextView.text = "🔄 Processing thermal image..."
                    processButton.isEnabled = false
                }
                
                // Create test thermal image (256x192 grayscale)
                val originalBitmap = createTestThermalImage()
                
                // Enhance with FSRCNN
                val startTime = System.currentTimeMillis()
                val enhancedBitmap = enhanceImage(originalBitmap)
                val processingTime = System.currentTimeMillis() - startTime
                
                withContext(Dispatchers.Main) {
                    // Display results
                    originalImageView.setImageBitmap(originalBitmap)
                    enhancedImageView.setImageBitmap(enhancedBitmap)
                    
                    statusTextView.text = """
                        ✅ Super Resolution Complete!
                        
                        📊 Results:
                        • Input: ${originalBitmap.width}x${originalBitmap.height}
                        • Output: ${enhancedBitmap.width}x${enhancedBitmap.height}
                        • Processing Time: ${processingTime}ms
                        • Scale Factor: 2x
                        
                        🎯 Ready for real thermal images!
                    """.trimIndent()
                    
                    processButton.isEnabled = true
                }
                
            } catch (e: Exception) {
                Log.e("TfLiteTest", "Processing failed", e)
                withContext(Dispatchers.Main) {
                    statusTextView.text = "❌ Processing failed: ${e.message}"
                    processButton.isEnabled = true
                }
            }
        }
    }
    
    private fun createTestThermalImage(): Bitmap {
        // Create a 256x192 test image simulating thermal data
        val bitmap = Bitmap.createBitmap(256, 192, Bitmap.Config.ARGB_8888)
        
        // Add some thermal-like patterns
        for (y in 0 until 192) {
            for (x in 0 until 256) {
                // Create a gradient with some noise
                val base = ((x + y) * 255 / (256 + 192)).coerceIn(0, 255)
                val noise = (Math.random() * 50 - 25).toInt()
                val value = (base + noise).coerceIn(0, 255)
                
                bitmap.setPixel(x, y, Color.rgb(value, value, value))
            }
        }
        
        return bitmap
    }
    
    private fun enhanceImage(inputBitmap: Bitmap): Bitmap {
        // Preprocess: Bitmap → Float Array
        val inputArray = preprocessBitmap(inputBitmap)
        
        // Prepare output buffer
        val outputArray = Array(1) { Array(384) { Array(512) { FloatArray(1) } } }
        
        // Run FSRCNN inference
        interpreter.run(inputArray, outputArray)
        
        // Postprocess: Float Array → Bitmap
        return postprocessToBitmap(outputArray)
    }
    
    private fun preprocessBitmap(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val inputArray = Array(1) { Array(192) { Array(256) { FloatArray(1) } } }
        
        for (y in 0 until 192) {
            for (x in 0 until 256) {
                val pixel = bitmap.getPixel(x, y)
                val gray = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                inputArray[0][y][x][0] = gray / 255.0f  // Normalize to [0,1]
            }
        }
        
        return inputArray
    }
    
    private fun postprocessToBitmap(output: Array<Array<Array<FloatArray>>>): Bitmap {
        val height = output[0].size  // 384
        val width = output[0][0].size  // 512
        
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = (output[0][y][x][0] * 255).toInt().coerceIn(0, 255)
                val pixel = Color.rgb(value, value, value)
                bitmap.setPixel(x, y, pixel)
            }
        }
        
        return bitmap
    }
    
    private fun loadModelFromAssets(filename: String): ByteBuffer {
        val assetFileDescriptor = assets.openFd(filename)
        val inputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = assetFileDescriptor.startOffset
        val declaredLength = assetFileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
```

### **Create Test Layout**
Create `app/src/main/res/layout/activity_tflite_test.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:orientation="vertical"
    android:padding="16dp">

    <TextView
        android:id="@+id/statusTextView"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Loading FSRCNN model..."
        android:textSize="14sp"
        android:fontFamily="monospace"
        android:background="#f0f0f0"
        android:padding="12dp"
        android:layout_marginBottom="16dp" />

    <Button
        android:id="@+id/processButton"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:text="Test Super Resolution"
        android:enabled="false"
        android:layout_marginBottom="16dp" />

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="0dp"
        android:layout_weight="1"
        android:orientation="horizontal">

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:orientation="vertical"
            android:padding="8dp">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Original (256x192)"
                android:textAlign="center"
                android:textStyle="bold"
                android:layout_marginBottom="8dp" />

            <ImageView
                android:id="@+id/originalImageView"
                android:layout_width="match_parent"
                android:layout_height="0dp"
                android:layout_weight="1"
                android:scaleType="fitCenter"
                android:background="#e0e0e0" />

        </LinearLayout>

        <LinearLayout
            android:layout_width="0dp"
            android:layout_height="match_parent"
            android:layout_weight="1"
            android:orientation="vertical"
            android:padding="8dp">

            <TextView
                android:layout_width="match_parent"
                android:layout_height="wrap_content"
                android:text="Enhanced (512x384)"
                android:textAlign="center"
                android:textStyle="bold"
                android:layout_marginBottom="8dp" />

            <ImageView
                android:id="@+id/enhancedImageView"
                android:layout_width="match_parent"
                android:layout_height="0dp"
                android:layout_weight="1"
                android:scaleType="fitCenter"
                android:background="#e0e0e0" />

        </LinearLayout>

    </LinearLayout>

</LinearLayout>
```

### **Add Activity to AndroidManifest.xml**
```xml
<activity
    android:name=".TfLiteTestActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

## 🧪 **Step 4: Test the Implementation**

### **Build and Run**
1. Copy `fsrcnn_x2.tflite` to `app/src/main/assets/models/`
2. Sync project in Android Studio
3. Run on your Pixel 7
4. Launch TfLiteTestActivity
5. Press "Test Super Resolution"

### **Expected Results**
- ✅ Model loads successfully
- ✅ Shows input/output shapes
- ✅ Processes 256x192 → 512x384
- ✅ Processing time ~15-25ms
- ✅ Side-by-side comparison displayed

## 🎯 **Success Criteria**
- [ ] FSRCNN model converts to TFLite successfully
- [ ] Android app loads model without errors
- [ ] Processes 256x192 test images
- [ ] Outputs 512x384 enhanced images
- [ ] Processing time < 30ms on Pixel 7

**Once this works, you'll have proven the entire pipeline for your custom thermal models!**

---
**Ready to start with Step 1 (downloading the model)?**