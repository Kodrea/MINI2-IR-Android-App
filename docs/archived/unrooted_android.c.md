/*
 *  libusb example program for reading out USB descriptors on unrooted Android
 *  (based on testlibusb.c)
 *
 *  Copyright 2020-2021 Peter Stoiber
 *
 *  This library is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public
 *  License as published by the Free Software Foundation; either
 *  version 2.1 of the License, or (at your option) any later version.
 *
 *  This library is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 *  Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  Please contact the author if you need another license.
 *  This Repository is provided "as is", without warranties of any kind.
*/

/*
 * This example creates a shared object which can be accessed over JNA or JNI from Java or Kotlin in Android.
 * Hint: If you are using Android Studio, set the "Debug type" to "Java Only" to receive debug messages.
 */

/*
 * Usage:
 * First, you have to connect your USB device from the Java side.
 * Use the android.hardware.usb class to find the USB device, claim the interfaces, and open the usb_device_connection
 * Obtain the native File Descriptor --> usb_device_connection.getFileDescriptor()
 * Pass the received int value to the unrooted_usb_description method of this code (over JNA)
 */

/*
 * libusb can only be included in Android projects using NDK for now. (CMake is not supported at the moment)
 * Clone the libusb git repo into your Android project and include the Android.mk file in your build.gradle.
 */

/*
 Example JNA Approach:
    public interface unrooted_sample extends Library {
        public static final unrooted_sample INSTANCE = Native.load("unrooted_android", unrooted_sample.class);
        public int unrooted_usb_description (int fileDescriptor);
    }
    unrooted_sample.INSTANCE.unrooted_usb_description( usbDeviceConnection.getFileDescriptor());
 */

#include <jni.h>
#include <string.h>
#include "unrooted_android.h"
#include "libusb.h"
#include <android/log.h>
#include <pthread.h>
#include <stdint.h>
#define  LOG_TAG    "LibUsb"
#define  LOGD(...)  __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define  LOGE(...)  __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define IRLIB_SUCCESS 0 // Define if not already present from other includes
typedef int IrlibError_e; // Define IrlibError_e as an integer type

// Forward declarations for ACTUAL library functions (expected to be linked)
// These signatures are based on Android_USB_outline.c
extern int iruvc_usb_data_write(void* iruvc_handle_ptr, void* usb_cmd_param, uint8_t* data, uint16_t len);
extern IrlibError_e basic_ffc_update(void* ircmd_handle_ptr);

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

int verbose = 0;

// Definitions from Android_USB_outline.c (Step 4) - REPLACED BY MySdk_IruvcHandle_t
// Minimal structure to hold the libusb_device_handle*
// typedef struct {
//     libusb_device_handle *usb_devh; 
// } MinimalDeviceHandleHolder; 

// Simplified IruvcHandle_t for this specific command path - REPLACED BY MySdk_IruvcHandle_t
// typedef struct {
//     MinimalDeviceHandleHolder *devh; 
//     pthread_mutex_t mtx;
//     // Other fields can be added later if needed by iruvc_usb_data_write
// } MyManualIruvcHandle_t;

// Definitions from Android_USB_outline.c (Step 5) - REPLACED BY MySdk_IrcmdHandle_t
// Actual signature needed for write_func, matching iruvc_usb_data_write after casting IruvcHandle_t:
// The first parameter is effectively MyManualIruvcHandle_t* after casting from void*
// typedef int (*UsbWriteFunc)(void* /* MyManualIruvcHandle_t* */, void* /* usb_cmd_param */, uint8_t* /* data */, uint16_t /* len */);

// typedef struct {
//     void *driver_handle;       // Points to my_iruvc_handle
//     UsbWriteFunc write_func;    // Points to iruvc_usb_data_write
//     uint16_t polling_time;
//     void * (*upgrade_callback)(void *, void *); // Can be NULL
//     // Other fields zeroed or NULL if not directly used by basic_ffc_update via standard_cmd_write
//     uint8_t driver_type;      // Example: 0
//     uint16_t slave_id;        // Example: 0
//     // ... other members from actual IrcmdHandle_t struct ...
// } MyManualIrcmdHandle_t_for_cmd; 

// Placeholder/stub for iruvc_usb_data_write
// This needs to match the UsbWriteFunc signature.
// In a real scenario, this function would be part of your linked libraries.
int iruvc_usb_data_write_stub(void* iruvc_handle_ptr, void* usb_cmd_param, uint8_t* data, uint16_t len) {
    LOGD("Native (iruvc_usb_data_write_stub): Called. iruvc_handle_ptr: %p, data_len: %u", iruvc_handle_ptr, len);
    if (iruvc_handle_ptr == NULL) {
        LOGE("Native (iruvc_usb_data_write_stub): iruvc_handle_ptr is NULL!");
        return -1; 
    }
    // Attempt to access through the NEW MySdk_IruvcHandle_t structure
    MySdk_IruvcHandle_t* sdk_iruvc_handle = (MySdk_IruvcHandle_t*)iruvc_handle_ptr;
    if (sdk_iruvc_handle->devh && sdk_iruvc_handle->devh->usb_devh) {
        LOGD("Native (iruvc_usb_data_write_stub): Successfully accessed usb_devh: %p via MySdk_IruvcHandle_t", sdk_iruvc_handle->devh->usb_devh);
    } else {
        LOGE("Native (iruvc_usb_data_write_stub): Failed to access usb_devh via MySdk_IruvcHandle_t!");
        if (!sdk_iruvc_handle->devh) LOGE("sdk_iruvc_handle->devh is NULL");
        else LOGE("sdk_iruvc_handle->devh->usb_devh is NULL");
        return -2;
    }
    return 0; 
}

