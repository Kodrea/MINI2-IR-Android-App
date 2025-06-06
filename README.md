# MINI2-IR Android App

An Android application for interfacing with MINI2 thermal cameras, supporting various models including MINI2-384, MINI2-256, and MINI2-640.

## Features

- USB camera connection and management
- Real-time thermal image display
- Multiple palette options (White Hot, Sepia, Ironbow, etc.)
- Scene mode selection
- Brightness and contrast adjustment
- FFC (Flat Field Correction) control
- Support for different MINI2 camera models

## Requirements

- Android Studio Hedgehog | 2023.1.1 or newer
- Android SDK 35 or higher
- Minimum Android version: API 26 (Android 8.0)
- USB Host support on your Android device
- C++ development tools (NDK) for native code compilation

## Setup Instructions

1. Clone the repository:
```bash
git clone https://github.com/Kodrea/MINI2-Android-App.git
```

2. Open Android Studio and select "Open an Existing Project"

3. Navigate to the cloned directory and click "OK"

4. Let Android Studio sync the project and download dependencies

5. Connect your Android device with USB debugging enabled

6. Build and run the project

## Build Configuration

The project uses Gradle with Kotlin DSL for build configuration. Key configurations:

- Target SDK: 35
- Minimum SDK: 26
- NDK support enabled for native code
- C++ STL: c++_shared
- CMake version: 3.22.1

## Project Structure

- `/app/src/main/java/` - Kotlin source files
- `/app/src/main/cpp/` - Native C++ code
- `/app/src/main/res/` - Resource files
- `/app/src/main/AndroidManifest.xml` - App manifest

## Dependencies

- AndroidX Core KTX
- AndroidX AppCompat
- Material Design Components
- ConstraintLayout
- RecyclerView
- Fragment KTX
- Activity KTX

## USB Permissions

The app requires USB Host permissions to communicate with the thermal camera. Make sure your Android device supports USB Host mode and grant the necessary permissions when prompted.

## Contact

Cody - codysthermolab@gmail.com
Project Link: https://github.com/Kodrea/https://github.com/Kodrea/MINI2-Android-App.git 