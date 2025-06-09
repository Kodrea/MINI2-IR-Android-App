#include "uvc_manager.h"
#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <libusb.h>
#include <libyuv.h>
#include <cstring>   // For memcpy
#include <chrono>    // For video recording timestamps

// Helper function for color conversion
inline int clamp(int value, int min, int max) {
    return value < min ? min : (value > max ? max : value);
}

extern "C" uvc_error_t uvc_wrap(int sys_dev, uvc_context_t *context, uvc_device_handle_t **devh);

// Global camera instance
static std::unique_ptr<UVCCamera> g_camera;

// JNI functions
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_androidlibuvc_UVCManager_nativeInit(JNIEnv* env, jobject thiz, jint fileDescriptor) {
    if (!g_camera) {
        g_camera = std::make_unique<UVCCamera>();
    }
    return g_camera->init(fileDescriptor);
}

JNIEXPORT jboolean JNICALL
Java_com_example_androidlibuvc_UVCManager_nativeStartCamera(
        JNIEnv* env, jobject thiz, jint width, jint height, jint fps, jobject surface) {
    if (!g_camera) {
        LOGE("Camera not initialized");
        return JNI_FALSE;
    }

    // Get the native window from the surface
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        LOGE("Failed to get native window from surface");
        return JNI_FALSE;
    }

    bool result = g_camera->startStream(width, height, fps, window);
    ANativeWindow_release(window);
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_androidlibuvc_UVCManager_nativeStopCamera(JNIEnv* env, jobject thiz) {
    if (g_camera) {
        g_camera->stopStream();
    }
}

JNIEXPORT void JNICALL
Java_com_example_androidlibuvc_UVCManager_nativeCleanup(JNIEnv* env, jobject thiz) {
    if (g_camera) {
        g_camera->cleanup();
        g_camera.reset();
    }
}

} // extern "C"

// UVCCamera implementation
UVCCamera::UVCCamera()
    : ctx_(nullptr), dev_(nullptr), devh_(nullptr), usb_ctx_(nullptr),
      is_streaming_(false), window_(nullptr), keep_usb_event_thread_running_(false),
      capture_next_frame_(false), has_captured_frame_(false),
      captured_frame_width_(0), captured_frame_height_(0),
      video_recording_enabled_(false), video_encoder_callback_(nullptr), 
      video_callback_user_ptr_(nullptr), video_recording_start_time_(0) {
}

UVCCamera::~UVCCamera() {
    cleanup();
}

bool UVCCamera::init(int fileDescriptor) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    if (ctx_) {
        LOGI("UVC already initialized");
        return true;
    }

    LOGI("Setting libusb global option NO_DEVICE_DISCOVERY");
    // Set option globally before initializing any specific context, as per the guide
    int res_option = libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, NULL);
    if (res_option != LIBUSB_SUCCESS) {
        // This might not be critical on all platforms or libusb versions if init still works as expected
        LOGW("Failed to set libusb global option NO_DEVICE_DISCOVERY: %s. Continuing...", libusb_error_name(res_option));
    }

    LOGI("Initializing libusb context");
    int res_libusb = libusb_init(&usb_ctx_);
    if (res_libusb != LIBUSB_SUCCESS) {
        LOGE("Failed to initialize libusb context: %s", libusb_error_name(res_libusb));
        usb_ctx_ = nullptr;
        return false;
    }

    LOGI("Starting USB event thread");
    keep_usb_event_thread_running_.store(true);
    try {
        usb_event_thread_ = std::thread(&UVCCamera::usbEventThreadLoop, this);
    } catch (const std::system_error& e) {
        LOGE("Failed to create USB event thread: %s", e.what());
        libusb_exit(usb_ctx_);
        usb_ctx_ = nullptr;
        keep_usb_event_thread_running_.store(false);
        return false;
    }

    // Note: Do not call libusb_set_option(usb_ctx_, ...) for NO_DEVICE_DISCOVERY here again,
    // as it was set globally above.

    LOGI("Initializing UVC context with provided libusb context");
    uvc_error_t res_uvc = uvc_init(&ctx_, usb_ctx_);
    if (res_uvc != UVC_SUCCESS) {
        LOGE("Failed to initialize UVC context: %s", uvc_strerror(res_uvc));
        libusb_exit(usb_ctx_);
        usb_ctx_ = nullptr;
        ctx_ = nullptr; // Ensure it's null if init failed
        return false;
    }

    // Wrap the file descriptor using uvc_wrap
    LOGI("Wrapping file descriptor %d", fileDescriptor);
    res_uvc = uvc_wrap(fileDescriptor, ctx_, &devh_);
    if (res_uvc != UVC_SUCCESS) {
        LOGE("Failed to wrap sys device with uvc_wrap: %s", uvc_strerror(res_uvc));
        uvc_exit(ctx_);
        libusb_exit(usb_ctx_);
        usb_ctx_ = nullptr;
        ctx_ = nullptr;
        devh_ = nullptr;
        return false;
    }
    LOGI("Device wrapped successfully");

    // Get the device from the handle
    dev_ = uvc_get_device(devh_);
    if (!dev_) {
        LOGE("Failed to get device from handle");
        uvc_close(devh_); // uvc_close will also call libusb_close on the underlying handle
        devh_ = nullptr;
        uvc_exit(ctx_); // This will not call libusb_exit on usb_ctx_ because own_usb_ctx is false
        libusb_exit(usb_ctx_);
        usb_ctx_ = nullptr;
        ctx_ = nullptr;
        return false;
    }

    // Print device information
    uvc_device_descriptor_t* dev_desc;
    res_uvc = uvc_get_device_descriptor(dev_, &dev_desc);
    if (res_uvc == UVC_SUCCESS) {
        LOGI("Device opened successfully:");
        LOGI("  Manufacturer: %s", dev_desc->manufacturer ? dev_desc->manufacturer : "Unknown");
        LOGI("  Product: %s", dev_desc->product ? dev_desc->product : "Unknown");
        LOGI("  Serial Number: %s", dev_desc->serialNumber ? dev_desc->serialNumber : "Unknown");
        LOGI("  Vendor ID: 0x%04x", dev_desc->idVendor);
        LOGI("  Product ID: 0x%04x", dev_desc->idProduct);
        LOGI("  UVC Version: %d.%d", (dev_desc->bcdUVC >> 8) & 0xFF, dev_desc->bcdUVC & 0xFF);
        uvc_free_device_descriptor(dev_desc);
    }

    // After successful device initialization, enumerate interfaces and formats
    LOGI("Enumerating device interfaces and formats:");
    printDeviceInfo();
    enumerateInterfaces();
    enumerateFormats();

    LOGI("UVC device initialized and configured successfully via FD wrapping");
    return true;
}