static void print_endpoint_comp(const struct libusb_ss_endpoint_companion_descriptor *ep_comp)
{
    LOGD("      USB 3.0 Endpoint Companion:\n");
    LOGD("        bMaxBurst:           %u\n", ep_comp->bMaxBurst);
    LOGD("        bmAttributes:        %02xh\n", ep_comp->bmAttributes);
    LOGD("        wBytesPerInterval:   %u\n", ep_comp->wBytesPerInterval);
}

static void print_endpoint(const struct libusb_endpoint_descriptor *endpoint)
{
    int i, ret;

    LOGD("      Endpoint:\n");
    LOGD("        bEndpointAddress:    %02xh\n", endpoint->bEndpointAddress);
    LOGD("        bmAttributes:        %02xh\n", endpoint->bmAttributes);
    LOGD("        wMaxPacketSize:      %u\n", endpoint->wMaxPacketSize);
    LOGD("        bInterval:           %u\n", endpoint->bInterval);
    LOGD("        bRefresh:            %u\n", endpoint->bRefresh);
    LOGD("        bSynchAddress:       %u\n", endpoint->bSynchAddress);

    for (i = 0; i < endpoint->extra_length;) {
        if (LIBUSB_DT_SS_ENDPOINT_COMPANION == endpoint->extra[i + 1]) {
            struct libusb_ss_endpoint_companion_descriptor *ep_comp;

            ret = libusb_get_ss_endpoint_companion_descriptor(NULL, endpoint, &ep_comp);
            if (LIBUSB_SUCCESS != ret)
                continue;

            print_endpoint_comp(ep_comp);

            libusb_free_ss_endpoint_companion_descriptor(ep_comp);
        }

        i += endpoint->extra[i];
    }
}

static void print_altsetting(const struct libusb_interface_descriptor *interface)
{
    uint8_t i;

    LOGD("    Interface:\n");
    LOGD("      bInterfaceNumber:      %u\n", interface->bInterfaceNumber);
    LOGD("      bAlternateSetting:     %u\n", interface->bAlternateSetting);
    LOGD("      bNumEndpoints:         %u\n", interface->bNumEndpoints);
    LOGD("      bInterfaceClass:       %u\n", interface->bInterfaceClass);
    LOGD("      bInterfaceSubClass:    %u\n", interface->bInterfaceSubClass);
    LOGD("      bInterfaceProtocol:    %u\n", interface->bInterfaceProtocol);
    LOGD("      iInterface:            %u\n", interface->iInterface);

    for (i = 0; i < interface->bNumEndpoints; i++)
        print_endpoint(&interface->endpoint[i]);
}

static void print_2_0_ext_cap(struct libusb_usb_2_0_extension_descriptor *usb_2_0_ext_cap)
{
    LOGD("    USB 2.0 Extension Capabilities:\n");
    LOGD("      bDevCapabilityType:    %u\n", usb_2_0_ext_cap->bDevCapabilityType);
    LOGD("      bmAttributes:          %08xh\n", usb_2_0_ext_cap->bmAttributes);
}

static void print_ss_usb_cap(struct libusb_ss_usb_device_capability_descriptor *ss_usb_cap)
{
    LOGD("    USB 3.0 Capabilities:\n");
    LOGD("      bDevCapabilityType:    %u\n", ss_usb_cap->bDevCapabilityType);
    LOGD("      bmAttributes:          %02xh\n", ss_usb_cap->bmAttributes);
    LOGD("      wSpeedSupported:       %u\n", ss_usb_cap->wSpeedSupported);
    LOGD("      bFunctionalitySupport: %u\n", ss_usb_cap->bFunctionalitySupport);
    LOGD("      bU1devExitLat:         %u\n", ss_usb_cap->bU1DevExitLat);
    LOGD("      bU2devExitLat:         %u\n", ss_usb_cap->bU2DevExitLat);
}

static void print_bos(libusb_device_handle *handle)
{
    struct libusb_bos_descriptor *bos;
    uint8_t i;
    int ret;

    ret = libusb_get_bos_descriptor(handle, &bos);
    if (ret < 0)
        return;

    LOGD("  Binary Object Store (BOS):\n");
    LOGD("    wTotalLength:            %u\n", bos->wTotalLength);
    LOGD("    bNumDeviceCaps:          %u\n", bos->bNumDeviceCaps);

    for (i = 0; i < bos->bNumDeviceCaps; i++) {
        struct libusb_bos_dev_capability_descriptor *dev_cap = bos->dev_capability[i];

        if (dev_cap->bDevCapabilityType == LIBUSB_BT_USB_2_0_EXTENSION) {
            struct libusb_usb_2_0_extension_descriptor *usb_2_0_extension;

            ret = libusb_get_usb_2_0_extension_descriptor(NULL, dev_cap, &usb_2_0_extension);
            if (ret < 0)
                return;

            print_2_0_ext_cap(usb_2_0_extension);
            libusb_free_usb_2_0_extension_descriptor(usb_2_0_extension);
        } else if (dev_cap->bDevCapabilityType == LIBUSB_BT_SS_USB_DEVICE_CAPABILITY) {
            struct libusb_ss_usb_device_capability_descriptor *ss_dev_cap;

            ret = libusb_get_ss_usb_device_capability_descriptor(NULL, dev_cap, &ss_dev_cap);
            if (ret < 0)
                return;

            print_ss_usb_cap(ss_dev_cap);
            libusb_free_ss_usb_device_capability_descriptor(ss_dev_cap);
        }
    }

    libusb_free_bos_descriptor(bos);
}

