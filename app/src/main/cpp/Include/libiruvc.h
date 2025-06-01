#ifndef _LIBIRUVC_H_
#define _LIBIRUVC_H_
#ifdef __cplusplus
extern "C" {
#endif

#include<stdint.h>
#include"libircam.h"

/**
* @file
*
* @brief Libiruvc library header file
*
*/

typedef struct _IruvcHandle_t IruvcHandle_t;

#define FORMAT_YUY2 "YUY2"
#define FORMAT_MJPEG "MJPEG"

#define FPS_CNT_MAX 32

#if defined(_WIN32)
#define DLLEXPORT __declspec(dllexport)
#elif defined(linux) || defined(unix)
#define DLLEXPORT
#endif


/**
* @brief Device's configuration
*/
typedef struct {
    unsigned int    pid;	///< pid
    unsigned int    vid;	///< vid
    char* name;	            ///< device name
}DevCfg_t;

/**
* @brief User's callback function and parameters
*/
typedef struct {
    IruvcHandle_t* iruvc_handle;	///< libiruvc's handle
    void* usr_func;					///< user's function
    void* usr_param;				///< parameters of user's function
}UserCallback_t;


/**
* @brief Control camera side's preview
*/
typedef enum {
    CLOSE_CAM_SIDE_PREVIEW = 0,	///< close camera side's preview
    KEEP_CAM_SIDE_PREVIEW = 1	///< keep camera side's preview
}cam_side_preview_ctl;

/**
* @brief Device's configuration
*/
typedef struct {
    unsigned int    pid;	///< pid
    unsigned int    vid;	///< vid
    unsigned int    same_idx;	///< vid
}IruvcDevParam_t;

/**
* @brief Camera's one stream parameter
*/
typedef struct {
    char* format;					///< format
    unsigned int	width;					///< resolution width
    unsigned int    height;					///< resolution height
    unsigned int	frame_size;				///< frame byte size
    unsigned int    fps;					///< frame per second
    unsigned int	timeout_ms_delay;		///< frame get's timeout timer(ms)
}CameraParam_t;

typedef struct
{
    CameraParam_t camera_param;
    UserCallback_t usr_callback;
    uint8_t reserved[48];
}IruvcCamStreamParams_t;


typedef struct
{
    cam_side_preview_ctl stop_mode;
    uint8_t reserved[60];
}IruvcStreamStopParams_t;


typedef struct
{
    int reserved[32];
}IruvcFrameGetParams_t;

/**
* @brief Camera's supported stream information
*/
typedef struct
{
    char* format;				///< format
    unsigned int	width;				///< resolution width
    unsigned int	height;				///< resolution height
    unsigned int	frame_size;			///< frame byte size
    unsigned int	fps[FPS_CNT_MAX];	///< frame per second
}CameraStreamInfo_t;

typedef struct
{
    int reserved[64];
} UvcConDevParam_t;

/**
    * @brief Log message levels
    */
typedef enum {
    /// all debug&error infomation
    IRUVC_LOG_DEBUG = 0,
    /// all error infomation
    IRUVC_LOG_ERROR = 1,
    /// no print infomation(default)
    IRUVC_LOG_NO_PRINT = 2
}IruvcLogLevel_t;

/**
    * @brief Get libiruvc library's version
    *
    * @return version's string
    */
DLLEXPORT const char* iruvc_version(void);

/**
    * @brief Get libiruvc library's version number
    *
    * @return version number's string
    */
DLLEXPORT const char* iruvc_version_number(void);

/**
    * @brief Register log print via log_level
    *
    * @param[in] log_level debug information level
    * @param[in] callback the callback function of iruvc
    *
    */
DLLEXPORT void iruvc_log_register(IruvcLogLevel_t log_level, void* (*callback)(void* callback_data, void* priv_data), void* priv_data);


DLLEXPORT IruvcHandle_t* iruvc_camera_handle_create(IrVideoHandle_t* ir_video_handle);
DLLEXPORT IrlibError_e iruvc_camera_handle_create_with_exist_instance(IrVideoHandle_t* ir_video_handle, IruvcHandle_t* iruvc_handle);
DLLEXPORT IrlibError_e iruvc_camera_handle_delete(IruvcHandle_t* iruvc_handle);
DLLEXPORT IrlibError_e iruvc_camera_open(IruvcHandle_t* iruvc_handle, IruvcDevParam_t* dev_param);
DLLEXPORT IrlibError_e iruvc_camera_init(IruvcHandle_t* iruvc_handle, IruvcDevParam_t* params);
DLLEXPORT IrlibError_e iruvc_camera_start_stream(IruvcHandle_t* iruvc_handle, IruvcCamStreamParams_t* params);
DLLEXPORT IrlibError_e iruvc_camera_frame_get(IruvcHandle_t* iruvc_handle, IruvcFrameGetParams_t* params, uint8_t* frame_data, int len);
DLLEXPORT IrlibError_e iruvc_camera_stop_stream(IruvcHandle_t* iruvc_handle, IruvcStreamStopParams_t* params);
DLLEXPORT IrlibError_e iruvc_camera_release(IruvcHandle_t* iruvc_handle, IruvcDevParam_t* params);
DLLEXPORT IrlibError_e iruvc_camera_close(IruvcHandle_t* iruvc_handle);


DLLEXPORT IruvcHandle_t* iruvc_i2c_usb_handle_create(IrControlHandle_t* ir_control_handle);
DLLEXPORT IrlibError_e iruvc_i2c_usb_handle_create_with_exist_instance(IrControlHandle_t* ir_control_handle, IruvcHandle_t* iruvc_handle);
DLLEXPORT IrlibError_e iruvc_i2c_usb_device_open(IruvcHandle_t* iruvc_handle, IruvcDevParam_t* dev_param);
DLLEXPORT IrlibError_e iruvc_i2c_usb_device_init(IruvcHandle_t* iruvc_handle, UvcConDevParam_t* param);
DLLEXPORT IrlibError_e iruvc_i2c_usb_data_read(IruvcHandle_t* iruvc_handle, void* i2c_usb_cmd_param, uint8_t* data, uint16_t len);
DLLEXPORT IrlibError_e iruvc_i2c_usb_data_write(IruvcHandle_t* iruvc_handle, void* i2c_usb_cmd_param, uint8_t* data, uint16_t len);
DLLEXPORT IrlibError_e iruvc_i2c_usb_device_release(IruvcHandle_t* iruvc_handle, UvcConDevParam_t* param);
DLLEXPORT IrlibError_e iruvc_i2c_usb_device_close(IruvcHandle_t* iruvc_handle);
DLLEXPORT IrlibError_e iruvc_i2c_usb_handle_delete(IruvcHandle_t* iruvc_handle);
DLLEXPORT IrlibError_e iri2c_usb_firmware_download(IruvcHandle_t* iruvc_handle, char* file_name, uint8_t* data, \
    uint32_t file_len, void* (*callback)(void* callback_data));
DLLEXPORT IrlibError_e iruvc_i2c_usb_command_channel_get(IruvcHandle_t* uart_handle, int* command_channel_type);

DLLEXPORT IruvcHandle_t* iruvc_usb_handle_create(IrControlHandle_t* ir_control_handle);
DLLEXPORT IrlibError_e iruvc_usb_handle_create_with_exist_instance(IrControlHandle_t* ir_control_handle, IruvcHandle_t* iruvc_handle);
DLLEXPORT IrlibError_e iruvc_usb_device_open(IruvcHandle_t* iruvc_handle, IruvcDevParam_t* dev_param);
DLLEXPORT IrlibError_e iruvc_usb_device_init(IruvcHandle_t* iruvc_handle, UvcConDevParam_t* param);
DLLEXPORT IrlibError_e iruvc_usb_data_read(IruvcHandle_t* iruvc_handle, void* usb_cmd_param, uint8_t* data, uint16_t len);
DLLEXPORT IrlibError_e iruvc_usb_data_write(IruvcHandle_t* iruvc_handle, void* usb_cmd_param, uint8_t* data, uint16_t len);
DLLEXPORT IrlibError_e iruvc_data_write_without_read_return_status(IruvcHandle_t* iruvc_handle, void* usb_cmd_param, uint8_t* data, uint16_t len);
DLLEXPORT IrlibError_e iruvc_usb_device_release(IruvcHandle_t* iruvc_handle, UvcConDevParam_t* param);
DLLEXPORT IrlibError_e iruvc_usb_device_close(IruvcHandle_t* iruvc_handle);
DLLEXPORT IrlibError_e iruvc_usb_handle_delete(IruvcHandle_t* iruvc_handle);
DLLEXPORT IrlibError_e iruvc_usb_command_channel_get(IruvcHandle_t* uart_handle, int* command_channel_type);

#ifdef __cplusplus
}
#endif

#endif