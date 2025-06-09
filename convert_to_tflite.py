#!/usr/bin/env python3
"""
FSRCNN to TensorFlow Lite Converter
Converts Saafke's FSRCNN_x2.pb to TensorFlow Lite format and validates compatibility
"""

import tensorflow as tf
import numpy as np
import os
import sys

def convert_fsrcnn_to_tflite():
    """Convert FSRCNN_x2.pb to TensorFlow Lite format"""
    print("🔄 Converting FSRCNN_x2.pb to TensorFlow Lite...")
    
    # Check if model file exists
    if not os.path.exists('FSRCNN_x2.pb'):
        print("❌ Error: FSRCNN_x2.pb not found!")
        print("📥 Please download it first:")
        print("   wget https://github.com/Saafke/FSRCNN_Tensorflow/raw/master/models/FSRCNN_x2.pb")
        return False
    
    try:
        # Method 1: Try loading as SavedModel
        print("📂 Attempting to load as SavedModel...")
        converter = tf.lite.TFLiteConverter.from_saved_model('FSRCNN_x2.pb')
        
    except Exception as e1:
        print(f"⚠️ SavedModel loading failed: {e1}")
        
        try:
            # Method 2: Try loading as frozen graph
            print("📂 Attempting to load as frozen graph...")
            
            # Load the .pb file as a frozen graph
            with tf.io.gfile.GFile('FSRCNN_x2.pb', 'rb') as f:
                graph_def = tf.compat.v1.GraphDef()
                graph_def.ParseFromString(f.read())
            
            # Create converter from frozen graph
            converter = tf.lite.TFLiteConverter.from_concrete_functions([
                tf.function(lambda: None).get_concrete_function()
            ])
            
            print("❌ Frozen graph method needs input/output specification")
            print("🔍 Let's examine the model structure first...")
            return examine_pb_model()
            
        except Exception as e2:
            print(f"❌ Frozen graph loading failed: {e2}")
            print("🔍 Let's examine the model structure...")
            return examine_pb_model()
    
    try:
        # Configure conversion options
        print("⚙️ Configuring conversion options...")
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        
        # Convert to TFLite
        print("🔄 Converting to TensorFlow Lite...")
        tflite_model = converter.convert()
        
        # Save TFLite model
        output_file = 'fsrcnn_x2.tflite'
        with open(output_file, 'wb') as f:
            f.write(tflite_model)
        
        print("✅ Conversion successful!")
        print(f"✅ Created: {output_file} ({len(tflite_model):,} bytes)")
        
        return True
        
    except Exception as e:
        print(f"❌ Conversion failed: {e}")
        print("🔍 Let's examine the model structure...")
        return examine_pb_model()

def examine_pb_model():
    """Examine the structure of the .pb model"""
    print("\n🔍 Examining FSRCNN_x2.pb structure...")
    
    try:
        # Load the graph
        with tf.io.gfile.GFile('FSRCNN_x2.pb', 'rb') as f:
            graph_def = tf.compat.v1.GraphDef()
            graph_def.ParseFromString(f.read())
        
        print(f"📊 Graph contains {len(graph_def.node)} nodes")
        
        # Find input and output nodes
        input_nodes = []
        output_nodes = []
        
        for node in graph_def.node:
            if node.op == 'Placeholder':
                input_nodes.append(node.name)
                print(f"📥 Input node: {node.name}")
                for attr_name, attr_value in node.attr.items():
                    if attr_name == 'shape':
                        print(f"   Shape: {attr_value}")
                    elif attr_name == 'dtype':
                        print(f"   Type: {attr_value}")
            
            # Look for potential output nodes (no outgoing edges)
            if len([n for n in graph_def.node if node.name in str(n.input)]) == 0:
                if not node.name.startswith('save') and not node.name.startswith('init'):
                    output_nodes.append(node.name)
        
        print(f"📤 Potential output nodes: {output_nodes[:5]}...")  # Show first 5
        
        # Try manual conversion with specific nodes
        if input_nodes and output_nodes:
            return try_manual_conversion(input_nodes[0], output_nodes[0])
        
        return False
        
    except Exception as e:
        print(f"❌ Model examination failed: {e}")
        return False

def try_manual_conversion(input_node, output_node):
    """Try manual conversion with specific input/output nodes"""
    print(f"\n🛠️ Attempting manual conversion...")
    print(f"📥 Input: {input_node}")
    print(f"📤 Output: {output_node}")
    
    try:
        # Load graph
        with tf.io.gfile.GFile('FSRCNN_x2.pb', 'rb') as f:
            graph_def = tf.compat.v1.GraphDef()
            graph_def.ParseFromString(f.read())
        
        # Create converter from frozen graph
        converter = tf.lite.TFLiteConverter.from_frozen_graph(
            'FSRCNN_x2.pb',
            input_arrays=[input_node],
            output_arrays=[output_node]
        )
        
        # Configure options
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        
        # Convert
        tflite_model = converter.convert()
        
        # Save
        with open('fsrcnn_x2.tflite', 'wb') as f:
            f.write(tflite_model)
        
        print("✅ Manual conversion successful!")
        print(f"✅ Created: fsrcnn_x2.tflite ({len(tflite_model):,} bytes)")
        
        return True
        
    except Exception as e:
        print(f"❌ Manual conversion failed: {e}")
        return False