bool UVCCamera::findAndOpenDevice() {
    // This function is likely no longer needed if init() handles device opening via FD.
    // If called, it would conflict. For now, let's ensure it's not used or make it benign.
    LOGW("findAndOpenDevice called, but device should be opened via FD in init(). This path might be problematic.");
    if (devh_) { // Already opened via FD
        LOGI("Device already opened via init(fd). Skipping findAndOpenDevice.");
        return true;
    }
    // Original logic below is problematic on Android without root / direct USB FS access
    // ... (original findAndOpenDevice logic, which we are trying to avoid) ...
    LOGE("findAndOpenDevice: UVC device not available or context not initialized for discovery.");
    return false; 
}

void UVCCamera::closeDevice() {
    if (devh_) {
        uvc_close(devh_); // This should handle closing the libusb device handle obtained via wrap
        devh_ = nullptr;
    }
    if (dev_) { // uvc_device_t from uvc_get_device(devh_)
        // uvc_unref_device(dev_); // uvc_close -> uvc_free_devh -> uvc_unref_device(devh->dev)
        // So, unref here might be a double unref. Let uvc_close manage dev's ref count.
        dev_ = nullptr;
    }
}

bool UVCCamera::startStream(int width, int height, int fps, ANativeWindow* window) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    if (is_streaming_) {
        LOGI("Camera already streaming");
        return true;
    }

    if (!devh_) { // Check if device is open (should be done in init via FD)
        LOGE("Device not open. Cannot start stream.");
        return false;
    }
    
    LOGI("startStream: ANativeWindow pointer: %p", window);
    window_ = window; // Assign early to check in callback even if uvc_start_streaming fails

    LOGI("Attempting to get stream control for %dx%d @ %dfps, format YUYV", width, height, fps);
    
    // Try different formats in order of preference
    uvc_frame_format formats[] = {
        UVC_FRAME_FORMAT_YUYV,
        UVC_FRAME_FORMAT_UYVY,
        UVC_FRAME_FORMAT_MJPEG,
        UVC_FRAME_FORMAT_UNCOMPRESSED
    };
    
    const char* format_names[] = {"YUYV", "UYVY", "MJPEG", "UNCOMPRESSED"};
    
    uvc_error_t res = UVC_ERROR_NOT_FOUND;
    uvc_frame_format successful_format = UVC_FRAME_FORMAT_UNKNOWN;
    
    for (int i = 0; i < 4; i++) {
        LOGI("Trying format %s (%d)...", format_names[i], formats[i]);
        res = uvc_get_stream_ctrl_format_size(
            devh_, &ctrl_,
            formats[i],
            width, height, fps
        );
        
        if (res == UVC_SUCCESS) {
            successful_format = formats[i];
            LOGI("✅ Successfully negotiated format %s", format_names[i]);
            break;
        } else {
            LOGI("❌ Format %s failed: %s", format_names[i], uvc_strerror(res));
        }
    }

    if (res != UVC_SUCCESS) {
        LOGE("Failed to get stream control: %s (%d). Check if format/resolution/fps is supported.", uvc_strerror(res), res);
        window_ = nullptr; // Clear window if control negotiation failed
        return false;
    }
    LOGI("Stream control obtained successfully. Negotiated parameters:");
    LOGI("  Format: %s (%d)", format_names[successful_format == UVC_FRAME_FORMAT_YUYV ? 0 : 
                                       successful_format == UVC_FRAME_FORMAT_UYVY ? 1 :
                                       successful_format == UVC_FRAME_FORMAT_MJPEG ? 2 : 3], successful_format);
    LOGI("  bmHint: %u", ctrl_.bmHint);
    LOGI("  bFormatIndex: %u", ctrl_.bFormatIndex);
    LOGI("  bFrameIndex: %u", ctrl_.bFrameIndex);
    LOGI("  dwFrameInterval: %u (%f fps)", ctrl_.dwFrameInterval, 10000000.0 / ctrl_.dwFrameInterval);
    LOGI("  wKeyFrameRate: %u", ctrl_.wKeyFrameRate);
    LOGI("  wPFrameRate: %u", ctrl_.wPFrameRate);
    LOGI("  wCompQuality: %u", ctrl_.wCompQuality);
    LOGI("  wCompWindowSize: %u", ctrl_.wCompWindowSize);
    LOGI("  wDelay: %u", ctrl_.wDelay);
    LOGI("  dwMaxVideoFrameSize: %u", ctrl_.dwMaxVideoFrameSize);
    LOGI("  dwMaxPayloadTransferSize: %u", ctrl_.dwMaxPayloadTransferSize);
    LOGI("  bInterfaceNumber: %d", ctrl_.bInterfaceNumber);
    // uvc_print_stream_ctrl(&ctrl_, stderr); // Keep this as well, in case it starts working

    // Start streaming
    LOGI("Starting UVC streaming with window %p...", window_);
    res = uvc_start_streaming(devh_, &ctrl_, frameCallback, this, 0);
    if (res != UVC_SUCCESS) {
        LOGE("Failed to start streaming: %s (%d)", uvc_strerror(res), res);
        window_ = nullptr; // Clear window if streaming failed
        return false;
    }

    is_streaming_ = true;
    LOGI("Camera streaming started successfully.");
    return true;
}