static void print_interface(const struct libusb_interface *interface)
{
    int i;

    for (i = 0; i < interface->num_altsetting; i++)
        print_altsetting(&interface->altsetting[i]);
}

static void print_configuration(struct libusb_config_descriptor *config)
{
    uint8_t i;

    LOGD("  Configuration:\n");
    LOGD("    wTotalLength:            %u\n", config->wTotalLength);
    LOGD("    bNumInterfaces:          %u\n", config->bNumInterfaces);
    LOGD("    bConfigurationValue:     %u\n", config->bConfigurationValue);
    LOGD("    iConfiguration:          %u\n", config->iConfiguration);
    LOGD("    bmAttributes:            %02xh\n", config->bmAttributes);
    LOGD("    MaxPower:                %u\n", config->MaxPower);

    for (i = 0; i < config->bNumInterfaces; i++)
        print_interface(&config->interface[i]);
}

static void print_device(libusb_device *dev, libusb_device_handle *handle)
{
    struct libusb_device_descriptor desc;
    unsigned char string[256];
    const char *speed;
    int ret;
    uint8_t i;

    switch (libusb_get_device_speed(dev)) {
        case LIBUSB_SPEED_LOW:		speed = "1.5M"; break;
        case LIBUSB_SPEED_FULL:		speed = "12M"; break;
        case LIBUSB_SPEED_HIGH:		speed = "480M"; break;
        case LIBUSB_SPEED_SUPER:	speed = "5G"; break;
        case LIBUSB_SPEED_SUPER_PLUS:	speed = "10G"; break;
        case LIBUSB_SPEED_SUPER_PLUS_X2:	speed = "20G"; break;
        default:			speed = "Unknown";
    }

    ret = libusb_get_device_descriptor(dev, &desc);
    if (ret < 0) {
        LOGD("failed to get device descriptor");
        return;
    }

    LOGD("Dev (bus %u, device %u): %04X - %04X speed: %s\n",
           libusb_get_bus_number(dev), libusb_get_device_address(dev),
           desc.idVendor, desc.idProduct, speed);

    if (!handle)
        libusb_open(dev, &handle);

    if (handle) {
        if (desc.iManufacturer) {
            ret = libusb_get_string_descriptor_ascii(handle, desc.iManufacturer, string, sizeof(string));
            if (ret > 0)
                LOGD("  Manufacturer:              %s\n", (char *)string);
        }

        if (desc.iProduct) {
            ret = libusb_get_string_descriptor_ascii(handle, desc.iProduct, string, sizeof(string));
            if (ret > 0)
                LOGD("  Product:                   %s\n", (char *)string);
        }

        if (desc.iSerialNumber && verbose) {
            ret = libusb_get_string_descriptor_ascii(handle, desc.iSerialNumber, string, sizeof(string));
            if (ret > 0)
                LOGD("  Serial Number:             %s\n", (char *)string);
        }
    }

    if (verbose) {
        for (i = 0; i < desc.bNumConfigurations; i++) {
            struct libusb_config_descriptor *config;

            ret = libusb_get_config_descriptor(dev, i, &config);
            if (LIBUSB_SUCCESS != ret) {
                LOGD("  Couldn't retrieve descriptors\n");
                continue;
            }

            print_configuration(config);

            libusb_free_config_descriptor(config);
        }

        if (handle && desc.bcdUSB >= 0x0201)
            print_bos(handle);
    }

    if (handle)
        libusb_close(handle);
}


