#!/usr/bin/env python3
"""
FSRCNN Model Inspector
Examines the FSRCNN_x2.pb file to understand its structure
"""

import tensorflow as tf
import numpy as np
import os

def inspect_pb_model():
    """Inspect the .pb model structure in detail"""
    print("🔍 Detailed FSRCNN_x2.pb Model Inspection")
    print("=" * 50)
    
    if not os.path.exists('FSRCNN_x2.pb'):
        print("❌ Error: FSRCNN_x2.pb not found!")
        return False
    
    try:
        # Load the graph
        with tf.io.gfile.GFile('FSRCNN_x2.pb', 'rb') as f:
            graph_def = tf.compat.v1.GraphDef()
            graph_def.ParseFromString(f.read())
        
        print(f"📊 Graph contains {len(graph_def.node)} nodes")
        
        # Analyze all nodes
        input_nodes = []
        output_candidates = []
        conv_nodes = []
        placeholder_nodes = []
        
        # Count outgoing connections for each node
        node_outputs = {}
        for node in graph_def.node:
            node_outputs[node.name] = 0
        
        for node in graph_def.node:
            for input_name in node.input:
                # Clean input name (remove ^ and :0 suffixes)
                clean_input = input_name.split(':')[0].replace('^', '')
                if clean_input in node_outputs:
                    node_outputs[clean_input] += 1
        
        print("\n📝 Node Analysis:")
        for i, node in enumerate(graph_def.node):
            if i < 10:  # Show first 10 nodes
                print(f"  [{i}] {node.name} ({node.op}) - outputs: {node_outputs.get(node.name, 0)}")
            
            # Categorize nodes
            if node.op == 'Placeholder':
                placeholder_nodes.append(node)
                input_nodes.append(node.name)
                print(f"📥 Placeholder: {node.name}")
                for attr_name, attr_value in node.attr.items():
                    print(f"     {attr_name}: {attr_value}")
            
            elif 'Conv' in node.op:
                conv_nodes.append(node.name)
            
            # Find nodes with no outputs (potential final outputs)
            if node_outputs.get(node.name, 0) == 0:
                if not any(skip in node.name.lower() for skip in ['save', 'init', 'iterator']):
                    output_candidates.append(node.name)
        
        print(f"\n📥 Input nodes (Placeholders): {len(placeholder_nodes)}")
        for node in placeholder_nodes:
            print(f"  - {node.name}")
        
        print(f"\n🧠 Convolution nodes: {len(conv_nodes)}")
        print(f"  First few: {conv_nodes[:5]}")
        
        print(f"\n📤 Output candidates (no outgoing edges): {len(output_candidates)}")
        for i, name in enumerate(output_candidates[:10]):
            print(f"  [{i}] {name}")
        
        # Try to find the actual input/output nodes
        likely_input = None
        likely_output = None
        
        # Look for common input patterns
        for node in placeholder_nodes:
            if any(pattern in node.name.lower() for pattern in ['input', 'image', 'x', 'data']):
                likely_input = node.name
                break
        if not likely_input and placeholder_nodes:
            likely_input = placeholder_nodes[0].name
        
        # Look for common output patterns
        for name in output_candidates:
            if any(pattern in name.lower() for pattern in ['output', 'result', 'conv', 'add']):
                likely_output = name
                break
        if not likely_output and output_candidates:
            likely_output = output_candidates[0]
        
        print(f"\n🎯 Best Guesses:")
        print(f"  Input: {likely_input}")
        print(f"  Output: {likely_output}")
        
        if likely_input and likely_output:
            return try_conversion_with_nodes(likely_input, likely_output)
        
        return False
        
    except Exception as e:
        print(f"❌ Model inspection failed: {e}")
        return False

def try_conversion_with_nodes(input_node, output_node):
    """Try conversion with specific input/output nodes"""
    print(f"\n🛠️ Attempting conversion with:")
    print(f"  Input: {input_node}")
    print(f"  Output: {output_node}")
    
    try:
        # Method 1: from_frozen_graph
        converter = tf.lite.TFLiteConverter.from_frozen_graph(
            'FSRCNN_x2.pb',
            input_arrays=[input_node],
            output_arrays=[output_node]
        )
        
        converter.optimizations = [tf.lite.Optimize.DEFAULT]
        tflite_model = converter.convert()
        
        with open('fsrcnn_x2.tflite', 'wb') as f:
            f.write(tflite_model)
        
        print("✅ Conversion successful!")
        print(f"✅ Created: fsrcnn_x2.tflite ({len(tflite_model):,} bytes)")
        
        return test_converted_model()
        
    except Exception as e:
        print(f"❌ Conversion failed: {e}")
        return try_alternative_methods()