void UVCCamera::stopStream() {
    std::lock_guard<std::mutex> lock(mutex_);
    
    if (!is_streaming_) {
        LOGI("Stream not active, no need to stop.");
        return;
    }

    if (devh_) {
        uvc_stop_streaming(devh_);
        LOGI("uvc_stop_streaming called.");
    } else {
        LOGW("stopStream called but devh_ is null.");
    }
    
    // We don't call closeDevice() here anymore as per typical UVC lifecycle.
    // closeDevice() and full cleanup should happen in UVCCamera::cleanup()
    // or when the device is truly disconnected/reinitialized.

    is_streaming_ = false;
    window_ = nullptr; // Release native window reference from our side
    LOGI("Camera streaming stopped logic completed in UVCCamera::stopStream.");
}

void UVCCamera::cleanup() {
    std::lock_guard<std::mutex> lock(mutex_);
    LOGI("UVCCamera::cleanup called");

    if (is_streaming_) {
        // Attempt to stop stream if still running
        LOGI("Stream was active, calling internal stopStream measures.");
        if (devh_) {
            uvc_stop_streaming(devh_);
            LOGI("uvc_stop_streaming called during cleanup.");
        }
        is_streaming_ = false;
        window_ = nullptr;
    }

    if (devh_) {
        LOGI("Closing UVC device handle (devh_)");
        uvc_close(devh_); // This closes the libusb handle and unrefs the uvc_device
        devh_ = nullptr;
        dev_ = nullptr; // dev_ is obtained from devh_, so it's invalid after uvc_close
    }
    
    if (dev_ && !devh_) {
        // This case should ideally not happen if devh_ is managed correctly.
        // If devh_ was null but dev_ wasn't, dev_ might be stale.
        // uvc_unref_device(dev_); // This was likely already handled by uvc_close if devh_ was valid
        LOGW("dev_ was not null in cleanup after devh_ was handled. Setting to null.");
        dev_ = nullptr;
    }

    // Stop and join USB event thread BEFORE exiting libusb context
    if (keep_usb_event_thread_running_.load()) {
        LOGI("Stopping USB event thread...");
        keep_usb_event_thread_running_.store(false);
        if (usb_event_thread_.joinable()) {
            try {
                 usb_event_thread_.join();
                 LOGI("USB event thread joined.");
            } catch (const std::system_error& e) {
                LOGE("Error joining USB event thread: %s", e.what());
            }
        } else {
            LOGW("USB event thread was not joinable.");
        }
    } else if (usb_event_thread_.joinable()) {
        // This case might happen if init failed after thread creation but before running flag was fully utilized
        LOGW("keep_usb_event_thread_running_ was false, but thread was joinable. Attempting join.");
        try {
            usb_event_thread_.join();
            LOGI("USB event thread (post-failure init) joined.");
        } catch (const std::system_error& e) {
            LOGE("Error joining USB event thread (post-failure init): %s", e.what());
        }
    }

    if (ctx_) {
        LOGI("Exiting UVC context (ctx_)");
        uvc_exit(ctx_); // This will NOT exit usb_ctx_ because own_usb_ctx is false
        ctx_ = nullptr;
    }

    if (usb_ctx_) {
        LOGI("Exiting libusb context (usb_ctx_)");
        libusb_exit(usb_ctx_);
        usb_ctx_ = nullptr;
    }
    LOGI("UVCCamera::cleanup finished");
}

