#include "ircmd_manager.h"
#include <libusb.h>
#include <pthread.h>
#include <thread>
#include "libircmd.h"
#include "error.h"

// Forward declaration if the type isn't available from headers
#ifndef IrcmdHandle_t
typedef struct _IrcmdHandle_t IrcmdHandle_t;
#endif

// Forward declaration of write function stub
static int iruvc_usb_data_write_stub(void* driver_handle, void* usb_cmd_param, uint8_t* data, int len);
extern "C" int iruvc_usb_data_write(void* iruvc_handle_ptr, void* usb_cmd_param, uint8_t* data, uint16_t len);
extern "C" IrlibError_e basic_ffc_update(IrcmdHandle_t* handle);
// FFC command structure
struct FFCCommand {
    uint8_t cmd_type;      // Command type (0x01 for FFC)
    uint8_t cmd_id;        // Command ID (0x01 for update)
    uint16_t data_len;     // Data length (0 for FFC update)
    uint8_t checksum;      // Checksum
} __attribute__((packed));

IrcmdManager::IrcmdManager()
    : is_initialized_(false)
    , last_error_(0)
    , ircmd_handle_(nullptr) {
    IRCMD_LOGI("IrcmdManager constructor called");
}

IrcmdManager::~IrcmdManager() {
    IRCMD_LOGI("IrcmdManager destructor called");
    cleanup();
}