/* fileDescriptor = the native file descriptor obtained in Java and transferred to native over JNA, for example */
JNIEXPORT jstring JNICALL Java_com_example_mini2_1app_MainActivity_unrootedUsbDescription(JNIEnv *env, jobject thiz, jint fileDescriptor)
{
    libusb_context *ctx = NULL;
    libusb_device_handle *devh = NULL;
    libusb_device *dev;
    struct libusb_device_descriptor desc;
    unsigned char manufacturer[256];
    int r = 0;
    verbose = 1; // To ensure print_device and its sub-calls attempt to get string descriptors

    // It seems libusb_set_option for NO_DEVICE_DISCOVERY might be better set per-context
    // or we ensure it's only called once if set with NULL context.
    // For now, assuming it's okay as is for this specific flow.
    r = libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, NULL);
    if (r != LIBUSB_SUCCESS) {
        LOGE("libusb_set_option LIBUSB_OPTION_NO_DEVICE_DISCOVERY failed: %d (%s)", r, libusb_error_name(r));
        // Not returning an error string here, as init might still work for wrapping
        // If this option is critical for your use case, consider returning an error.
    }

    r = libusb_init(&ctx);
    if (r < 0) {
        LOGE("libusb_init failed: %d (%s)", r, libusb_error_name(r));
        return (*env)->NewStringUTF(env, "Error: libusb_init failed");
    }

    r = libusb_wrap_sys_device(ctx, (intptr_t)fileDescriptor, &devh);
    if (r < 0 || devh == NULL) {
        LOGE("libusb_wrap_sys_device failed: %d (%s), handle: %p", r, libusb_error_name(r), devh);
        if(ctx) libusb_exit(ctx);
        return (*env)->NewStringUTF(env, "Error: libusb_wrap_sys_device failed");
    }

    dev = libusb_get_device(devh);
    if (dev == NULL) {
        LOGE("libusb_get_device failed");
        libusb_close(devh);
        libusb_exit(ctx);
        return (*env)->NewStringUTF(env, "Error: libusb_get_device failed");
    }

    r = libusb_get_device_descriptor(dev, &desc);
    if (r < 0) {
        LOGE("failed to get device descriptor: %d (%s)", r, libusb_error_name(r));
        libusb_close(devh);
        libusb_exit(ctx);
        return (*env)->NewStringUTF(env, "Error: failed to get device descriptor");
    }

    // Get Manufacturer String
    if (desc.iManufacturer > 0) {
        r = libusb_get_string_descriptor_ascii(devh, desc.iManufacturer, manufacturer, sizeof(manufacturer));
        if (r > 0) {
            // Successfully got manufacturer string
            LOGD("Manufacturer: %s", (char *)manufacturer);
            jstring manufacturerJavaString = (*env)->NewStringUTF(env, (char*)manufacturer);
            libusb_close(devh);
            libusb_exit(ctx);
            return manufacturerJavaString;
        } else {
            LOGE("Failed to get manufacturer string: %d (%s)", r, libusb_error_name(r));
        }
    } else {
        LOGD("No manufacturer string index.");
    }

    // Fallback or if no manufacturer string
    print_device(dev, devh); // This will still log to logcat via LOGD
    libusb_close(devh); // devh is closed by print_device if handle was null, but good to ensure
    libusb_exit(ctx);

    if (desc.iManufacturer > 0 && r <= 0) {
         return (*env)->NewStringUTF(env, "Error: Failed to retrieve manufacturer string");
    }
    return (*env)->NewStringUTF(env, "Manufacturer: N/A"); // Default if not found or no index
}

// New JNI function for testing Step 4 (IruvcHandle construction and mutex init)
JNIEXPORT jint JNICALL 
Java_com_example_mini2_1app_MainActivity_testManualHandleStep1(JNIEnv *env, jobject thiz, jint fileDescriptor) {
    libusb_context *ctx = NULL;
    libusb_device_handle *devh_from_fd = NULL; 
    int r = 0;

    MySdk_IruvcHandle_t test_sdk_iruvc_handle;         // Using new SDK-like struct
    MySdk_uvc_device_handle_t test_sdk_uvc_devh;       // The sub-struct for devh

    LOGD("Native (testManualHandleStep1): Called with FD: %d", fileDescriptor);

    r = libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, NULL);
    if (r != LIBUSB_SUCCESS) {
        LOGE("Native (testManualHandleStep1): libusb_set_option failed: %d (%s)", r, libusb_error_name(r));
    }

    r = libusb_init(&ctx);
    if (r < 0) {
        LOGE("Native (testManualHandleStep1): libusb_init failed: %d (%s)", r, libusb_error_name(r));
        return r;
    }
    LOGD("Native (testManualHandleStep1): libusb_init success, ctx: %p", ctx);

    r = libusb_wrap_sys_device(ctx, (intptr_t)fileDescriptor, &devh_from_fd);
    if (r < 0 || devh_from_fd == NULL) {
        LOGE("Native (testManualHandleStep1): libusb_wrap_sys_device failed: %d (%s), handle: %p", r, libusb_error_name(r), devh_from_fd);
        if(ctx) libusb_exit(ctx);
        return r == 0 ? -100 : r; 
    }
    LOGD("Native (testManualHandleStep1): libusb_wrap_sys_device success, devh_from_fd: %p", devh_from_fd);
    
    memset(&test_sdk_uvc_devh, 0, sizeof(MySdk_uvc_device_handle_t));
    test_sdk_uvc_devh.usb_devh = devh_from_fd;

    memset(&test_sdk_iruvc_handle, 0, sizeof(MySdk_IruvcHandle_t));
    test_sdk_iruvc_handle.devh = &test_sdk_uvc_devh; 
    // Initialize other potentially important fields based on iruvc_camera_handle_create
    test_sdk_iruvc_handle.max_delay_ms = 2000; // As seen in decompiled code

    LOGD("Native (testManualHandleStep1): Initializing mutex for MySdk_IruvcHandle_t (at offset %ld)...", (long)((uintptr_t)&test_sdk_iruvc_handle.mtx - (uintptr_t)&test_sdk_iruvc_handle));
    r = pthread_mutex_init(&test_sdk_iruvc_handle.mtx, NULL);
    if (r != 0) { 
        LOGE("Native (testManualHandleStep1): pthread_mutex_init failed with error code: %d", r);
        libusb_close(devh_from_fd);
        libusb_exit(ctx);
        return r; 
    }
    LOGD("Native (testManualHandleStep1): pthread_mutex_init success. MySdk_IruvcHandle_t.devh->usb_devh: %p", test_sdk_iruvc_handle.devh->usb_devh);
    LOGD("Native (testManualHandleStep1): test_sdk_iruvc_handle address: %p, mtx address: %p", &test_sdk_iruvc_handle, &test_sdk_iruvc_handle.mtx);
    LOGD("Native (testManualHandleStep1): Manual handle step 1 (mutex init with MySdk_IruvcHandle_t) successful.");

    // Cleanup
    LOGD("Native (testManualHandleStep1): Performing cleanup...");
    pthread_mutex_destroy(&test_sdk_iruvc_handle.mtx);
    LOGD("Native (testManualHandleStep1): Mutex destroyed.");

    if (devh_from_fd) {
        LOGD("Native (testManualHandleStep1): Closing libusb_device_handle: %p", devh_from_fd);
        libusb_close(devh_from_fd); 
    }
    if (ctx) {
        LOGD("Native (testManualHandleStep1): Exiting libusb context: %p", ctx);
        libusb_exit(ctx);
    }
    LOGD("Native (testManualHandleStep1): Cleanup complete.");

    return IRLIB_SUCCESS;
}

