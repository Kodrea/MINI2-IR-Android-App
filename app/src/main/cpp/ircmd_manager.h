#pragma once

#include <android/log.h>
#include <libusb.h>
#include <mutex>
#include "libircmd.h"  // Include this for error codes and function declarations
#include "camera_function_registry.h"  // Include our new registry

// Logging macros
#define IRCMD_LOG_TAG "IrcmdManager"
#define IRCMD_LOGI(...) __android_log_print(ANDROID_LOG_INFO, IRCMD_LOG_TAG, __VA_ARGS__)
#define IRCMD_LOGW(...) __android_log_print(ANDROID_LOG_WARN, IRCMD_LOG_TAG, __VA_ARGS__)
#define IRCMD_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, IRCMD_LOG_TAG, __VA_ARGS__)
#define IRCMD_LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, IRCMD_LOG_TAG, __VA_ARGS__)

// Decompiled/SDK-compatible structure definitions
// Based on ir_video_handle.md.c and ir_control_handle.md.c

// Simplified version of uvc_device_handle_t, to correctly offset usb_devh
// Based on struct uvc_device_handle from ir_video_handle.md.c
typedef struct {
    void *dev;                             // Offset 0; Placeholder for struct uvc_device *
    void *prev;                            // Offset 8; Placeholder for struct uvc_device_handle *
    void *next;                            // Offset 16; Placeholder for struct uvc_device_handle *
    struct libusb_device_handle *usb_devh; // Offset 24; Actual libusb_device_handle
    // Remaining fields from uvc_device_handle are padded if their exact layout/usage is unknown
    // but important for overall size if IruvcHandle_t relies on it.
    // info (ptr) + status_xfer (ptr) = 16 bytes
    // status_buf[32] = 32 bytes
    // status_cb (ptr) + status_user_ptr (ptr) + button_cb (ptr) + button_user_ptr (ptr) = 32 bytes
    // streams (ptr) = 8 bytes
    // is_isight (1) + claimed (4) + padding ~ 8 bytes
    // Total after usb_devh (offset 24, size 8) = 32 bytes used. approx 128 - 32 = 96 bytes padding needed.
    uint8_t internal_padding[96];          // Padding to make total size around 128 bytes.
} MySdk_uvc_device_handle_t;

// Based on _IruvcHandle_t from ir_video_handle.md.c
// Size 0x68 (104 bytes)
typedef struct {
    void *ctx;                          // Offset 0    (uvc_context_t *)
    void *dev;                          // Offset 8    (uvc_device_t *)
    MySdk_uvc_device_handle_t *devh;    // Offset 16   (uvc_device_handle_t *) - OURS NOW
    void *ctrl;                         // Offset 24   (uvc_stream_ctrl_t *)
    void *cur_dev_cfg;                  // Offset 32   (struct DevCfg_t *)
    void *raw_frame1;                   // Offset 40   (uvc_frame_t *)
    pthread_mutex_t mtx;                // Offset 48   (pthread_mutex_t is 40 bytes on Android)
    int same_index;                     // Offset 88
    int got_frame_cnt;                  // Offset 92
    int max_delay_ms;                   // Offset 96
    uint8_t padding_to_104[4];          // Offset 100, to make it 104 bytes if int is 4 bytes
} MySdk_IruvcHandle_t;

// Typedef for the function pointer, matching UsbWriteFunc for our purposes
typedef int (*MySdk_UsbWriteFunc)(void* driver_handle, void* usb_cmd_param, uint8_t* data, uint16_t len);

// Add device type enum before the structure definition
typedef enum device_type_e {
    DEV_CS640 = 1,
    DEV_G1280S = 2,
    DEV_MINI2_384 = 3,
    DEV_AC02 = 4,
    DEV_P2L = 5,
    DEV_TINY2_C = 6,
    DEV_MINI2_256 = 7,
    DEV_MINI2_640 = 8,
    DEV_G21280S = 9
} device_type_e;

// Forward declare the function type to match original
typedef int (*HandleFunc)(void* driver_handle, void* usb_cmd_param, uint8_t* data, uint16_t len);