// Frame callback needs to be a static member or a free function
void UVCCamera::frameCallback(uvc_frame_t* frame, void* ptr) {
    UVCCamera* camera = static_cast<UVCCamera*>(ptr);

    if (!camera || !camera->is_streaming_ || !camera->window_ || !frame) {
        if (!camera) LOGE("frameCallback: camera pointer is null!");
        else if (!camera->is_streaming_) LOGE("frameCallback: Not streaming");
        else if (!camera->window_) LOGE("frameCallback: window is null");
        else if (!frame) LOGE("frameCallback: frame is null");
        return;
    }


    // Verify frame format - support multiple formats
    const char* format_name = "UNKNOWN";
    switch (frame->frame_format) {
        case UVC_FRAME_FORMAT_YUYV:
            format_name = "YUYV";
            break;
        case UVC_FRAME_FORMAT_UYVY:
            format_name = "UYVY";
            break;
        case UVC_FRAME_FORMAT_MJPEG:
            format_name = "MJPEG";
            break;
        case UVC_FRAME_FORMAT_UNCOMPRESSED:
            format_name = "UNCOMPRESSED";
            break;
        default:
            LOGE("frameCallback: Unsupported frame format: %d", frame->frame_format);
            return;
    }
    
    // Log frame processing info periodically (every 100 frames) to avoid spam
    static int frame_count = 0;
    frame_count++;
    if (frame_count % 100 == 0) {
        LOGI("frameCallback: Processed %d %s frames %dx%d, %zu bytes", 
             frame_count, format_name, frame->width, frame->height, frame->data_bytes);
    }

    // Verify frame dimensions
    if (frame->width <= 0 || frame->height <= 0 || frame->data_bytes <= 0) {
        LOGE("frameCallback: Invalid frame dimensions: %dx%d, data_size=%zu",
             frame->width, frame->height, frame->data_bytes);
        return;
    }

    // Calculate expected data size based on format
    size_t expected_size;
    switch (frame->frame_format) {
        case UVC_FRAME_FORMAT_YUYV:
        case UVC_FRAME_FORMAT_UYVY:
            expected_size = frame->width * frame->height * 2; // 2 bytes per pixel
            break;
        case UVC_FRAME_FORMAT_MJPEG:
            // MJPEG is compressed, so size varies - just check minimum
            expected_size = frame->width * frame->height / 10; // Very conservative estimate
            break;
        case UVC_FRAME_FORMAT_UNCOMPRESSED:
            // Could be various formats, check based on step if available
            expected_size = frame->step ? frame->step * frame->height : frame->width * frame->height * 2;
            break;
        default:
            expected_size = frame->width * frame->height; // Minimum single channel
            break;
    }
    
    if (frame->data_bytes < expected_size) {
        LOGE("frameCallback: Frame data size mismatch: received %zu bytes, expected %zu bytes for %dx%d %s",
             frame->data_bytes, expected_size, frame->width, frame->height, format_name);
        LOGE("  Frame details: format=%d, step=%zu", frame->frame_format, frame->step);
        
        // For MJPEG, size variation is normal
        if (frame->frame_format == UVC_FRAME_FORMAT_MJPEG) {
            LOGW("  MJPEG frame size variation is normal, proceeding...");
        } else {
            // Try to work with what we have if it's at least the minimum viable size
            if (frame->data_bytes < (frame->width * frame->height)) {
                LOGE("  Data too small even for single channel, skipping frame");
                return;
            } else {
                LOGW("  Attempting to process frame with smaller than expected data size");
            }
        }
    }

    // 🎯 RAW FRAME CAPTURE FOR SUPER RESOLUTION
    if (camera->capture_next_frame_.load() && frame->width == 256 && frame->height == 192) {
        std::lock_guard<std::mutex> lock(camera->capture_mutex_);
        
        // Use actual frame size instead of calculated size for capture
        size_t capture_size = std::min(frame->data_bytes, static_cast<size_t>(frame->width * frame->height * 2));
        camera->captured_frame_data_.resize(capture_size);
        std::memcpy(camera->captured_frame_data_.data(), frame->data, capture_size);
        
        camera->captured_frame_width_ = frame->width;
        camera->captured_frame_height_ = frame->height;
        camera->has_captured_frame_.store(true);
        camera->capture_next_frame_.store(false);
        
        LOGI("🎯 Captured raw thermal frame: %dx%d, %zu bytes (actual: %zu)", 
             frame->width, frame->height, capture_size, frame->data_bytes);
    }

    // 🎥 DIRECT VIDEO RECORDING
    if (camera->video_recording_enabled_.load() && camera->video_encoder_callback_ != nullptr) {
        if (frame->frame_format == UVC_FRAME_FORMAT_YUYV) {
            // Calculate YUV420 buffer size (1.5 bytes per pixel)
            const int yuv420_size = frame->width * frame->height * 3 / 2;
            
            // Allocate temporary buffer for YUV420 conversion
            uint8_t* yuv420_buffer = new uint8_t[yuv420_size];
            
            // Convert YUYV to YUV420 directly
            camera->convertYUYVToYUV420(
                static_cast<const uint8_t*>(frame->data),
                yuv420_buffer,
                frame->width,
                frame->height
            );
            
            // Calculate timestamp relative to recording start
            auto now = std::chrono::duration_cast<std::chrono::microseconds>(
                std::chrono::steady_clock::now().time_since_epoch()
            ).count();
            
            // Initialize recording start time on first frame
            if (camera->video_recording_start_time_ == 0) {
                camera->video_recording_start_time_ = now;
            }
            
            int64_t timestampUs = now - camera->video_recording_start_time_;
            
            // Call the video encoder callback with converted YUV420 data
            camera->video_encoder_callback_(
                yuv420_buffer,
                frame->width,
                frame->height,
                timestampUs,
                camera->video_callback_user_ptr_
            );
            
            // Clean up temporary buffer
            delete[] yuv420_buffer;
        }
    }

    ANativeWindow_Buffer buffer;
    // For little-endian systems (like Android), RGBA_8888 is actually stored as ABGR in memory
    int set_geom_ret = ANativeWindow_setBuffersGeometry(camera->window_, frame->width, frame->height, WINDOW_FORMAT_RGBA_8888);
    if (set_geom_ret != 0) {
        LOGE("frameCallback: Failed to set buffers geometry: %d", set_geom_ret);
        return;
    }

    int lock_ret = ANativeWindow_lock(camera->window_, &buffer, nullptr);
    if (lock_ret != 0) {
        LOGE("frameCallback: Failed to lock native window: %d", lock_ret);
        return;
    }


    // Verify buffer dimensions
    if (buffer.width < frame->width || buffer.height < frame->height) {
        LOGE("frameCallback: Buffer too small: %dx%d < %dx%d",
             buffer.width, buffer.height, frame->width, frame->height);
        ANativeWindow_unlockAndPost(camera->window_);
        return;
    }

    // Calculate expected stride for RGBA (4 bytes per pixel)
    size_t expected_stride = frame->width * 4;
    if (buffer.stride * 4 < expected_stride) {
        LOGE("frameCallback: Buffer stride too small: %d < %zu",
             buffer.stride * 4, expected_stride);
        ANativeWindow_unlockAndPost(camera->window_);
        return;
    }

    // Use appropriate libyuv conversion based on format
    int result = -1;
    switch (frame->frame_format) {
        case UVC_FRAME_FORMAT_YUYV:
            result = libyuv::YUY2ToARGB(
                static_cast<const uint8_t*>(frame->data),     // src_yuy2
                frame->step,                                  // src_stride_yuy2 (use frame->step)
                static_cast<uint8_t*>(buffer.bits),           // dst_argb
                buffer.stride * 4,                            // dst_stride_argb (4 bytes per pixel)
                frame->width,                                 // width
                frame->height                                 // height
            );
            break;
        case UVC_FRAME_FORMAT_UYVY:
            result = libyuv::UYVYToARGB(
                static_cast<const uint8_t*>(frame->data),     // src_uyvy
                frame->step,                                  // src_stride_uyvy
                static_cast<uint8_t*>(buffer.bits),           // dst_argb
                buffer.stride * 4,                            // dst_stride_argb
                frame->width,                                 // width
                frame->height                                 // height
            );
            break;
        case UVC_FRAME_FORMAT_MJPEG:
            // MJPEG needs to be decoded first - for now, create a placeholder
            LOGW("frameCallback: MJPEG decoding not yet implemented, using placeholder");
            memset(buffer.bits, 0x80, buffer.width * buffer.height * 4); // Gray placeholder
            result = 0;
            break;
        case UVC_FRAME_FORMAT_UNCOMPRESSED:
            // Try YUYV conversion as fallback for uncompressed
            result = libyuv::YUY2ToARGB(
                static_cast<const uint8_t*>(frame->data),
                frame->step,
                static_cast<uint8_t*>(buffer.bits),
                buffer.stride * 4,
                frame->width,
                frame->height
            );
            break;
        default:
            LOGE("frameCallback: No conversion available for format %d", frame->frame_format);
            ANativeWindow_unlockAndPost(camera->window_);
            return;
    }
    
    if (result != 0) {
        LOGE("frameCallback: Format conversion failed: %d for %s", result, format_name);
        ANativeWindow_unlockAndPost(camera->window_);
        return;
    }
    
    // Convert from ARGB to ABGR using libyuv function
    result = libyuv::ARGBToABGR(
        static_cast<uint8_t*>(buffer.bits),           // src_argb
        buffer.stride * 4,                            // src_stride_argb
        static_cast<uint8_t*>(buffer.bits),           // dst_abgr (same buffer)
        buffer.stride * 4,                            // dst_stride_abgr
        frame->width,                                 // width
        frame->height                                 // height
    );
    
    if (result != 0) {
        LOGE("frameCallback: ARGBToABGR conversion failed: %d", result);
        ANativeWindow_unlockAndPost(camera->window_);
        return;
    }

    int post_ret = ANativeWindow_unlockAndPost(camera->window_);
    if (post_ret != 0) {
        LOGE("frameCallback: Failed to unlock and post: %d", post_ret);
    }
}