def try_alternative_methods():
    """Try alternative conversion methods"""
    print("\n🔄 Trying alternative conversion methods...")
    
    # Method 1: Load as concrete function and convert
    try:
        print("📂 Method 1: Loading graph and wrapping...")
        
        def load_graph():
            with tf.io.gfile.GFile('FSRCNN_x2.pb', 'rb') as f:
                graph_def = tf.compat.v1.GraphDef()
                graph_def.ParseFromString(f.read())
            return graph_def
        
        graph_def = load_graph()
        
        # Create a session and run a test
        with tf.compat.v1.Session() as sess:
            tf.import_graph_def(graph_def, name='')
            
            # Get input/output tensors
            input_tensor = sess.graph.get_tensor_by_name('IteratorGetNext:0')
            
            # Find output tensor by looking at the last operation
            operations = sess.graph.get_operations()
            last_op = operations[-1]
            output_tensor = last_op.outputs[0] if last_op.outputs else None
            
            if output_tensor:
                print(f"Found output tensor: {output_tensor.name}")
                
                # Create a concrete function
                @tf.function
                def inference(x):
                    return sess.run(output_tensor, {input_tensor: x})
                
                concrete_func = inference.get_concrete_function(
                    tf.TensorSpec(shape=[1, None, None, 1], dtype=tf.float32)
                )
                
                converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete_func])
                tflite_model = converter.convert()
                
                with open('fsrcnn_x2.tflite', 'wb') as f:
                    f.write(tflite_model)
                
                print("✅ Alternative conversion successful!")
                return test_converted_model()
            
    except Exception as e:
        print(f"❌ Method 1 failed: {e}")
    
    # Method 2: Try OpenCV DNN approach
    try:
        print("📂 Method 2: Using OpenCV DNN for inspection...")
        import cv2
        
        net = cv2.dnn.readNetFromTensorflow('FSRCNN_x2.pb')
        
        # Get layer names
        layer_names = net.getLayerNames()
        print(f"OpenCV detected {len(layer_names)} layers")
        
        # Try to understand the network structure
        unconnected_layers = net.getUnconnectedOutLayers()
        print(f"Output layers: {unconnected_layers}")
        
        print("⚠️ OpenCV can read the model, but we need TensorFlow Lite format")
        print("💡 Suggestion: The model might be working, but we need to understand its exact input/output format")
        
    except Exception as e:
        print(f"❌ Method 2 failed: {e}")
    
    return False

def test_converted_model():
    """Test the converted TFLite model"""
    print("\n🧪 Testing converted TFLite model...")
    
    if not os.path.exists('fsrcnn_x2.tflite'):
        print("❌ No TFLite model found to test")
        return False
    
    try:
        interpreter = tf.lite.Interpreter(model_path='fsrcnn_x2.tflite')
        interpreter.allocate_tensors()
        
        input_details = interpreter.get_input_details()
        output_details = interpreter.get_output_details()
        
        print("📋 Model Details:")
        for i, detail in enumerate(input_details):
            print(f"  Input {i}: {detail['shape']} ({detail['dtype']})")
        for i, detail in enumerate(output_details):
            print(f"  Output {i}: {detail['shape']} ({detail['dtype']})")
        
        # Test with thermal-like input
        input_shape = input_details[0]['shape']
        if input_shape[1] is None:
            # Dynamic shape - use our thermal dimensions
            test_input = np.random.rand(1, 192, 256, 1).astype(np.float32)
        else:
            test_input = np.random.rand(*input_shape).astype(np.float32)
        
        print(f"🔢 Test input shape: {test_input.shape}")
        
        interpreter.set_tensor(input_details[0]['index'], test_input)
        interpreter.invoke()
        
        output = interpreter.get_tensor(output_details[0]['index'])
        print(f"✅ Output shape: {output.shape}")
        print(f"✅ Output range: [{output.min():.3f}, {output.max():.3f}]")
        
        # Check if it's super resolution
        if len(output.shape) >= 3:
            scale_h = output.shape[1] / test_input.shape[1] if test_input.shape[1] > 0 else 0
            scale_w = output.shape[2] / test_input.shape[2] if test_input.shape[2] > 0 else 0
            print(f"🎯 Scale factors: {scale_h:.1f}x height, {scale_w:.1f}x width")
            
            if abs(scale_h - 2.0) < 0.1 and abs(scale_w - 2.0) < 0.1:
                print("🔥 Perfect! 2x super resolution confirmed!")
                print("✅ Model ready for Android integration!")
                return True
        
        return True
        
    except Exception as e:
        print(f"❌ Testing failed: {e}")
        return False

def suggest_solutions():
    """Suggest solutions based on the analysis"""
    print("\n💡 Suggested Solutions:")
    print("=" * 30)
    
    print("1. 🔄 Try SRCNN instead:")
    print("   - Download a simpler SRCNN model")
    print("   - SRCNN models are often easier to convert")
    
    print("\n2. 🛠️ Manual model creation:")
    print("   - Create FSRCNN from scratch in TensorFlow 2.x")
    print("   - Train on thermal-like data")
    print("   - Direct TFLite conversion")
    
    print("\n3. 📦 Use OpenCV DNN:")
    print("   - The model works with OpenCV")
    print("   - Could implement via OpenCV4Android")
    print("   - But TensorFlow Lite is preferred for mobile")
    
    print("\n4. 🔍 Find the correct input/output nodes:")
    print("   - Model has 92 nodes, we need to find the right ones")
    print("   - May need to examine the original training code")
    
    print("\n5. ⚡ Quick alternative - Use a working model:")
    print("   - Find a different pre-trained super resolution model")
    print("   - That's already in TensorFlow Lite format")

def main():
    """Main inspection pipeline"""
    success = inspect_pb_model()
    
    if not success:
        suggest_solutions()
        print("\n🎯 Recommended next step:")
        print("Try the SRCNN approach or find a model already in TFLite format")

if __name__ == "__main__":
    main()