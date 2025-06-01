# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html.
# For more examples on how to use CMake, see https://github.com/android/ndk-samples.

# Sets the minimum CMake version required for this project.
cmake_minimum_required(VERSION 3.22.1)

# Declares the project name. The project name can be accessed via ${ PROJECT_NAME},
# Since this is the top level CMakeLists.txt, the project name is also accessible
# with ${CMAKE_PROJECT_NAME} (both CMake variables are in-sync within the top level
# build script scope).
project("ircmd_handle")

# Disable JPEG support for libuvc since we don't need it
set(WITH_JPEG OFF CACHE BOOL "Build libuvc with JPEG support" FORCE)

# Disable installation for libyuv since we're using it as a subdirectory
set(INSTALL_LIBYUV OFF CACHE BOOL "Install libyuv" FORCE)

# Add libyuv as a subdirectory
add_subdirectory(third_party/libyuv)

# Add libusb as a subdirectory (must be before libuvc)
add_subdirectory(third_party/libusb)

# Add libuvc as a subdirectory
add_subdirectory(third_party/libuvc/libuvc-master)

# Find required Android libraries
find_library(log-lib log)
find_library(android-lib android)

# Creates and names a library, sets it as either STATIC
# or SHARED, and provides the relative paths to its source code.
# You can define multiple libraries, and CMake builds them for you.
# Gradle automatically packages shared libraries with your APK.
#
# In this top level CMakeLists.txt, ${CMAKE_PROJECT_NAME} is used to define
# the target library name; in the sub-module's CMakeLists.txt, ${PROJECT_NAME}
# is preferred for the same purpose.
#
# In order to load a library into your app from Java/Kotlin, you must call
# System.loadLibrary() and pass the name of the library defined here;
# for GameActivity/NativeActivity derived applications, the same library name must be
# used in the AndroidManifest.xml file.
add_library(${CMAKE_PROJECT_NAME} SHARED
        # List C/C++ source files with relative paths to this CMakeLists.txt.
        native-lib.cpp
        uvc_manager.cpp
        ircmd_manager.cpp)

# Add SDK libraries directory
set(SDK_LIBS_DIR ${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/${ANDROID_ABI})

# Add SDK libraries
add_library(ircmd SHARED IMPORTED)
set_target_properties(ircmd PROPERTIES IMPORTED_LOCATION
    ${SDK_LIBS_DIR}/libircmd.so)

add_library(iruvc SHARED IMPORTED)
set_target_properties(iruvc PROPERTIES IMPORTED_LOCATION
    ${SDK_LIBS_DIR}/libiruvc.so)

# Add include directories for libuvc and libusb
target_include_directories(${CMAKE_PROJECT_NAME} PRIVATE
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/libuvc/libuvc-master/include
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/libusb
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/libusb/libusb
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/libusb/libusb/os
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/libusb/android
        ${CMAKE_CURRENT_SOURCE_DIR}/third_party/libyuv/include
        ${CMAKE_CURRENT_SOURCE_DIR}/Include)

# Specifies libraries CMake should link to your target library. You
# can link libraries from various origins, such as libraries defined in this
# build script, prebuilt third-party libraries, or Android system libraries.
target_link_libraries(${CMAKE_PROJECT_NAME}
        # List libraries link to the target library
        android
        log
        nativewindow  # Add Android native window library
        LibUSB::LibUSB  # Link against libusb
        LibUVC::UVC
        ircmd
        iruvc
        yuv)