// New JNI function for testing Step 5 (IrcmdHandle construction)
JNIEXPORT jint JNICALL 
Java_com_example_mini2_1app_MainActivity_testManualHandleStep2(JNIEnv *env, jobject thiz, jint fileDescriptor) {
    libusb_context *ctx = NULL;
    libusb_device_handle *devh_from_fd = NULL;
    int r = 0;

    MySdk_IruvcHandle_t test_sdk_iruvc_handle;
    MySdk_uvc_device_handle_t test_sdk_uvc_devh;
    MySdk_IrcmdHandle_t test_sdk_ircmd_handle; 

    LOGD("Native (testManualHandleStep2): Called with FD: %d", fileDescriptor);

    r = libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, NULL); 
    if (r != LIBUSB_SUCCESS) {
        LOGE("Native (testManualHandleStep2): libusb_set_option LIBUSB_OPTION_NO_DEVICE_DISCOVERY failed: %d (%s)", r, libusb_error_name(r));
    }

    r = libusb_init(&ctx);
    if (r < 0) {
        LOGE("Native (testManualHandleStep2): libusb_init failed: %d (%s)", r, libusb_error_name(r));
        return r;
    }
    LOGD("Native (testManualHandleStep2): libusb_init success, ctx: %p", ctx);

    r = libusb_wrap_sys_device(ctx, (intptr_t)fileDescriptor, &devh_from_fd);
    if (r < 0 || devh_from_fd == NULL) {
        LOGE("Native (testManualHandleStep2): libusb_wrap_sys_device failed: %d (%s), handle: %p", r, libusb_error_name(r), devh_from_fd);
        if(ctx) libusb_exit(ctx);
        return r == 0 ? -100 : r; 
    }
    LOGD("Native (testManualHandleStep2): libusb_wrap_sys_device success, devh_from_fd: %p", devh_from_fd);

    memset(&test_sdk_uvc_devh, 0, sizeof(MySdk_uvc_device_handle_t));
    test_sdk_uvc_devh.usb_devh = devh_from_fd;

    memset(&test_sdk_iruvc_handle, 0, sizeof(MySdk_IruvcHandle_t));
    test_sdk_iruvc_handle.devh = &test_sdk_uvc_devh;
    test_sdk_iruvc_handle.max_delay_ms = 2000;


    LOGD("Native (testManualHandleStep2): Initializing mutex for MySdk_IruvcHandle_t...");
    r = pthread_mutex_init(&test_sdk_iruvc_handle.mtx, NULL);
    if (r != 0) {
        LOGE("Native (testManualHandleStep2): pthread_mutex_init failed: %d", r);
        libusb_close(devh_from_fd);
        libusb_exit(ctx);
        return r;
    }
    LOGD("Native (testManualHandleStep2): MySdk_IruvcHandle_t constructed. devh->usb_devh: %p", test_sdk_iruvc_handle.devh->usb_devh);

    // Step 5: Construct MySdk_IrcmdHandle_t
    LOGD("Native (testManualHandleStep2): Constructing MySdk_IrcmdHandle_t...");
    memset(&test_sdk_ircmd_handle, 0, sizeof(MySdk_IrcmdHandle_t));
    test_sdk_ircmd_handle.driver_handle = &test_sdk_iruvc_handle;    
    test_sdk_ircmd_handle.write_func = iruvc_usb_data_write_stub; // Assign the STUB function pointer
    test_sdk_ircmd_handle.polling_time = 2000; 
    test_sdk_ircmd_handle.driver_type = 0; 
    test_sdk_ircmd_handle.slave_id = 0;    
    // upgrade_callback can remain NULL as it is in memset

    LOGD("Native (testManualHandleStep2): MySdk_IrcmdHandle_t constructed.");
    LOGD("  driver_handle (offset %ld): %p (should be addr of test_sdk_iruvc_handle: %p)", (long)((uintptr_t)&test_sdk_ircmd_handle.driver_handle - (uintptr_t)&test_sdk_ircmd_handle), test_sdk_ircmd_handle.driver_handle, &test_sdk_iruvc_handle);
    LOGD("  write_func (offset %ld): %p (should be addr of iruvc_usb_data_write_stub)", (long)((uintptr_t)&test_sdk_ircmd_handle.write_func - (uintptr_t)&test_sdk_ircmd_handle), test_sdk_ircmd_handle.write_func);
    LOGD("  polling_time (offset %ld): %u", (long)((uintptr_t)&test_sdk_ircmd_handle.polling_time - (uintptr_t)&test_sdk_ircmd_handle), test_sdk_ircmd_handle.polling_time);

    LOGD("Native (testManualHandleStep2): Testing stubbed write_func call...");
    uint8_t dummy_data[] = {0x01, 0x02};
    // For usb_cmd_param, standard_cmd_write passes a pointer to polling_time (or an array starting with it).
    // Let's mimic this by passing a pointer to our polling_time.
    int write_stub_ret = test_sdk_ircmd_handle.write_func(test_sdk_ircmd_handle.driver_handle, &test_sdk_ircmd_handle.polling_time, dummy_data, sizeof(dummy_data));
    LOGD("Native (testManualHandleStep2): Stubbed write_func returned: %d", write_stub_ret);

    LOGD("Native (testManualHandleStep2): Performing cleanup...");
    pthread_mutex_destroy(&test_sdk_iruvc_handle.mtx);
    LOGD("Native (testManualHandleStep2): Mutex destroyed.");

    if (devh_from_fd) {
        LOGD("Native (testManualHandleStep2): Closing libusb_device_handle: %p", devh_from_fd);
        libusb_close(devh_from_fd); 
    }
    if (ctx) {
        LOGD("Native (testManualHandleStep2): Exiting libusb context: %p", ctx);
        libusb_exit(ctx);
    }
    LOGD("Native (testManualHandleStep2): Cleanup complete.");

    return write_stub_ret == 0 ? IRLIB_SUCCESS : write_stub_ret; 
}