// USB Event Thread Loop
void UVCCamera::usbEventThreadLoop() {
    LOGI("USB event thread started.");
    struct timeval tv;
    tv.tv_sec = 0;
    tv.tv_usec = 100000; // 100ms timeout for libusb_handle_events_timeout_completed

    while (keep_usb_event_thread_running_.load()) {
        // LOGI("USB event thread: calling libusb_handle_events_timeout_completed"); // Too verbose
        int res = libusb_handle_events_timeout_completed(usb_ctx_, &tv, nullptr);
        if (res < 0) {
            LOGE("USB event thread: libusb_handle_events_timeout_completed error %d: %s", res, libusb_error_name(res));
            if (res == LIBUSB_ERROR_INTERRUPTED) { // This can happen if libusb_exit is called elsewhere
                LOGW("USB event thread: libusb_handle_events was interrupted. May be shutting down.");
            }
            // Potentially break or add a small sleep if errors are persistent but not fatal for the loop
        }
    }
    LOGI("USB event thread finished.");
}

void UVCCamera::printInterfaceInfo(const libusb_interface_descriptor* if_desc) {
    if (!if_desc) return;
    
    LOGI("Interface %d:", if_desc->bInterfaceNumber);
    LOGI("  Class: %d", if_desc->bInterfaceClass);
    LOGI("  Subclass: %d", if_desc->bInterfaceSubClass);
    LOGI("  Protocol: %d", if_desc->bInterfaceProtocol);
    LOGI("  Alt settings: %d", if_desc->bNumEndpoints);
    
    // Print endpoint information
    for (int i = 0; i < if_desc->bNumEndpoints; i++) {
        const libusb_endpoint_descriptor* ep = &if_desc->endpoint[i];
        LOGI("  Endpoint %d:", i);
        LOGI("    Address: 0x%02x", ep->bEndpointAddress);
        LOGI("    Attributes: 0x%02x", ep->bmAttributes);
        LOGI("    Max packet size: %d", ep->wMaxPacketSize);
        LOGI("    Interval: %d", ep->bInterval);
    }
}

