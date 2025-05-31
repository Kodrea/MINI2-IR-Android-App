cmake_minimum_required(VERSION 3.22.1)

project(libusb C)

# Set Android-specific options
set(CMAKE_SYSTEM_NAME Android)
set(CMAKE_ANDROID_ARCH_ABI ${ANDROID_ABI})
set(CMAKE_ANDROID_NDK ${ANDROID_NDK})
set(CMAKE_ANDROID_STL_TYPE c++_shared)

# Disable building examples and tests
option(ENABLE_EXAMPLES OFF)
option(ENABLE_TESTS OFF)

# Add source files with correct paths
set(SOURCES
    libusb/core.c
    libusb/descriptor.c
    libusb/hotplug.c
    libusb/io.c
    libusb/sync.c
    libusb/strerror.c
    libusb/os/linux_usbfs.c
    libusb/os/linux_netlink.c
    libusb/os/threads_posix.c
    libusb/os/events_posix.c
)

# Create the library
add_library(usb SHARED ${SOURCES})

# Set include directories
target_include_directories(usb
    PUBLIC
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/libusb>
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/libusb/os>
        $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/android>
        $<INSTALL_INTERFACE:include>
)

# Set compile definitions for Android
target_compile_definitions(usb
    PRIVATE
        PLATFORM_LINUX
        THREADS_POSIX
        HAVE_CONFIG_H
        _GNU_SOURCE
        _REENTRANT
)

# --- Add target_link_libraries for log ---
find_library(log-lib log)
target_link_libraries(usb PRIVATE ${log-lib})
# -----------------------------------------

# Set properties for the library
set_target_properties(usb PROPERTIES
    VERSION 1.0.0
    SOVERSION 1
    PUBLIC_HEADER "libusb/libusb.h"
)

# Create an alias target for easier linking
add_library(LibUSB::LibUSB ALIAS usb)

# Export the usb target
install(TARGETS usb
    EXPORT LibUSBTargets
    LIBRARY DESTINATION lib/${ANDROID_ABI}
    ARCHIVE DESTINATION lib/${ANDROID_ABI}
    PUBLIC_HEADER DESTINATION include
)

install(EXPORT LibUSBTargets
    FILE LibUSBTargets.cmake
    NAMESPACE LibUSB::
    DESTINATION lib/cmake/LibUSB
) 