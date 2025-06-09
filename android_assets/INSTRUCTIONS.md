# Android Integration Instructions

## Quick Setup:
1. Copy the entire 'models' folder to: `app/src/main/assets/`
2. The model will be loaded as: `models/fsrcnn_x2.tflite`
3. Build and run your Android app
4. Press "🔥 Test Super Resolution" button

## Expected Results:
- Model loads successfully
- Processes 256x192 → 512x384 (or similar scaling)
- Processing time: 15-30ms on Pixel 7
- Visual enhancement visible in side-by-side comparison

## If you see errors:
- Check logcat for detailed error messages
- Verify TensorFlow Lite dependencies are added
- Ensure model file is in correct assets location

## Model Details:
- Input: Flexible size (optimized for 256x192)
- Output: 2x super resolution
- Format: Grayscale thermal images
- Type: Float32 normalized [0,1]