void UVCCamera::printFormatInfo(const uvc_format_desc_t* format_desc) {
    if (!format_desc) return;
    
    const char* format_name;
    switch (format_desc->bDescriptorSubtype) {
        case UVC_VS_FORMAT_UNCOMPRESSED:
            format_name = "UncompressedFormat";
            break;
        case UVC_VS_FORMAT_MJPEG:
            format_name = "MJPEGFormat";
            break;
        case UVC_VS_FORMAT_FRAME_BASED:
            format_name = "FrameFormat";
            break;
        default:
            format_name = "Unknown";
            break;
    }
    
    LOGI("Format: %s", format_name);
    LOGI("  Format Index: %d", format_desc->bFormatIndex);
    LOGI("  Number of frame descriptors: %d", format_desc->bNumFrameDescriptors);
    
    // Print format-specific information
    if (format_desc->bDescriptorSubtype == UVC_VS_FORMAT_MJPEG) {
        LOGI("  FourCC: %.4s", format_desc->fourccFormat);
    } else {
        LOGI("  Bits per pixel: %d", format_desc->bBitsPerPixel);
        LOGI("  GUID: %02x%02x%02x%02x-%02x%02x-%02x%02x-%02x%02x-%02x%02x%02x%02x%02x%02x",
             format_desc->guidFormat[0], format_desc->guidFormat[1],
             format_desc->guidFormat[2], format_desc->guidFormat[3],
             format_desc->guidFormat[4], format_desc->guidFormat[5],
             format_desc->guidFormat[6], format_desc->guidFormat[7],
             format_desc->guidFormat[8], format_desc->guidFormat[9],
             format_desc->guidFormat[10], format_desc->guidFormat[11],
             format_desc->guidFormat[12], format_desc->guidFormat[13],
             format_desc->guidFormat[14], format_desc->guidFormat[15]);
    }
    
    LOGI("  Default frame index: %d", format_desc->bDefaultFrameIndex);
    LOGI("  Aspect ratio: %dx%d", format_desc->bAspectRatioX, format_desc->bAspectRatioY);
}

void UVCCamera::printFrameInfo(const uvc_frame_desc_t* frame_desc) {
    if (!frame_desc) return;
    
    LOGI("Frame: %dx%d", frame_desc->wWidth, frame_desc->wHeight);
    LOGI("  Frame Index: %d", frame_desc->bFrameIndex);
    LOGI("  Frame Interval: %d (%.2f fps)", 
         frame_desc->dwDefaultFrameInterval,
         10000000.0 / frame_desc->dwDefaultFrameInterval);
}

bool UVCCamera::enumerateInterfaces() {
    if (!devh_) {
        LOGE("Device not initialized");
        return false;
    }

    // Get the device descriptor
    uvc_device_descriptor_t* desc;
    uvc_error_t res = uvc_get_device_descriptor(dev_, &desc);
    if (res != UVC_SUCCESS) {
        LOGE("Failed to get device descriptor: %s", uvc_strerror(res));
        return false;
    }

    // Get the libusb device handle
    libusb_device_handle* usb_devh = uvc_get_libusb_handle(devh_);
    if (!usb_devh) {
        LOGE("Failed to get libusb device handle");
        uvc_free_device_descriptor(desc);
        return false;
    }

    // Get the libusb device
    libusb_device* usb_dev = libusb_get_device(usb_devh);
    if (!usb_dev) {
        LOGE("Failed to get libusb device");
        uvc_free_device_descriptor(desc);
        return false;
    }

    // Get the active configuration
    libusb_config_descriptor* config;
    int ret = libusb_get_active_config_descriptor(usb_dev, &config);
    if (ret != LIBUSB_SUCCESS) {
        LOGE("Failed to get config descriptor: %s", libusb_error_name(ret));
        uvc_free_device_descriptor(desc);
        return false;
    }

    LOGI("Device has %d interfaces", config->bNumInterfaces);
    
    // Print information about each interface
    for (int i = 0; i < config->bNumInterfaces; i++) {
        const libusb_interface* interface = &config->interface[i];
        LOGI("Interface %d has %d alternate settings", i, interface->num_altsetting);
        
        // Print each alternate setting
        for (int j = 0; j < interface->num_altsetting; j++) {
            const libusb_interface_descriptor* if_desc = &interface->altsetting[j];
            LOGI("Alternate setting %d:", j);
            printInterfaceInfo(if_desc);
        }
    }

    libusb_free_config_descriptor(config);
    uvc_free_device_descriptor(desc);
    return true;
}

bool UVCCamera::enumerateFormats() {
    if (!devh_) {
        LOGE("Device not initialized");
        return false;
    }

    const uvc_format_desc_t* format_desc = uvc_get_format_descs(devh_);
    if (!format_desc) {
        LOGE("Failed to get format descriptors");
        return false;
    }

    LOGI("Available formats:");
    while (format_desc) {
        printFormatInfo(format_desc);
        
        // Print frame information for this format
        const uvc_frame_desc_t* frame_desc = format_desc->frame_descs;
        while (frame_desc) {
            printFrameInfo(frame_desc);
            frame_desc = frame_desc->next;
        }
        
        format_desc = format_desc->next;
    }

    return true;
}

