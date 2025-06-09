# MINI2-IR Android App - Project Context

## Tech Stack

### Core Technologies
- **Platform**: Android (API 24+)
- **Primary Language**: Kotlin
- **Native Layer**: C++ (NDK)
- **Camera Interface**: USB Camera (UVC) + Thermal SDK
- **Video Processing**: libuvc, libyuv
- **USB Communication**: libusb

### Key Dependencies
- **Native Libraries**: 
  - libircmd (thermal camera control)
  - libiruvc (thermal UVC interface)
  - libircam (thermal image processing)
  - libusb (USB communication)
  - libuvc (UVC video streaming)
  - libyuv (video format conversion)
- **Android Components**: 
  - Camera2 API (fallback)
  - USB Host API
  - NDK/JNI bridge
- **UI Framework**: Android Views (Material Design)

## Current Capabilities

### Working Features ✅
- **Real-time UVC video streaming** from thermal cameras
- **USB device detection and permission handling**
- **Thermal camera parameter control** (brightness, contrast, palette, scene mode)
- **Function registry system** with 18+ camera functions
- **FFC (Flat Field Correction)** manual triggering
- **Multi-device configuration** for different MINI2 variants
- **Fullscreen camera view** with orientation handling
- **Device configuration management** (add/remove devices)

### Core Architecture
- **Handle-based SDK integration** with proper RAII pattern
- **Registry-based function dispatch** for scalable camera control
- **Dual API support** (legacy + modern registry interface)
- **Thread-safe operations** with coroutine integration
- **Comprehensive error handling** with user-friendly messages

## Key Lessons / Pitfalls

### Critical Issues to Avoid
1. **SDK GET Function Crashes** ⚠️
   - **Problem**: `basic_current_brightness_level_get()` causes SIGSEGV
   - **Root Cause**: SDK initialization sequence incompatibility
   - **Solution**: Use cached values from SET operations, never call GET functions
   - **Lesson**: Always test SDK functions in isolation before UI integration

2. **Handle Lifecycle Management** ⚠️
   - **Problem**: Improper handle cleanup causes crashes
   - **Solution**: Strict RAII pattern with proper mutex protection
   - **Lesson**: Never access handles after cleanup, always check validity

3. **USB Permission Flow** ⚠️
   - **Problem**: Complex Android USB permission system
   - **Solution**: Comprehensive permission state machine
   - **Lesson**: Handle all permission states explicitly, provide clear user feedback

4. **Memory Layout Sensitivity** ⚠️
   - **Problem**: SDK structures require exact memory layout
   - **Solution**: Custom MySdk_* structures with precise byte alignment
   - **Lesson**: Document all struct layouts, never modify without verification

### Performance Gotchas
- **UI Thread Blocking**: Never call native SDK functions on main thread
- **Excessive Logging**: Disable verbose logging in production builds
- **Handle Reuse**: Avoid creating/destroying handles frequently
- **USB Bandwidth**: Limit concurrent USB operations to prevent timeouts

## Style Preferences

### Error Handling Approach
- **User-Facing Errors**: Always provide clear, actionable error messages
- **Technical Errors**: Log detailed error information for debugging
- **Graceful Degradation**: Continue operation when non-critical features fail
- **Retry Logic**: Implement exponential backoff for transient failures
- **Error Categories**: Distinguish between user errors, system errors, and bugs

### UI Aesthetic
- **Design System**: Material Design 3 principles
- **Visual Style**: Clean, minimal, professional
- **Color Scheme**: Dark theme optimized for low-light thermal imaging
- **Controls**: Intuitive sliders and toggles, clear labeling
- **Feedback**: Immediate visual feedback for all user actions
- **Layout**: Responsive design supporting various screen sizes

### Programming Practices

#### Architecture Patterns
- **Thin Activities**: Activities only handle UI lifecycle and navigation
- **Separate ViewModels**: Business logic in ViewModels, not Activities
- **Repository Pattern**: Data access through dedicated repository classes
- **Dependency Injection**: Manual DI for simplicity, avoid complex frameworks

#### Code Organization
- **Package Structure**: Group by feature, not by type
- **Naming Conventions**: 
  - Classes: PascalCase
  - Functions: camelCase
  - Constants: UPPER_SNAKE_CASE
  - Files: snake_case for C++, PascalCase for Kotlin
- **Documentation**: KDoc for public APIs, inline comments for complex logic

#### Native Development
- **C++ Style**: Modern C++17, RAII patterns, smart pointers where applicable
- **JNI Best Practices**: Minimal JNI surface, batch operations when possible
- **Memory Management**: Explicit cleanup, avoid memory leaks
- **Error Propagation**: Consistent error code system between native and Kotlin

#### Testing Strategy
- **Unit Tests**: Critical business logic and utility functions
- **Integration Tests**: Native-Kotlin bridge functionality
- **Device Tests**: USB and camera functionality on real hardware
- **Manual Testing**: User scenarios and edge cases

## Development Environment

### Required Tools
- **Android Studio**: Arctic Fox or later
- **NDK**: 23.0+ with CMake 3.18+
- **Gradle**: 8.0+
- **Test Device**: Android device with USB Host support

### Build Configuration
- **Minimum SDK**: API 24 (Android 7.0)
- **Target SDK**: Latest stable
- **Architectures**: arm64-v8a, armeabi-v7a, x86_64
- **Build Types**: Debug (with logging), Release (optimized)

### Key Directories
```
app/src/main/
├── cpp/                 # Native C++ code
├── java/.../           # Kotlin source code
├── jniLibs/            # Precompiled native libraries
└── res/                # Android resources
```

## Project Constraints

### Hardware Dependencies
- **USB Host Mode**: Requires Android device with USB OTG support
- **Camera Compatibility**: MINI2-IR thermal cameras (384, 256, 640 variants)
- **Performance**: Smooth video streaming requires mid-range+ device

### Software Limitations
- **SDK Dependencies**: Proprietary thermal camera libraries
- **Android Versions**: USB permission behavior varies across Android versions
- **Device Fragmentation**: Different USB implementations across manufacturers

### Known Issues
- **GET Function Crashes**: SDK read operations cause crashes
- **USB Timing**: Occasional timeout issues with rapid commands
- **Memory Constraints**: Large video buffers on memory-limited devices