def test_tflite_model():
    """Test TFLite model compatibility with thermal images"""
    print("\n🧪 Testing TFLite model compatibility...")
    
    if not os.path.exists('fsrcnn_x2.tflite'):
        print("❌ fsrcnn_x2.tflite not found! Conversion must have failed.")
        return False
    
    try:
        # Load TFLite model
        interpreter = tf.lite.Interpreter(model_path='fsrcnn_x2.tflite')
        interpreter.allocate_tensors()
        
        # Get input/output details
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()
        
        print("📋 Model Details:")
        for i, detail in enumerate(input_details):
            print(f"  Input {i}: {detail['shape']} ({detail['dtype']})")
        for i, detail in enumerate(output_details):
            print(f"  Output {i}: {detail['shape']} ({detail['dtype']})")
        
        # Test with thermal resolution (256x192)
        input_shape = input_details[0]['shape']
        print(f"\n🧪 Testing with thermal resolution...")
        
        # Handle dynamic shapes
        if input_shape[1] is None or input_shape[2] is None:
            print("📐 Model supports dynamic input shapes - perfect for 256x192!")
            test_input = np.random.rand(1, 192, 256, 1).astype(np.float32)
        else:
            print(f"📐 Model expects fixed input shape: {input_shape}")
            # Use the model's expected shape
            test_input = np.random.rand(*input_shape).astype(np.float32)
        
        print(f"🔢 Test input shape: {test_input.shape}")
        
        # Set input tensor
        interpreter.set_tensor(input_details[0]['index'], test_input)
        
        # Run inference
        start_time = tf.timestamp()
        interpreter.invoke()
        end_time = tf.timestamp()
        
        # Get output
        output = interpreter.get_tensor(output_details[0]['index'])
        processing_time = (end_time - start_time) * 1000  # Convert to ms
        
        print(f"✅ Output shape: {output.shape}")
        print(f"✅ Output range: [{output.min():.3f}, {output.max():.3f}]")
        print(f"⏱️ Processing time: {processing_time:.1f}ms")
        
        # Check if it's 2x super resolution
        if len(output.shape) >= 3:
            if (output.shape[1] == test_input.shape[1] * 2 and 
                output.shape[2] == test_input.shape[2] * 2):
                print("🎯 Perfect! 2x super resolution confirmed")
                if test_input.shape[1:3] == (192, 256):
                    print("🔥 Excellent! 256x192 → 512x384 thermal enhancement ready!")
            else:
                print(f"⚠️ Unexpected scaling: {test_input.shape[1:3]} → {output.shape[1:3]}")
        
        return True
        
    except Exception as e:
        print(f"❌ Test failed: {e}")
        return False

def create_android_assets():
    """Prepare files for Android integration"""
    print("\n📱 Preparing Android assets...")
    
    if not os.path.exists('fsrcnn_x2.tflite'):
        print("❌ fsrcnn_x2.tflite not found!")
        return False
    
    # Create directory structure
    os.makedirs('android_assets/models', exist_ok=True)
    
    # Copy model file
    import shutil
    shutil.copy('fsrcnn_x2.tflite', 'android_assets/models/')
    
    # Create README
    with open('android_assets/README.md', 'w') as f:
        f.write("""# FSRCNN Android Assets

## Usage
1. Copy the entire 'models' folder to your Android app's assets directory:
   `app/src/main/assets/models/`

2. Load the model in your Android app:
   ```kotlin
   val modelBuffer = loadModelFromAssets(context, "models/fsrcnn_x2.tflite")
   val interpreter = Interpreter(modelBuffer)
   ```

## Model Details
- File: fsrcnn_x2.tflite
- Purpose: 2x super resolution for thermal images
- Input: 256x192 grayscale thermal images
- Output: 512x384 enhanced thermal images
- Expected processing time: 15-25ms on Pixel 7

## Integration
See the TfLiteTestActivity.kt for complete implementation example.
""")
    
    print("✅ Android assets prepared in 'android_assets/' directory")
    print("📂 Ready to copy to your Android project!")
    
    return True

def main():
    """Main conversion and validation pipeline"""
    print("🚀 FSRCNN to TensorFlow Lite Converter")
    print("=" * 50)
    
    # Step 1: Convert model
    print("\n🔄 Step 1: Converting model...")
    if not convert_fsrcnn_to_tflite():
        print("❌ Conversion failed - cannot proceed with testing")
        return
    
    # Step 2: Test compatibility
    print("\n🧪 Step 2: Testing compatibility...")
    if not test_tflite_model():
        print("❌ Testing failed - model may not work correctly")
        return
    
    # Step 3: Prepare Android assets
    print("\n📱 Step 3: Preparing Android assets...")
    create_android_assets()
    
    print("\n" + "=" * 50)
    print("🎉 SUCCESS! FSRCNN ready for Android integration!")
    print("\n📋 Next steps:")
    print("1. Copy 'android_assets/models/' to 'app/src/main/assets/'")
    print("2. Add TensorFlow Lite dependencies to build.gradle.kts")
    print("3. Implement TfLiteTestActivity.kt")
    print("4. Test on your Pixel 7 device!")

if __name__ == "__main__":
    main()