// Stub for basic_ffc_update
IrlibError_e basic_ffc_update_stub(void* ircmd_handle_ptr) {
    LOGD("Native (basic_ffc_update_stub): Called.");
    if (ircmd_handle_ptr == NULL) {
        LOGE("Native (basic_ffc_update_stub): Received NULL ircmd_handle_ptr.");
        return -1; 
    }

    MySdk_IrcmdHandle_t* cmd_handle = (MySdk_IrcmdHandle_t*)ircmd_handle_ptr; // Use new struct

    LOGD("Native (basic_ffc_update_stub): cmd_handle->driver_handle: %p", cmd_handle->driver_handle);
    LOGD("Native (basic_ffc_update_stub): cmd_handle->polling_time: %u", cmd_handle->polling_time);

    if (cmd_handle->write_func == NULL) {
        LOGE("Native (basic_ffc_update_stub): cmd_handle->write_func is NULL.");
        return -2;
    }

    LOGD("Native (basic_ffc_update_stub): Attempting to call cmd_handle->write_func (iruvc_usb_data_write_stub)...", cmd_handle->write_func);
    uint8_t ffc_dummy_payload[] = {0xFF, 0xCC}; 
    // Pass pointer to polling_time as usb_cmd_param, mimicking standard_cmd_write
    int write_ret = cmd_handle->write_func(cmd_handle->driver_handle, &cmd_handle->polling_time, ffc_dummy_payload, sizeof(ffc_dummy_payload));
    
    if (write_ret == 0) {
        LOGD("Native (basic_ffc_update_stub): call to cmd_handle->write_func SUCCEEDED.");
        return IRLIB_SUCCESS; 
    } else {
        LOGE("Native (basic_ffc_update_stub): call to cmd_handle->write_func FAILED with %d.", write_ret);
        return write_ret; 
    }
}