bool IrcmdManager::init(int fileDescriptor) {
    std::lock_guard<std::mutex> lock(mutex_);
    
    if (is_initialized_) {
        IRCMD_LOGW("IrcmdManager already initialized");
        return true;
    }

    IRCMD_LOGI("Initializing IrcmdManager with file descriptor %d", fileDescriptor);
    
    // Log structure sizes for verification
    IRCMD_LOGI("Structure sizes: MySdk_uvc_device_handle_t=%zu, MySdk_IruvcHandle_t=%zu, MySdk_IrcmdHandle_t=%zu",
               sizeof(MySdk_uvc_device_handle_t),
               sizeof(MySdk_IruvcHandle_t),
               sizeof(MySdk_IrcmdHandle_t));
    
    // Initialize libusb with the NO_DEVICE_DISCOVERY option
    libusb_context* usb_ctx = nullptr;
    int res_option = libusb_set_option(nullptr, LIBUSB_OPTION_NO_DEVICE_DISCOVERY, nullptr);
    if (res_option != LIBUSB_SUCCESS) {
        IRCMD_LOGW("Failed to set libusb global option NO_DEVICE_DISCOVERY: %s. Continuing...", 
                   libusb_error_name(res_option));
    } else {
        IRCMD_LOGI("Successfully set libusb NO_DEVICE_DISCOVERY option");
    }

    int res_libusb = libusb_init(&usb_ctx);
    if (res_libusb != LIBUSB_SUCCESS) {
        setError(res_libusb);
        IRCMD_LOGE("Failed to initialize libusb context: %s", libusb_error_name(res_libusb));
        return false;
    }
    IRCMD_LOGI("Successfully initialized libusb context: %p", usb_ctx);

    // Wrap the file descriptor into a libusb device handle
    libusb_device_handle* usb_devh = nullptr;
    int res_wrap = libusb_wrap_sys_device(usb_ctx, fileDescriptor, &usb_devh);
    if (res_wrap != LIBUSB_SUCCESS) {
        setError(res_wrap);
        IRCMD_LOGE("Failed to wrap file descriptor: %s", libusb_error_name(res_wrap));
        libusb_exit(usb_ctx);
        return false;
    }
    IRCMD_LOGI("Successfully wrapped file descriptor into libusb device handle: %p", usb_devh);

    // Get the device from the handle
    libusb_device* usb_dev = libusb_get_device(usb_devh);
    if (!usb_dev) {
        setError(LIBUSB_ERROR_NO_DEVICE);
        IRCMD_LOGE("Failed to get libusb device from handle");
        libusb_close(usb_devh);
        libusb_exit(usb_ctx);
        return false;
    }
    IRCMD_LOGI("Successfully got libusb device: %p", usb_dev);

    // Get device descriptor to verify it's our camera
    libusb_device_descriptor dev_desc;
    int res_desc = libusb_get_device_descriptor(usb_dev, &dev_desc);
    if (res_desc != LIBUSB_SUCCESS) {
        setError(res_desc);
        IRCMD_LOGE("Failed to get device descriptor: %s", libusb_error_name(res_desc));
        libusb_close(usb_devh);
        libusb_exit(usb_ctx);
        return false;
    }

    // Log device information
    IRCMD_LOGI("Device Information:");
    IRCMD_LOGI("  Vendor ID: 0x%04x", dev_desc.idVendor);
    IRCMD_LOGI("  Product ID: 0x%04x", dev_desc.idProduct);
    IRCMD_LOGI("  Device Class: %d", dev_desc.bDeviceClass);
    IRCMD_LOGI("  Device Subclass: %d", dev_desc.bDeviceSubClass);
    IRCMD_LOGI("  Number of Configurations: %d", dev_desc.bNumConfigurations);

    // Verify this is our camera
    if (dev_desc.idVendor != 0x3474 || dev_desc.idProduct != 0x43d1) {  // Thermal Camera Co.,Ltd IDs
        setError(LIBUSB_ERROR_NOT_SUPPORTED);
        IRCMD_LOGE("Unsupported device: vendor=0x%04x, product=0x%04x", 
                   dev_desc.idVendor, dev_desc.idProduct);
        libusb_close(usb_devh);
        libusb_exit(usb_ctx);
        return false;
    }
    IRCMD_LOGI("Device verified as Thermal Camera Co.,Ltd camera");

    // After getting the device descriptor, enumerate interfaces
    libusb_config_descriptor* config;
    int res_config = libusb_get_active_config_descriptor(usb_dev, &config);
    if (res_config == LIBUSB_SUCCESS) {
        IRCMD_LOGI("Device has %d interfaces", config->bNumInterfaces);
        
        for (int i = 0; i < config->bNumInterfaces; i++) {
            const libusb_interface* interface = &config->interface[i];
            IRCMD_LOGI("Interface %d has %d alternate settings", i, interface->num_altsetting);
            
            for (int j = 0; j < interface->num_altsetting; j++) {
                const libusb_interface_descriptor* if_desc = &interface->altsetting[j];
                IRCMD_LOGI("  Interface %d, Alt Setting %d:", i, j);
                IRCMD_LOGI("    Class: %d", if_desc->bInterfaceClass);
                IRCMD_LOGI("    Subclass: %d", if_desc->bInterfaceSubClass);
                IRCMD_LOGI("    Protocol: %d", if_desc->bInterfaceProtocol);
                IRCMD_LOGI("    Number of endpoints: %d", if_desc->bNumEndpoints);
                
                for (int k = 0; k < if_desc->bNumEndpoints; k++) {
                    const libusb_endpoint_descriptor* ep = &if_desc->endpoint[k];
                    IRCMD_LOGI("      Endpoint %d:", k);
                    IRCMD_LOGI("        Address: 0x%02x", ep->bEndpointAddress);
                    IRCMD_LOGI("        Attributes: 0x%02x", ep->bmAttributes);
                    IRCMD_LOGI("        Max packet size: %d", ep->wMaxPacketSize);
                }
            }
        }
        libusb_free_config_descriptor(config);
    }

    // Create IRCMD handle structures
    IRCMD_LOGI("Creating handle structures...");
    ircmd_handle_ = new MySdk_IrcmdHandle_t();
    if (!ircmd_handle_) {
        IRCMD_LOGE("Failed to allocate IRCMD handle");
        libusb_close(usb_devh);
        libusb_exit(usb_ctx);
        return false;
    }
    IRCMD_LOGI("Created IRCMD handle: %p", ircmd_handle_);

    // Create and initialize IRUVC handle
    MySdk_IruvcHandle_t* iruvc_handle = new MySdk_IruvcHandle_t();
    if (!iruvc_handle) {
        IRCMD_LOGE("Failed to allocate IRUVC handle");
        delete ircmd_handle_;
        ircmd_handle_ = nullptr;
        libusb_close(usb_devh);
        libusb_exit(usb_ctx);
        return false;
    }
    IRCMD_LOGI("Created IRUVC handle: %p", iruvc_handle);

    // Create and initialize UVC device handle
    MySdk_uvc_device_handle_t* uvc_devh = new MySdk_uvc_device_handle_t();
    if (!uvc_devh) {
        IRCMD_LOGE("Failed to allocate UVC device handle");
        delete iruvc_handle;
        delete ircmd_handle_;
        ircmd_handle_ = nullptr;
        libusb_close(usb_devh);
        libusb_exit(usb_ctx);
        return false;
    }
    IRCMD_LOGI("Created UVC device handle: %p", uvc_devh);

    // Initialize UVC device handle
    memset(uvc_devh, 0, sizeof(MySdk_uvc_device_handle_t));
    uvc_devh->usb_devh = usb_devh;
    IRCMD_LOGI("Initialized UVC device handle with libusb handle: %p", uvc_devh->usb_devh);

    // Initialize IRUVC handle
    memset(iruvc_handle, 0, sizeof(MySdk_IruvcHandle_t));
    iruvc_handle->devh = uvc_devh;
    iruvc_handle->max_delay_ms = 2000;
    IRCMD_LOGI("Initialized IRUVC handle with UVC device handle: %p", iruvc_handle->devh);

    // Initialize mutex for IRUVC handle
    IRCMD_LOGI("Initializing mutex for IRUVC handle...");
    int res_mutex = pthread_mutex_init(&iruvc_handle->mtx, nullptr);
    if (res_mutex != 0) {
        IRCMD_LOGE("Failed to initialize mutex: %d", res_mutex);
        delete uvc_devh;
        delete iruvc_handle;
        delete ircmd_handle_;
        ircmd_handle_ = nullptr;
        libusb_close(usb_devh);
        libusb_exit(usb_ctx);
        return false;
    }
    IRCMD_LOGI("Successfully initialized mutex for IRUVC handle");

    // Initialize IRCMD handle
    memset(ircmd_handle_, 0, sizeof(MySdk_IrcmdHandle_t));
    ircmd_handle_->driver_handle = iruvc_handle;
    ircmd_handle_->write_func = iruvc_usb_data_write;
    ircmd_handle_->polling_time = 2000;
    ircmd_handle_->driver_type = 0;
    ircmd_handle_->slave_id = 0;
    IRCMD_LOGI("Initialized IRCMD handle with:");
    IRCMD_LOGI("  - driver_handle: %p", ircmd_handle_->driver_handle);
    IRCMD_LOGI("  - write_func: %p", (void*)ircmd_handle_->write_func);
    IRCMD_LOGI("  - polling_time: %u", ircmd_handle_->polling_time);

    // Store the USB context and device handle
    usb_ctx_ = usb_ctx;
    usb_devh_ = usb_devh;
    
    is_initialized_ = true;
    IRCMD_LOGI("IrcmdManager initialized successfully");
    return true;
}

