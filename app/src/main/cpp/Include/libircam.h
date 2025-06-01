#ifndef _LIBIRCAM_H_
#define _LIBIRCAM_H_
#define _CRT_SECURE_NO_WARNINGS
#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>
#include <stdio.h>
#include <stdarg.h>
#include <stdlib.h>
#include <string.h>
#include "error.h"

#if defined(_WIN32)
#define DLLEXPORT __declspec(dllexport)
#elif defined(linux) || defined(unix)
#define DLLEXPORT
#endif

    /**
    * @brief Log level definition in libircam library
    */
    typedef enum {
        IRCAM_LOG_DEBUG = 0,		///< print debug and error information
        IRCAM_LOG_ERROR = 1,		///< only print error information
        IRCAM_LOG_NO_PRINT = 2,	///< don't print debug and error information
    }ircam_log_level_t;

    /**
    * @brief the kind of command channel
    **/
    typedef enum {
        COMMAND_CHANNEL_IDLE = 0,
        UVC_USB_COMMAND_CHANNEL = 1,
        V4L2_USB_COMMAND_CHANNEL = 2,
        UART_COMMAND_CHANNEL = 3,
        UVC_USB_I2C_COMMAND_CHANNEL = 4,
        V4L2_USB_I2C_COMMAND_CHANNEL = 5,
        I2C_COMMAND_CHANNEL = 6,
    }command_channel_e;

    typedef struct
    {
        void* ir_video_handle;
        int (*ir_video_open)(void* device_handle, void* params);
        int (*ir_video_init)(void* device_handle, void* params);
        int (*ir_video_start_stream)(void* device_handle, void* params);
        int (*ir_video_frame_get)(void* device_handle, void* params, uint8_t* frame_data, int len);
        int (*ir_video_stop_stream)(void* device_handle, void* params);
        int (*ir_video_release)(void* device_handle, void* params);
        int (*ir_video_close)(void* device_handle);
    } IrVideoHandle_t;

    typedef struct
    {
        void* ir_control_handle;
        int (*ir_control_open)(void* device_handle, void* params);
        int (*ir_control_init)(void* device_handle, void* params);
        int (*ir_control_read)(void* device_handle, void* cmd_param, uint8_t* data, uint32_t len);
        int (*ir_control_write)(void* device_handle, void* cmd_param, uint8_t* data, uint32_t len);
        int (*ir_control_write_without_read_return_status)(void* device_handle, void* cmd_param, uint8_t* data, uint32_t len);
        int (*ir_control_release)(void* device_handle, void* params);
        int (*ir_control_close)(void* device_handle);
        int (*ir_control_firmware_download)(void* device_handle, char* file_name, uint8_t* data, uint32_t file_len, \
            void* (*callback)(void* callback_data, void* priv_data), void* priv_data);
        int (*ir_control_detect_device_status)(void* device_handle, void* params, int* status);
        int (*ir_control_command_channel_type_get)(void* device_handle, int* command_channel_type);
        int (*ir_control_bootloader_download)(void* device_handle, char* file_name, uint8_t* data, uint32_t file_len, \
            void* (*callback)(void* callback_data, void* priv_data), void* priv_data);
    } IrControlHandle_t;

DLLEXPORT void ircam_log_register(ircam_log_level_t log_level, void* (*callback)(void* callback_data, void* priv_data), void* priv_data);

DLLEXPORT const char* ircam_version(void);

DLLEXPORT const char* ircam_version_number(void);

DLLEXPORT IrlibError_e ir_video_handle_create(IrVideoHandle_t** ir_video_handle);

DLLEXPORT IrlibError_e ir_video_handle_delete(IrVideoHandle_t** ir_video_handle);

DLLEXPORT IrlibError_e ir_control_handle_create(IrControlHandle_t** ir_control_handle);

DLLEXPORT IrlibError_e ir_control_handle_delete(IrControlHandle_t** ir_control_handle);



#ifdef __cplusplus
}
#endif

#endif