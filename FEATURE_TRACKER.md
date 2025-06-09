# MINI2-IR Android App - Feature Tracker

| Feature or File | Status | Priority | Notes | Last Updated |
|----------------|--------|----------|-------|--------------|
| **Core Infrastructure** | | | | |
| UVC Video Streaming | Done | Critical | Real-time thermal camera video via libuvc | 2025-01-08 |
| USB Permission System | Done | Critical | Device detection and permission handling | 2025-01-08 |
| SDK Integration (libircmd) | Done | Critical | Thermal camera control functions | 2025-01-08 |
| JNI Bridge (native-lib.cpp) | Done | Critical | Kotlin-C++ interface | 2025-01-08 |
| Handle Management | Done | Critical | RAII pattern with proper cleanup | 2025-01-08 |
| **Camera Control** | | | | |
| Function Registry System | Done | High | 18+ functions with modern dispatch | 2025-01-08 |
| Basic Image Controls | Done | High | Brightness, contrast, palette, scene mode | 2025-01-08 |
| FFC (Flat Field Correction) | Done | High | Manual FFC triggering | 2025-01-08 |
| Noise Reduction Controls | Done | Medium | Temporal, spatial, general noise reduction | 2025-01-08 |
| Advanced Image Controls | Done | Medium | Edge enhancement, ROI, AGC | 2025-01-08 |
| GET Function Support | Disabled | Low | SDK crashes on read operations - using cached values | 2025-01-08 |
| **User Interface** | | | | |
| Camera Activity | Done | Critical | Main camera interface with controls | 2025-01-08 |
| Device Configuration UI | Done | High | Add/manage multiple device configs | 2025-01-08 |
| Fullscreen Mode | Done | Medium | Immersive camera viewing | 2025-01-08 |
| Control Panel UI | Done | Medium | Expandable groups for camera parameters | 2025-01-08 |
| Error Handling UI | Done | Medium | User-friendly error messages | 2025-01-08 |
| **Device Support** | | | | |
| MINI2-384 Support | Done | Critical | 384x288 thermal camera | 2025-01-08 |
| MINI2-256 Support | Done | Critical | 256x192 thermal camera | 2025-01-08 |
| MINI2-640 Support | Done | Critical | 640x480 thermal camera | 2025-01-08 |
| Multi-Device Management | Done | High | Support for multiple camera configurations | 2025-01-08 |
| **Recording & Capture** | | | | |
| Video Recording | Done | High | High-performance H.264 recording at native framerates (25/50fps) | 2025-01-09 |
| Direct Recording Pipeline | Done | High | Native YUYV→YUV420 conversion bypassing display bottleneck | 2025-01-09 |
| Image Capture | Planned | High | Save thermal snapshots | - |
| TFLite Super Resolution | Planned | High | AI-powered 2x/4x upscaling for 256x192 thermal images | 2025-01-08 |
| Temperature Measurement | Planned | Medium | Point and area temperature readings | - |
| Advanced Analytics | Planned | Medium | Histogram, thermal profiling | - |
| Cloud Integration | Planned | Low | Optional cloud storage for thermal data | - |
| **Technical Debt** | | | | |
| GET Function Investigation | TODO | Medium | Resolve SDK read operation crashes | - |
| Performance Optimization | TODO | Medium | Reduce memory usage and improve responsiveness | - |
| Test Coverage | TODO | Medium | Comprehensive unit and integration tests | - |
| Documentation | TODO | Low | API documentation and user guides | - |
| **Development Tools** | | | | |
| Build System (CMake) | Done | Critical | C++ native library compilation | 2025-01-08 |
| Gradle Configuration | Done | Critical | Android app build and dependencies | 2025-01-08 |
| Debug Logging System | Done | High | Comprehensive logging for troubleshooting | 2025-01-08 |
| Test Infrastructure | TODO | Medium | Unit test framework setup | - |

## Status Definitions
- **Done**: Feature is complete and working in production
- **WIP**: Currently being developed
- **TODO**: Planned for development but not started
- **Planned**: Identified for future development
- **Disabled**: Implemented but disabled due to issues
- **Blocked**: Cannot proceed due to external dependencies

## Priority Levels
- **Critical**: Core functionality, app won't work without it
- **High**: Important features that significantly impact user experience
- **Medium**: Nice-to-have features that improve usability
- **Low**: Future enhancements, not immediately necessary

## Recent Changes
- **2025-01-09**: Video recording implementation completed
  - High-performance direct recording pipeline bypassing TextureView bottleneck
  - Native YUYV→YUV420 conversion achieving target 25/50fps framerates
  - MediaCodec H.264 encoding with proper timing and format keys
- **2025-01-08**: Initial feature tracker created based on current codebase analysis
  - All core thermal camera functionality marked as Done
  - GET function support disabled due to SDK crashes
  - Identified planned features for future development