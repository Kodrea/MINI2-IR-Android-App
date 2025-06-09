#pragma once

#include <android/log.h>
#include <android/native_window.h>
#include <libuvc/libuvc.h>
#include <libusb.h> // For libusb_context and interface descriptor
#include <memory>
#include <mutex>
#include <thread>  // Added for std::thread
#include <atomic>  // Added for std::atomic
#include <vector>  // Added for captured frame storage

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

    // New methods for device enumeration
    bool enumerateInterfaces();
    bool enumerateFormats();
    void printDeviceInfo();
    
    // Raw frame capture for super resolution
    void setCaptureNextFrame(bool capture) { capture_next_frame_ = capture; }
    bool getCapturedFrameData(uint8_t* buffer, int* width, int* height);
    bool hasNewCapturedFrame() const { return has_captured_frame_; }
    
    // UVC framerate control
    std::vector<int> getSupportedFrameRates(int width, int height);
    bool setFrameRate(int width, int height, int fps);
    int getCurrentFrameRate();
    void enumerateAllFrameRates();
    
    // Direct video recording support
    void setVideoRecordingEnabled(bool enabled) { 
        video_recording_enabled_ = enabled; 
        if (!enabled) {
            video_recording_start_time_ = 0;  // Reset timing on stop
        }
    }
    void setVideoEncoderCallback(void (*callback)(uint8_t* yuvData, int width, int height, int64_t timestampUs, void* userPtr), void* userPtr);
    bool isVideoRecordingEnabled() const { return video_recording_enabled_; }

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
    
    // Raw frame capture members
    std::atomic<bool> capture_next_frame_;
    std::atomic<bool> has_captured_frame_;
    std::vector<uint8_t> captured_frame_data_;
    int captured_frame_width_;
    int captured_frame_height_;
    std::mutex capture_mutex_;
    
    // Direct video recording members
    std::atomic<bool> video_recording_enabled_;
    void (*video_encoder_callback_)(uint8_t* yuvData, int width, int height, int64_t timestampUs, void* userPtr);
    void* video_callback_user_ptr_;
    int64_t video_recording_start_time_;

    // Updated to use libusb_interface_descriptor instead of uvc_interface_descriptor_t
    void printInterfaceInfo(const libusb_interface_descriptor* if_desc);
    void printFormatInfo(const uvc_format_desc_t* format_desc);
    void printFrameInfo(const uvc_frame_desc_t* frame_desc);
    
    // YUV conversion functions for direct recording
    void convertYUYVToYUV420(const uint8_t* yuyv_data, uint8_t* yuv420_data, int width, int height);
}; 