void UVCCamera::printDeviceInfo() {
    if (!devh_) {
        LOGE("Device not initialized");
        return;
    }

    uvc_device_descriptor_t* desc;
    uvc_error_t res = uvc_get_device_descriptor(dev_, &desc);
    if (res != UVC_SUCCESS) {
        LOGE("Failed to get device descriptor: %s", uvc_strerror(res));
        return;
    }

    LOGI("Device Information:");
    LOGI("  Manufacturer: %s", desc->manufacturer ? desc->manufacturer : "Unknown");
    LOGI("  Product: %s", desc->product ? desc->product : "Unknown");
    LOGI("  Serial Number: %s", desc->serialNumber ? desc->serialNumber : "Unknown");
    LOGI("  Vendor ID: 0x%04x", desc->idVendor);
    LOGI("  Product ID: 0x%04x", desc->idProduct);
    LOGI("  UVC Version: %d.%d", (desc->bcdUVC >> 8) & 0xFF, desc->bcdUVC & 0xFF);

    uvc_free_device_descriptor(desc);
}

// Raw frame capture implementation
bool UVCCamera::getCapturedFrameData(uint8_t* buffer, int* width, int* height) {
    std::lock_guard<std::mutex> lock(capture_mutex_);
    
    if (!has_captured_frame_.load() || captured_frame_data_.empty()) {
        return false;
    }
    
    *width = captured_frame_width_;
    *height = captured_frame_height_;
    
    // Copy the captured YUYV data to the provided buffer
    std::memcpy(buffer, captured_frame_data_.data(), captured_frame_data_.size());
    
    // Reset the capture flag
    has_captured_frame_.store(false);
    
    LOGI("📸 Retrieved captured frame: %dx%d, %zu bytes", 
         *width, *height, captured_frame_data_.size());
    
    return true;
}

// ===== UVC FRAMERATE CONTROL IMPLEMENTATION =====

std::vector<int> UVCCamera::getSupportedFrameRates(int width, int height) {
    std::vector<int> frameRates;
    
    if (!devh_) {
        LOGE("Device not initialized");
        return frameRates;
    }
    
    LOGI("🔍 Querying supported frame rates for %dx%d...", width, height);
    
    // Get format descriptors
    const uvc_format_desc_t* format_desc = uvc_get_format_descs(devh_);
    while (format_desc) {
        // Look for YUYV format
        if (format_desc->bDescriptorSubtype == UVC_VS_FORMAT_UNCOMPRESSED) {
            LOGI("Checking format index %d", format_desc->bFormatIndex);
            
            // Check frame descriptors for this format
            const uvc_frame_desc_t* frame_desc = format_desc->frame_descs;
            while (frame_desc) {
                if (frame_desc->wWidth == width && frame_desc->wHeight == height) {
                    LOGI("Found matching resolution %dx%d (frame index %d)", 
                         width, height, frame_desc->bFrameIndex);
                    
                    // Extract all supported frame intervals
                    if (frame_desc->bFrameIntervalType == 0) {
                        // Continuous frame intervals
                        uint32_t min_interval = frame_desc->dwMinFrameInterval;
                        uint32_t max_interval = frame_desc->dwMaxFrameInterval;
                        uint32_t step_interval = frame_desc->dwFrameIntervalStep;
                        
                        LOGI("Continuous intervals: min=%u, max=%u, step=%u", 
                             min_interval, max_interval, step_interval);
                        
                        for (uint32_t interval = min_interval; interval <= max_interval; interval += step_interval) {
                            int fps = (int)(10000000.0 / interval);
                            if (fps > 0 && fps <= 120) { // Reasonable FPS range
                                frameRates.push_back(fps);
                                LOGI("  📊 Supported FPS: %d (interval: %u)", fps, interval);
                            }
                        }
                    } else {
                        // Discrete frame intervals
                        LOGI("Discrete intervals: count=%d", frame_desc->bFrameIntervalType);
                        for (int i = 0; i < frame_desc->bFrameIntervalType; i++) {
                            uint32_t interval = frame_desc->intervals[i];
                            int fps = (int)(10000000.0 / interval);
                            frameRates.push_back(fps);
                            LOGI("  📊 Supported FPS: %d (interval: %u)", fps, interval);
                        }
                    }
                }
                frame_desc = frame_desc->next;
            }
        }
        format_desc = format_desc->next;
    }
    
    // Remove duplicates and sort
    std::sort(frameRates.begin(), frameRates.end());
    frameRates.erase(std::unique(frameRates.begin(), frameRates.end()), frameRates.end());
    
    LOGI("📊 Final supported frame rates for %dx%d:", width, height);
    for (int fps : frameRates) {
        LOGI("  - %d fps", fps);
    }
    
    return frameRates;
}