// Structure to match original decompiled version exactly
typedef struct {
    HandleFunc read_func;                            // Offset 0
    HandleFunc write_func;                           // Offset 8
    HandleFunc firmware_download_func;               // Offset 16
    HandleFunc detect_device_status_func;            // Offset 24
    HandleFunc command_channel_type_get_func;        // Offset 32
    HandleFunc write_func_without_read_return_status;// Offset 40
    HandleFunc device_open_func;                     // Offset 48
    HandleFunc device_init_func;                     // Offset 56
    HandleFunc device_close_func;                    // Offset 64
    void* driver_handle;                             // Offset 72
    uint8_t driver_type;                             // Offset 80
    uint16_t slave_id;                               // Offset 82 (no padding needed, naturally aligned)
    uint16_t polling_time;                           // Offset 84
    void* (*upgrade_callback)(void*, void*);         // Offset 88
    void* upgrade_priv_data;                         // Offset 96

    // File info array and count
    struct {
        uint8_t dummy[32];  // Placeholder for file_info_t structure
    } file_info[100];                               // Offset 104
    uint16_t file_num;                              // Offset 3304 (104 + 100*32)

    uint32_t total_length;                          // Offset 3306 (3304 + 2)
    uint32_t transferred_length;                    // Offset 3310 (3306 + 4)
    device_type_e device_type;                      // Offset 3314 (3310 + 4)
    uint8_t device_type_got_flag;                   // Offset 3318 (3314 + 4)
} MySdk_IrcmdHandle_t;

// Camera function enumerations
enum CameraFunction {
    // Getter functions
    GET_BRIGHTNESS = 0,
    
    // Setter functions
    SET_BRIGHTNESS = 1,
    SET_CONTRAST = 2,
    
    // Action functions
    PERFORM_FFC = 3,
    
    // Palette functions
    SET_PALETTE = 4,
    
    // Scene mode functions
    SET_SCENE_MODE = 5,
    
    // Noise reduction functions
    SET_NOISE_REDUCTION = 6,
    SET_TIME_NOISE_REDUCTION = 7,
    SET_SPACE_NOISE_REDUCTION = 8,
    
    // Detail enhancement function
    SET_DETAIL_ENHANCEMENT = 9,
    
    // Global contrast function
    SET_GLOBAL_CONTRAST = 10
    
    // Add more as needed...
};

class IrcmdManager {
public:
    IrcmdManager();
    ~IrcmdManager();

    // Initialize the manager with a file descriptor
    bool init(int fileDescriptor, int deviceType);
    
    // Clean up resources
    void cleanup();
    
    // Get the last error code
    int getLastError() const { return last_error_; }
    
    // Get the last error message
    const char* getLastErrorMessage() const;
    
    // Check if the manager is initialized
    bool isInitialized() const { return is_initialized_; }

    // Get properly cast handle to use with SDK functions
    IrcmdHandle_t* getCmdHandle() const {
        return reinterpret_cast<IrcmdHandle_t*>(ircmd_handle_);
    }

    // New registry-based function execution  
    int executeGetFunction(CameraFunctionId functionId, int& outValue);
    int executeSetFunction(CameraFunctionId functionId, int value);
    int executeSetFunction2(CameraFunctionId functionId, int value1, int value2);
    int executeActionFunction(CameraFunctionId functionId);
    
    // Legacy function execution (for backward compatibility during transition)
    int executeGetFunction(CameraFunction func, int& outValue);
    int executeSetFunction(CameraFunction func, int value);
    int executeActionFunction(CameraFunction func);

    // Set device type based on actual device
    void setDeviceType();

private:
    // Set the last error code
    void setError(int error_code);

    // Internal state
    bool is_initialized_;
    int last_error_;
    std::mutex mutex_;

    // USB context and device handle
    libusb_context* usb_ctx_;
    libusb_device_handle* usb_devh_;

    // IRCMD handle
    MySdk_IrcmdHandle_t* ircmd_handle_;
}; 