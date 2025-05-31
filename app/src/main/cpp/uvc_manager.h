#pragma once

#include <android/log.h>
#include <android/native_window.h>
#include <libuvc/libuvc.h>
#include <libusb.h> // For libusb_context
#include <memory>
#include <mutex>
#include <thread>  // Added for std::thread
#include <atomic>  // Added for std::atomic

// Logging macros
#define LOG_TAG "UVCCamera"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

class UVCCamera {
public:
    UVCCamera();
    ~UVCCamera();

    // Initialize UVC context using a file descriptor
    bool init(int fileDescriptor);
    
    // Start streaming from the camera
    bool startStream(int width, int height, int fps, ANativeWindow* window);
    
    // Stop streaming
    void stopStream();
    
    // Clean up resources
    void cleanup();

    // Get the UVC device handle
    uvc_device_handle_t* getDeviceHandle() const { return devh_; }

private:
    // This function is deprecated in favor of init(int fileDescriptor)
    bool findAndOpenDevice();

    // Close the device - internal helper
    void closeDevice();

    // Frame callback for UVC streaming
    static void frameCallback(uvc_frame_t* frame, void* ptr);

    // USB event handling
    void usbEventThreadLoop(); // New method for the event thread

    // UVC context and device handles
    uvc_context_t* ctx_;
    uvc_device_t* dev_;
    uvc_device_handle_t* devh_;
    uvc_stream_ctrl_t ctrl_;
    libusb_context *usb_ctx_;
    
    // Streaming state
    bool is_streaming_;
    ANativeWindow* window_;
    std::mutex mutex_;

    // USB event thread
    std::thread usb_event_thread_;
    std::atomic<bool> keep_usb_event_thread_running_;
}; 