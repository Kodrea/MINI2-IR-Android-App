# Step 1: LiteRT Documentation Research Findings

## 🔍 Research Summary (January 8, 2025)

### **Official Status: TensorFlow Lite → LiteRT Migration**

✅ **Confirmed**: Google has officially rebranded TensorFlow Lite to **LiteRT** (Lite Runtime)  
✅ **Timeline**: Migration occurred in late 2024  
✅ **Backward Compatibility**: Existing TensorFlow Lite apps continue to work  
✅ **Future Development**: All new features only in LiteRT packages  

## 📦 Current Dependencies (2025)

### **Primary LiteRT Dependencies** ⭐ RECOMMENDED
```kotlin
// build.gradle.kts (app level)
dependencies {
    implementation("com.google.ai.edge.litert:litert:1.0.1")
    implementation("com.google.ai.edge.litert:litert-gpu:1.0.1")          // GPU acceleration
    implementation("com.google.ai.edge.litert:litert-support:1.0.1")     // Helper utilities
    implementation("com.google.ai.edge.litert:litert-metadata:1.0.1")    // Model metadata
}
```

### **Google Play Services Alternative** (Smaller app size)
```kotlin
dependencies {
    implementation("com.google.android.gms:play-services-tflite-java:16.4.0")
    implementation("com.google.android.gms:play-services-tflite-support:16.4.0")
}
```

### **Legacy TensorFlow Lite** (Still works, but no new features)
```kotlin
dependencies {
    implementation("org.tensorflow:tensorflow-lite:2.15.0")
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
}
```

## 🔄 Migration Requirements

### **Code Changes Required**
✅ **Minimal**: Same method names as TensorFlow Lite APIs  
✅ **Package Updates**: Change import statements to new packages  
✅ **Dependency Updates**: Replace `org.tensorflow` with `com.google.ai.edge.litert`  

### **Import Statement Changes**
```kotlin
// OLD (TensorFlow Lite)
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate

// NEW (LiteRT)
import com.google.ai.edge.litert.Interpreter
import com.google.ai.edge.litert.gpu.GpuDelegate
```

## 🚀 New Features in LiteRT (2025)

### **LiteRT Next** (Alpha)
- **New API Design**: Improved hardware acceleration
- **Better Performance**: Optimized for modern mobile chips
- **Simplified Usage**: Easier integration patterns
- **NPU Support**: Experimental Neural Processing Unit acceleration

### **Hardware Acceleration Options**
1. **GPU Delegate**: Standard GPU acceleration
2. **C++ Native API**: Maximum performance
3. **NPU Acceleration**: Experimental (Pixel devices)

## 📱 Android Integration Best Practices

### **Recommended Approach for 2025**
1. **Use LiteRT** (not legacy TensorFlow Lite)
2. **Google Play Services Runtime** (preferred for app size)
3. **GPU Delegate** for performance
4. **Metadata-enabled models** for easier I/O

### **Build Requirements**
- **Android Gradle Plugin**: 8.2.0 or higher
- **Core Library Desugaring**: Must be enabled
- **Minimum SDK**: API 21+ (Android 5.0)

### **File Organization**
```
app/src/main/
├── assets/models/          # Store .tflite model files
│   └── super_resolution.tflite
├── java/.../              # Kotlin/Java source
│   └── ml/                 # AI/ML related classes
└── res/                    # Resources
```

## ⚠️ Important Considerations

### **Deprecation Status**
- **TensorFlow Lite**: Not deprecated, but no new features
- **LiteRT**: All active development moving here
- **Migration Urgency**: Not immediate, but recommended for new projects

### **Version Compatibility**
- **Current Stable**: LiteRT 1.0.1
- **Play Services**: 16.4.0
- **Update Frequency**: Regular updates via Google Play Services

### **Performance Notes**
- **GPU Delegate**: 2-10x speedup on mobile GPUs
- **Model Size**: LiteRT models typically 1-50MB
- **Memory Usage**: Efficient on-device inference
- **Pixel 7 Optimization**: Tensor G2 chip provides excellent LiteRT performance

## 🎯 Recommendation for Our Project

### **Chosen Approach**
✅ **Use LiteRT** with Google Play Services runtime  
✅ **Enable GPU acceleration** for super resolution processing  
✅ **Start with stable APIs** (not LiteRT Next Alpha)  

### **Dependencies for Our Super Resolution Project**
```kotlin
// Recommended for MINI2-IR thermal camera super resolution
dependencies {
    implementation("com.google.android.gms:play-services-tflite-java:16.4.0")
    implementation("com.google.android.gms:play-services-tflite-support:16.4.0")
    
    // Alternative: Direct LiteRT (if Play Services not preferred)
    // implementation("com.google.ai.edge.litert:litert:1.0.1")
    // implementation("com.google.ai.edge.litert:litert-gpu:1.0.1")
}
```

### **Repository Configuration**
```kotlin
// build.gradle.kts (project level)
allprojects {
    repositories {
        google()
        mavenCentral()
        // For nightly builds if needed:
        // maven("https://oss.sonatype.org/content/repositories/snapshots")
    }
}
```

## ✅ Step 1 Verification Checklist

- [x] **Official Name**: LiteRT (formerly TensorFlow Lite)
- [x] **Current Dependencies**: `com.google.ai.edge.litert:litert:1.0.1`
- [x] **Migration Required**: Minimal code changes, mainly imports
- [x] **Breaking Changes**: None for basic usage
- [x] **Recommended Approach**: Google Play Services runtime + GPU delegate
- [x] **Version Numbers**: LiteRT 1.0.1, Play Services 16.4.0

## 📋 Next Steps

1. **Add dependencies** to our project
2. **Test basic model loading** with a simple .tflite file
3. **Verify GPU delegate** works on your Pixel 7
4. **Create minimal test activity** for model inference

---
**Research Date**: January 8, 2025  
**Sources**: ai.google.dev/edge/litert, official migration guide  
**Status**: ✅ Complete - Ready for Step 2 (Model Research)  
**Confidence**: High - Based on official Google documentation