bool UVCCamera::setFrameRate(int width, int height, int fps) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    if (!devh_) {
        LOGE("Device not initialized");
        return false;
    }
    
    LOGI("🎯 Setting frame rate to %d fps for %dx%d", fps, width, height);
    
    // Stop current streaming if active
    bool was_streaming = is_streaming_;
    if (was_streaming) {
        LOGI("Stopping current stream to change framerate...");
        if (devh_) {
            uvc_stop_streaming(devh_);
        }
        is_streaming_ = false;
    }
    
    // Get new stream control with desired framerate
    uvc_stream_ctrl_t new_ctrl;
    uvc_error_t res = uvc_get_stream_ctrl_format_size(
        devh_, &new_ctrl,
        UVC_FRAME_FORMAT_YUYV,
        width, height, fps
    );
    
    if (res != UVC_SUCCESS) {
        LOGE("Failed to get stream control for %dx%d @ %dfps: %s (%d)", 
             width, height, fps, uvc_strerror(res), res);
        
        // Try to restart with original settings if we were streaming
        if (was_streaming) {
            LOGI("Attempting to restart with original settings...");
            uvc_start_streaming(devh_, &ctrl_, frameCallback, this, 0);
            is_streaming_ = true;
        }
        return false;
    }
    
    // Log negotiated parameters
    float actual_fps = 10000000.0f / new_ctrl.dwFrameInterval;
    LOGI("✅ Negotiated framerate: %.2f fps (requested: %d fps)", actual_fps, fps);
    LOGI("Stream control parameters:");
    LOGI("  bFormatIndex: %u", new_ctrl.bFormatIndex);
    LOGI("  bFrameIndex: %u", new_ctrl.bFrameIndex);
    LOGI("  dwFrameInterval: %u (%.2f fps)", new_ctrl.dwFrameInterval, actual_fps);
    
    // Update our control structure
    ctrl_ = new_ctrl;
    
    // Restart streaming if it was active
    if (was_streaming && window_) {
        LOGI("Restarting stream with new framerate...");
        res = uvc_start_streaming(devh_, &ctrl_, frameCallback, this, 0);
        if (res != UVC_SUCCESS) {
            LOGE("Failed to restart streaming: %s (%d)", uvc_strerror(res), res);
            return false;
        }
        is_streaming_ = true;
        LOGI("✅ Stream restarted successfully with new framerate");
    }
    
    return true;
}

int UVCCamera::getCurrentFrameRate() {
    if (!devh_ || !is_streaming_) {
        LOGE("Camera not streaming");
        return 0;
    }
    
    float fps = 10000000.0f / ctrl_.dwFrameInterval;
    LOGI("📊 Current frame rate: %.2f fps (interval: %u)", fps, ctrl_.dwFrameInterval);
    return (int)round(fps);
}

void UVCCamera::enumerateAllFrameRates() {
    if (!devh_) {
        LOGE("Device not initialized");
        return;
    }
    
    LOGI("🔍 Enumerating ALL supported frame rates...");
    
    const uvc_format_desc_t* format_desc = uvc_get_format_descs(devh_);
    while (format_desc) {
        if (format_desc->bDescriptorSubtype == UVC_VS_FORMAT_UNCOMPRESSED) {
            LOGI("Format Index %d:", format_desc->bFormatIndex);
            
            const uvc_frame_desc_t* frame_desc = format_desc->frame_descs;
            while (frame_desc) {
                LOGI("  Resolution: %dx%d (Frame Index %d)", 
                     frame_desc->wWidth, frame_desc->wHeight, frame_desc->bFrameIndex);
                
                if (frame_desc->bFrameIntervalType == 0) {
                    // Continuous
                    uint32_t min_interval = frame_desc->dwMinFrameInterval;
                    uint32_t max_interval = frame_desc->dwMaxFrameInterval;
                    float min_fps = 10000000.0f / max_interval;
                    float max_fps = 10000000.0f / min_interval;
                    LOGI("    Continuous: %.1f - %.1f fps", min_fps, max_fps);
                } else {
                    // Discrete
                    LOGI("    Discrete frame rates:");
                    for (int i = 0; i < frame_desc->bFrameIntervalType; i++) {
                        uint32_t interval = frame_desc->intervals[i];
                        float fps = 10000000.0f / interval;
                        LOGI("      %.2f fps (interval: %u)", fps, interval);
                    }
                }
                frame_desc = frame_desc->next;
            }
        }
        format_desc = format_desc->next;
    }
    
    LOGI("🔍 Frame rate enumeration complete");
}

// ===== DIRECT VIDEO RECORDING IMPLEMENTATION =====

void UVCCamera::setVideoEncoderCallback(void (*callback)(uint8_t* yuvData, int width, int height, int64_t timestampUs, void* userPtr), void* userPtr) {
    video_encoder_callback_ = callback;
    video_callback_user_ptr_ = userPtr;
    LOGI("🎥 Video encoder callback set: %p", callback);
}

void UVCCamera::convertYUYVToYUV420(const uint8_t* yuyv_data, uint8_t* yuv420_data, int width, int height) {
    // YUYV format: Y0 U0 Y1 V0 (4 bytes for 2 pixels)
    // YUV420 format: All Y values, then U values (1/4 size), then V values (1/4 size)
    
    const int ySize = width * height;
    const int uvSize = ySize / 4;
    
    uint8_t* yPlane = yuv420_data;
    uint8_t* uPlane = yuv420_data + ySize;
    uint8_t* vPlane = yuv420_data + ySize + uvSize;
    
    // Extract Y values (every byte at even positions)
    for (int i = 0; i < ySize; i++) {
        yPlane[i] = yuyv_data[i * 2];
    }
    
    // Extract U and V values (subsampled 2x2)
    int uvIndex = 0;
    for (int y = 0; y < height; y += 2) {
        for (int x = 0; x < width; x += 2) {
            int yuyvIndex = (y * width + x) * 2;
            uPlane[uvIndex] = yuyv_data[yuyvIndex + 1];     // U value
            vPlane[uvIndex] = yuyv_data[yuyvIndex + 3];     // V value
            uvIndex++;
        }
    }
} 