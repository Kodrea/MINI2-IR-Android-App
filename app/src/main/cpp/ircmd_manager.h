#pragma once

#include <android/log.h>
#include <libusb.h>
#include <mutex>
#include "libircmd.h"  // Include this for error codes and function declarations

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

// Based on _IrcmdHandle_t from ir_control_handle.md.c
// The original is 0x6c50 bytes, which is huge due to `file_info[100]`.
// We need to ensure the layout includes space for total_length and transferred_length
// as standard_cmd_write accesses them.
typedef struct {
    void* read_func;                                 // Offset 0
    MySdk_UsbWriteFunc write_func;                   // Offset 8 (IMPORTANT)
    void* firmware_download_func;                    // Offset 16
    void* detect_device_status_func;                 // Offset 24
    void* command_channel_type_get_func;             // Offset 32
    void* write_func_without_read_return_status;     // Offset 40
    void* device_open_func;                          // Offset 48
    void* device_init_func;                          // Offset 56
    void* device_close_func;                         // Offset 64
    MySdk_IruvcHandle_t *driver_handle;              // Offset 72 (IMPORTANT - points to MySdk_IruvcHandle_t)
    uint8_t driver_type;                             // Offset 80
    uint8_t _pad_to_align_slave_id[1];               // Padding to align next uint16_t (make it total 2 bytes)
    uint16_t slave_id;                               // Offset 82
    uint16_t polling_time;                           // Offset 84 (IMPORTANT)
    void * (*upgrade_callback)(void *, void *);      // Offset 88 (approx, if pointers are 8B, uint16_t 2B)
    void *upgrade_priv_data;                         // Offset 96 (approx)

    // Placeholder for the large file_info[100] array and file_num that precede total_length.
    // From _IrcmdHandle_t decompilation:
    //   struct file_info_t file_info[100];
    //   uint16_t file_num;
    // Let's estimate file_info_t is moderately sized, e.g. 32 bytes. 100 * 32 = 3200 bytes.
    // Add space for file_num (2 bytes). Total padding needed ~3202 bytes.
    // Round up for alignment, e.g., 3208 bytes.
    uint8_t file_info_and_num_padding[3208];         // Offset after upgrade_priv_data

    uint32_t total_length;                           // Offset ~96 + 8 (for priv_data if ptr) + 3208 = ~3312
    uint32_t transferred_length;                     // Offset ~3316

    // Placeholder for:
    //   enum device_type_e device_type;
    //   uint8_t device_type_got_flag;
    // And any other trailing members or general padding to reach a safer total size.
    uint8_t remaining_sdk_fields_padding[128];       // Generous final padding

} MySdk_IrcmdHandle_t;

class IrcmdManager {
public:
    IrcmdManager();
    ~IrcmdManager();

    // Initialize the manager with a file descriptor
    bool init(int fileDescriptor);
    
    // Clean up resources
    void cleanup();
    
    // Get the last error code
    int getLastError() const { return last_error_; }
    
    // Get the last error message
    const char* getLastErrorMessage() const;
    
    // Check if the manager is initialized
    bool isInitialized() const { return is_initialized_; }

    // FFC command
    int performFFC();

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