// JNI function to test calling the basic_ffc_update_stub
JNIEXPORT jint JNICALL
Java_com_example_mini2_1app_MainActivity_sendFfcCommandNativeStubbed(JNIEnv *env, jobject thiz, jint fileDescriptor) {
    libusb_context *ctx = NULL;
    libusb_device_handle *devh_from_fd = NULL;
    int r = 0;
    IrlibError_e ffc_status = -99; 

    MySdk_IruvcHandle_t sdk_iruvc_handle;    // Use new struct
    MySdk_uvc_device_handle_t sdk_uvc_devh;  // Sub-struct
    MySdk_IrcmdHandle_t sdk_ircmd_handle;    // Use new struct

    LOGD("Native (sendFfcCommandNativeStubbed): Called with FD: %d", fileDescriptor);

    r = libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, NULL);
    if (r != LIBUSB_SUCCESS) {
        LOGE("Native (sendFfcCommandNativeStubbed): libusb_set_option LIBUSB_OPTION_NO_DEVICE_DISCOVERY failed: %d (%s)", r, libusb_error_name(r));
    }
    r = libusb_init(&ctx);
    if (r < 0) {
        LOGE("Native (sendFfcCommandNativeStubbed): libusb_init failed: %d (%s)", r, libusb_error_name(r));
        return r; 
    }
    LOGD("Native (sendFfcCommandNativeStubbed): libusb_init success, ctx: %p", ctx);

    r = libusb_wrap_sys_device(ctx, (intptr_t)fileDescriptor, &devh_from_fd);
    if (r < 0 || devh_from_fd == NULL) {
        LOGE("Native (sendFfcCommandNativeStubbed): libusb_wrap_sys_device failed: %d (%s), handle: %p", r, libusb_error_name(r), devh_from_fd);
        if(ctx) libusb_exit(ctx);
        return r == 0 ? -100 : r; 
    }
    LOGD("Native (sendFfcCommandNativeStubbed): libusb_wrap_sys_device success, devh_from_fd: %p", devh_from_fd);

    memset(&sdk_uvc_devh, 0, sizeof(MySdk_uvc_device_handle_t));
    sdk_uvc_devh.usb_devh = devh_from_fd;
    
    memset(&sdk_iruvc_handle, 0, sizeof(MySdk_IruvcHandle_t));
    sdk_iruvc_handle.devh = &sdk_uvc_devh; 
    sdk_iruvc_handle.max_delay_ms = 2000;
    
    LOGD("Native (sendFfcCommandNativeStubbed): Initializing mutex for MySdk_IruvcHandle_t...");
    if (pthread_mutex_init(&sdk_iruvc_handle.mtx, NULL) != 0) { 
        LOGE("Native (sendFfcCommandNativeStubbed): pthread_mutex_init failed");
        libusb_close(devh_from_fd);
        libusb_exit(ctx);
        return -101; 
    }
    LOGD("Native (sendFfcCommandNativeStubbed): MySdk_IruvcHandle_t constructed. devh->usb_devh: %p", sdk_iruvc_handle.devh->usb_devh);

    LOGD("Native (sendFfcCommandNativeStubbed): Constructing MySdk_IrcmdHandle_t...");
    memset(&sdk_ircmd_handle, 0, sizeof(MySdk_IrcmdHandle_t));
    sdk_ircmd_handle.driver_handle = &sdk_iruvc_handle; 
    sdk_ircmd_handle.write_func = iruvc_usb_data_write_stub; 
    sdk_ircmd_handle.polling_time = 2000; 
    // Other fields like driver_type, slave_id are 0 by memset.
    
    LOGD("Native (sendFfcCommandNativeStubbed): MySdk_IrcmdHandle_t constructed.");
    LOGD("  driver_handle: %p, write_func: %p", sdk_ircmd_handle.driver_handle, sdk_ircmd_handle.write_func);

    LOGD("Native (sendFfcCommandNativeStubbed): Calling basic_ffc_update_stub...");
    ffc_status = basic_ffc_update_stub((void*)&sdk_ircmd_handle); 
    LOGD("Native (sendFfcCommandNativeStubbed): basic_ffc_update_stub result: %d", ffc_status);

    // Cleanup
    LOGD("Native (sendFfcCommandNativeStubbed): Performing cleanup...");
    pthread_mutex_destroy(&sdk_iruvc_handle.mtx);
    
    if (devh_from_fd) {
        libusb_close(devh_from_fd); 
    }
    if (ctx) {
        libusb_exit(ctx);
    }
    LOGD("Native (sendFfcCommandNativeStubbed): Cleanup complete. Returning status: %d", ffc_status);

    return ffc_status;
}