void IrcmdManager::cleanup() {
    std::lock_guard<std::mutex> lock(mutex_);
    
    if (!is_initialized_) {
        IRCMD_LOGD("IrcmdManager not initialized, nothing to cleanup");
        return;
    }

    IRCMD_LOGI("Cleaning up IrcmdManager");
    
    if (ircmd_handle_) {
        if (ircmd_handle_->driver_handle) {
            // Destroy mutex
            pthread_mutex_destroy(&ircmd_handle_->driver_handle->mtx);
            
            // Clean up UVC device handle
            if (ircmd_handle_->driver_handle->devh) {
                delete ircmd_handle_->driver_handle->devh;
            }
            
            // Clean up IRUVC handle
            delete ircmd_handle_->driver_handle;
        }
        
        // Clean up IRCMD handle
        delete ircmd_handle_;
        ircmd_handle_ = nullptr;
    }
    
    if (usb_devh_) {
        libusb_close(usb_devh_);
        usb_devh_ = nullptr;
    }
    
    if (usb_ctx_) {
        libusb_exit(usb_ctx_);
        usb_ctx_ = nullptr;
    }
    
    is_initialized_ = false;
    last_error_ = 0;
}

void IrcmdManager::setError(int error_code) {
    last_error_ = error_code;
    IRCMD_LOGE("Error set: %d (%s)", error_code, getLastErrorMessage());
}

const char* IrcmdManager::getLastErrorMessage() const {
    if (last_error_ == 0) {
        return "No error";
    }
    return libusb_error_name(last_error_);
}

// Modify the write stub to try different interfaces
static int iruvc_usb_data_write_stub(void* driver_handle, void* usb_cmd_param, uint8_t* data, int len) {
    if (!driver_handle || !data || len <= 0) {
        return -1;
    }

    MySdk_IruvcHandle_t* iruvc_handle = static_cast<MySdk_IruvcHandle_t*>(driver_handle);
    if (!iruvc_handle->devh || !iruvc_handle->devh->usb_devh) {
        return -1;
    }

    // Lock the mutex for thread safety
    pthread_mutex_lock(&iruvc_handle->mtx);
    
    // Try interface 0 first (control interface)
    int res = libusb_control_transfer(
        iruvc_handle->devh->usb_devh,
        LIBUSB_ENDPOINT_OUT | LIBUSB_REQUEST_TYPE_VENDOR | LIBUSB_RECIPIENT_DEVICE,
        0x01,  // Request
        0,     // Value
        0,     // Index (interface 0)
        data,
        len,
        iruvc_handle->max_delay_ms
    );
    
    if (res < 0) {
        IRCMD_LOGE("Control transfer failed on interface 0: %s", libusb_error_name(res));
        
        // Try interface 1 if interface 0 fails
        res = libusb_control_transfer(
            iruvc_handle->devh->usb_devh,
            LIBUSB_ENDPOINT_OUT | LIBUSB_REQUEST_TYPE_VENDOR | LIBUSB_RECIPIENT_DEVICE,
            0x01,  // Request
            0,     // Value
            1,     // Index (interface 1)
            data,
            len,
            iruvc_handle->max_delay_ms
        );
        
        if (res < 0) {
            IRCMD_LOGE("Control transfer failed on interface 1: %s", libusb_error_name(res));
        }
    }
    
    pthread_mutex_unlock(&iruvc_handle->mtx);
    
    return res;
}

int IrcmdManager::performFFC() {
    std::lock_guard<std::mutex> lock(mutex_);
    
    if (!is_initialized_ || !ircmd_handle_) {
        IRCMD_LOGE("Cannot perform FFC: IrcmdManager not initialized");
        return -2;  // Using raw value instead of enum
    }

    IRCMD_LOGI("Sending FFC command via basic_ffc_update");
    // Cast our handle type to what the SDK expects
    int result = basic_ffc_update(reinterpret_cast<IrcmdHandle_t*>(ircmd_handle_));
    
    if (result != 0) {  // 0 is success
        IRCMD_LOGE("FFC command failed: %d", result);
        setError(result);
    } else {
        IRCMD_LOGI("FFC command completed successfully");
    }
    
    return result;
} 