// New JNI function to call the ACTUAL basic_ffc_update and iruvc_usb_data_write
JNIEXPORT jint JNICALL
Java_com_example_mini2_1app_MainActivity_sendFfcCommandNativeActual(JNIEnv *env, jobject thiz, jint fileDescriptor) {
    libusb_context *ctx = NULL;
    libusb_device_handle *devh_from_fd = NULL;
    int r = 0;
    IrlibError_e ffc_status = -99; 

    MySdk_IruvcHandle_t sdk_iruvc_handle;         // Using new SDK-like struct
    MySdk_uvc_device_handle_t sdk_uvc_devh;       // The sub-struct for devh
    MySdk_IrcmdHandle_t sdk_ircmd_handle;         // Using new SDK-like struct

    LOGD("Native (sendFfcCommandNativeActual): Called with FD: %d", fileDescriptor);

    r = libusb_set_option(NULL, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, NULL);
    if (r != LIBUSB_SUCCESS) {
        LOGE("Native (sendFfcCommandNativeActual): libusb_set_option LIBUSB_OPTION_NO_DEVICE_DISCOVERY failed: %d (%s)", r, libusb_error_name(r));
    }
    r = libusb_init(&ctx);
    if (r < 0) {
        LOGE("Native (sendFfcCommandNativeActual): libusb_init failed: %d (%s)", r, libusb_error_name(r));
        return r;
    }
    LOGD("Native (sendFfcCommandNativeActual): libusb_init success, ctx: %p", ctx);

    r = libusb_wrap_sys_device(ctx, (intptr_t)fileDescriptor, &devh_from_fd);
    if (r < 0 || devh_from_fd == NULL) {
        LOGE("Native (sendFfcCommandNativeActual): libusb_wrap_sys_device failed: %d (%s), handle: %p", r, libusb_error_name(r), devh_from_fd);
        if(ctx) libusb_exit(ctx);
        return r == 0 ? -100 : r;
    }
    LOGD("Native (sendFfcCommandNativeActual): libusb_wrap_sys_device success, devh_from_fd: %p", devh_from_fd);

    // Initialize MySdk_uvc_device_handle_t
    memset(&sdk_uvc_devh, 0, sizeof(MySdk_uvc_device_handle_t));
    // dev, prev, next are NULL due to memset.
    sdk_uvc_devh.usb_devh = devh_from_fd; // Store the libusb handle at the correct offset (24)

    // Initialize MySdk_IruvcHandle_t
    memset(&sdk_iruvc_handle, 0, sizeof(MySdk_IruvcHandle_t));
    sdk_iruvc_handle.devh = &sdk_uvc_devh; // Point to our uvc_device_handle wrapper
    // Initialize other MySdk_IruvcHandle_t fields as per decompiled iruvc_camera_handle_create
    sdk_iruvc_handle.ctx = NULL; // We are not creating a full uvc_context_t
    sdk_iruvc_handle.dev = NULL; // We are not creating a full uvc_device_t
    sdk_iruvc_handle.ctrl = NULL; // Not managing stream controls here
    sdk_iruvc_handle.cur_dev_cfg = NULL; // Not managing device configs here
    sdk_iruvc_handle.raw_frame1 = NULL;
    sdk_iruvc_handle.same_index = 0;
    sdk_iruvc_handle.got_frame_cnt = 0;
    sdk_iruvc_handle.max_delay_ms = 2000; // Matches iruvc_camera_handle_create

    LOGD("Native (sendFfcCommandNativeActual): Initializing mutex for MySdk_IruvcHandle_t (at offset %ld)...", (long)((uintptr_t)&sdk_iruvc_handle.mtx - (uintptr_t)&sdk_iruvc_handle));
    if (pthread_mutex_init(&sdk_iruvc_handle.mtx, NULL) != 0) {
        LOGE("Native (sendFfcCommandNativeActual): pthread_mutex_init failed");
        libusb_close(devh_from_fd);
        libusb_exit(ctx);
        return -101; 
    }
    LOGD("Native (sendFfcCommandNativeActual): MySdk_IruvcHandle_t constructed. devh->usb_devh: %p", sdk_iruvc_handle.devh ? sdk_iruvc_handle.devh->usb_devh : NULL);

    // Initialize MySdk_IrcmdHandle_t
    LOGD("Native (sendFfcCommandNativeActual): Constructing MySdk_IrcmdHandle_t...");
    memset(&sdk_ircmd_handle, 0, sizeof(MySdk_IrcmdHandle_t));
    sdk_ircmd_handle.driver_handle = &sdk_iruvc_handle; 
    sdk_ircmd_handle.write_func = iruvc_usb_data_write; // <<< Using ACTUAL SDK function
    sdk_ircmd_handle.polling_time = 2000; 
    // Other function pointers (read_func, etc.) are NULL from memset
    sdk_ircmd_handle.driver_type = 0; // As per ircmd_create_handle
    sdk_ircmd_handle.slave_id = 0; // Default
    sdk_ircmd_handle.upgrade_callback = NULL;
    sdk_ircmd_handle.upgrade_priv_data = NULL;
    // Ensure other relevant fields from ircmd_create_handle are set if necessary
    // For now, focusing on the direct path for basic_ffc_update
    
    LOGD("Native (sendFfcCommandNativeActual): MySdk_IrcmdHandle_t constructed.");
    LOGD("  driver_handle (offset %ld): %p", (long)((uintptr_t)&sdk_ircmd_handle.driver_handle - (uintptr_t)&sdk_ircmd_handle), sdk_ircmd_handle.driver_handle);
    LOGD("  write_func (offset %ld) (actual): %p", (long)((uintptr_t)&sdk_ircmd_handle.write_func - (uintptr_t)&sdk_ircmd_handle), sdk_ircmd_handle.write_func);
    LOGD("  polling_time (offset %ld): %u", (long)((uintptr_t)&sdk_ircmd_handle.polling_time - (uintptr_t)&sdk_ircmd_handle), sdk_ircmd_handle.polling_time);

    LOGD("Native (sendFfcCommandNativeActual): Calling ACTUAL basic_ffc_update...");
    ffc_status = basic_ffc_update((void*)&sdk_ircmd_handle); 
    LOGD("Native (sendFfcCommandNativeActual): ACTUAL basic_ffc_update result: %d", ffc_status);

    // Cleanup
    LOGD("Native (sendFfcCommandNativeActual): Performing cleanup...");
    pthread_mutex_destroy(&sdk_iruvc_handle.mtx);

    if (devh_from_fd) {
        libusb_close(devh_from_fd);
    }
    if (ctx) {
        libusb_exit(ctx);
    }
    LOGD("Native (sendFfcCommandNativeActual): Cleanup complete. Returning status: %d", ffc_status);

    return ffc_status;
}


