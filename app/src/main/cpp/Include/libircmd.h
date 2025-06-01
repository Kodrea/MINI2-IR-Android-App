#ifndef _LIBIRCMD_BASIC_H_
#define _LIBIRCMD_BASIC_H_
//用来控制尚未实现的接口不进行编译
#define COMMAND_IN_DEVELOPING

#ifdef __cplusplus
extern "C" {
#endif

#include <stdio.h>
#include <stdint.h>
#include <stdarg.h>
#include <math.h>
#include <stdlib.h>
#include <string.h>

#include "libircam.h"

#define DDE_MAX_LEVEL           100
#define SNR_MAX_LEVEL           100
#define AGC_MAX_LEVEL           100
#define YUV_MAX_FORMAT          3

#define TU_TA_MIN_LEVEL            233
#define TU_TA_MAX_LEVEL            373
#define TAU_EMS_MIN_LEVEL          1
#define TAU_EMS_MAX_LEVEL          16384

#if defined(_WIN32)
#define DLLEXPORT __declspec(dllexport)
#include "windows.h"
#elif defined(linux) || defined(unix)
#define DLLEXPORT
#include <unistd.h>
#endif

typedef struct _IrcmdHandle_t IrcmdHandle_t;

//======================================
//           common command
//======================================


/**
 * @brief Point struct, start from 0
 */
typedef struct {
    /// x position
    uint16_t x;
    /// y position
    uint16_t y;
}IrcmdPoint_t;


/**
 * @brief Rectangle struct, start from 1
 */
typedef struct {
    /// start point's position
    IrcmdPoint_t start_point;
    /// end point's position
    IrcmdPoint_t end_point;
}IrcmdRect_t;


/**
 * @brief Maximum and minimum temperature information struct
 */
typedef struct
{
    /// maximum temperature value,Unit:Celsius
    float max_temp;
    /// minimum temperature value,Unit:Celsius
    float min_temp;
    /// maximum temperature point's position
    IrcmdPoint_t max_temp_point;
    /// minimum temperature point's position
    IrcmdPoint_t min_temp_point;
}MaxMinTempInfo_t;


/**
 * @brief Temperature information struct of a line or a rectangle.
 */
typedef struct
{
    /// maximum temperature point's position,Unit:Celsius
    float ave_temp;
    /// Maximum and minimum temperature information struct
    MaxMinTempInfo_t max_min_temp_info;
}LineRectTempInfo_t;


/**
 * @brief Get libircmd library's version
 *
 * @return version's string
 */
DLLEXPORT const char* ircmd_version(void);


/**
 * @brief Get libircmd library's version number
 *
 * @return version number's string
 */
DLLEXPORT const char* ircmd_version_number(void);


/**
* @brief Log level definition in libircmd library
*/
typedef enum {
    IRCMD_LOG_DEBUG = 0,		///< print debug and error information
    IRCMD_LOG_ERROR = 1,		///< only print error information
    IRCMD_LOG_NO_PRINT = 2,		///< don't print debug and error information
}IrcmdLogLevel_e;


/**
 * @brief Register log print via log_level
 *
 * @param[in] log_level debug information level
 * @param[in] callback callback function for printing log messages
 * @param[in] priv_data the private parameter of callback function
 */
DLLEXPORT void ircmd_log_register(IrcmdLogLevel_e log_level, void* (*callback)(void* callback_data, void* priv_data), void* priv_data);


/**
 * @brief Create and initialize ircmd's handle
 *
 * @param[in] ir_control_handle ir_control's handle
 *
 * @return IrcmdHandle_t struct pointer
 */
DLLEXPORT IrcmdHandle_t* ircmd_create_handle(IrControlHandle_t* ir_control_handle);


/**
 * @brief Delete and release ircmd's handle
 *
 * @param[in] handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e ircmd_delete_handle(IrcmdHandle_t* handle);


/**
 * @brief Set single command polling time
 *
 * @param[in] handle ircmd's handle
 * @param[in] timeout_ms timeout time,uint:ms
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e vdcmd_set_polling_wait_time(IrcmdHandle_t* handle, uint16_t timeout_ms);


/**
 * @brief location of callback function calls
 */
typedef enum
{
    UPGRADE_PROCESS_CALLBACK = 0,
}callback_mode_e;


/**
 * @brief Init callback function
 *
 * @param[in] handle ircmd's handle
 * @param[in] mode location of callback function calls,see callback_mode_e
 * @param[in] callback the callback function of ircmd
 * @param[in] callback_data the parameter of callback function
 * @param[in] priv_data the private parameter of callback function
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e ircmd_init_callback_function(IrcmdHandle_t* handle, int mode, \
    void* (*callback)(void* callback_data, void* priv_data), void* priv_data);


/**
 * @brief Delete callback function
 *
 * @param[in] handle ircmd's handle
 * @param[in] mode location of callback function calls,see callback_mode_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e ircmd_delete_callback_function(IrcmdHandle_t* handle, int mode);


/**
 * @brief Get percentage of data transfer
 *
 * @param[in] handle ircmd's handle
 * @param[out] percentage percentage of data transfer
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e ircmd_get_percentage_of_data_transfer(IrcmdHandle_t* handle, float* percentage);

//================================================================
//           basic command means frequently used command
//           adv command means command that is not used often
//================================================================

//======================================
//           FFC command
//======================================


/**
 * @brief Manual shutter update
 *
 * @param[in] handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_ffc_update(IrcmdHandle_t* handle);


/**
 * @brief auto shutter status
 */
typedef enum
{
    BASIC_AUTO_FFC_DISABLE = 0,
    BASIC_AUTO_FFC_ENABLE = 1,
    /// in SEMI_DISALBE status, auto ffc parameters will be set as the semi default parameters by the firmware
    /// usually, auto_min_interval and auto_max_interval are set to longer time
    /// support for G1280s and CS640
    BASIC_AUTO_FFC_SEMI_DISABLE = 2,
}basic_auto_ffc_status_e;


/**
 * @brief Set auto shutter status
 *
 * @param[in] handle ircmd's handle
 * @param[in] ffc_status enable status of shutter,see basic_auto_ffc_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_auto_ffc_status_set(IrcmdHandle_t* handle, int ffc_status);


/**
 * @brief Get auto shutter status
 *
 * @param[in] handle ircmd's handle
 * @param[out] ffc_status enable status of ffc,see basic_auto_ffc_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_auto_ffc_status_get(IrcmdHandle_t* handle, int* ffc_status);


/**
 * @brief auto shutter parameters types
 */
typedef enum
{
    /// Vtemp's threshold to trigger shutter update in auto shutter.
    /// Do ffc update according to the temperature difference between latest update.
    /// unit:cnt(36cnt~=1c)
    BASIC_AUTO_TEMP_THRESHOLD = 0,
    /// Trigger auto shutter minimun interval,to prevent updates too frequently.
    /// unit:1s
    BASIC_AUTO_MIN_INTERVAL = 1,
    /// Trigger auto shutter maximun interval,shutter updates automatically at this time.
    /// unit:1s
    BASIC_AUTO_MAX_INTERVAL = 2,
}basic_auto_ffc_param_e;


/**
 * @brief Set auto FFC's parameter
 * if param_type is BASIC_AUTO_MAX_INTERVAL, value must more than 5
 * maximun interval must lower than minimun interval
 *
 * @param[in] handle ircmd's handle
 * @param[in] param_type auto FFC's parameter type,see basic_auto_ffc_param_e
 * @param[in] value auto FFC's parameter value
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_auto_ffc_current_params_set(IrcmdHandle_t* handle, int param_type, int value);


/**
 * @brief Get current auto FFC's parameter
 *
 * @param[in] handle ircmd's handle
 * @param[in] param_type auto FFC's parameter type,see basic_auto_ffc_param_e
 * @param[out] value auto FFC's parameter value
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_auto_ffc_current_params_get(IrcmdHandle_t* handle, int param_type, int* value);


/**
 * @brief attribute of params(the range of value and quantify step size)
 */
typedef struct {
    uint16_t max_value;
    uint16_t min_value;
    uint16_t step;
}param_attribute_t;


/**
 * @brief Get attribute of auto FFC's parameter
 *
 * @param[in] handle ircmd's handle
 * @param[in] param_type auto FFC's parameter type,see basic_ffc_auto_shutter_param_e
 * @param[out] attribute attribute of auto FFC's parameter
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_auto_ffc_params_attribute_get(IrcmdHandle_t* handle, int param_type, param_attribute_t* attribute);


/**
 * @brief current shutter switching status
 */
typedef enum
{
    /// The shutter is close.
    ADV_SHUTTER_CLOSE_STA = 0,
    /// The shutter is open
    ADV_SHUTTER_OPEN_STA = 1,
    /// The shutter is in an unknown status.Appears only when first powered up and is more difficult to acquire
    ADV_SHUTTER_UNKNOW_STA = 2,
}adv_shutter_status_e;


/**
 * @brief Get current shutter switching status
 *
 * @param[in] handle ircmd's handle
 * @param[out] status status of shutter,see adv_shutter_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_shutter_status_get(IrcmdHandle_t* handle, int* status);


/**
 * @brief Set current shutter switching status
 *
 * @param[in] handle ircmd's handle
 * @param[in] status status of shutter,only support 0:close or 1:open
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_shutter_status_set(IrcmdHandle_t* handle, int status);


/**
 * @brief Whether all shutter functions are available (include auto shutter, manual shutter)
 */
typedef enum
{
    BASIC_ALL_FFC_FUNCTION_AVAILABLE = 0,
    BASIC_ALL_FFC_FUNCTION_UNAVAILABLE = 1,
    BASIC_ALL_FFC_FUNCTION_CANCEL = 2,
}basic_all_ffc_status_e;


/**
 * @brief Get current all shutter function enable status
 *
 * @param[in] handle ircmd's handle
 * @param[out] status whether all shutter functions are in effect,see basic_all_ffc_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_all_ffc_function_status_get(IrcmdHandle_t* handle, int* status);


/**
 * @brief Set current all shutter function enable status,
 * If the status is BASIC_ALL_FFC_FUNCTION_UNAVAILABLE, all shutter functions are disabled
 *
 * @param[in] handle ircmd's handle
 * @param[in] status whether all shutter functions are in effect,see basic_all_ffc_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_all_ffc_function_status_set(IrcmdHandle_t* handle, int status);


/**
 * @brief Property page's shutter behavior mode
 */
typedef enum
{
    BASIC_ONLY_B_UPDATE,
    BASIC_ONLY_OOC_UPDATE,
    BASIC_BOTH_B_AND_OOC_UPDATE
}basic_ffc_shutter_behavior_mode_e;


/**
 * @brief do FFC update with behavior selected
 *
 * @param[in] handle ircmd's handle
 * @param[in] mode_type select behavior when ffc updata,see basic_ffc_shutter_behavior_mode_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_manual_ffc_update(IrcmdHandle_t* handle, int mode_type);


/**
 * @brief do FFC without shutter
 *
 * @param[in]  handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_ffc_without_shutter(IrcmdHandle_t* handle);


/**
 * @brief Close the shutter tab
 *
 * @param[in]  handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_shutter_tab_close(IrcmdHandle_t* handle);


/**
 * @brief Open the shutter tab
 *
 * @param[in]  handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_shutter_tab_open(IrcmdHandle_t* handle);


/**
 * @brief  External trigger shutterless mode
 *
 * @param[in]  handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_external_trigger_shutterless(IrcmdHandle_t* handle);


//======================================
//           DEVICE INFO command
//======================================


/**
 * @brief the kind of basic device info
 */
typedef enum
{
    /// device name
    BASIC_DEV_NAME = 1,
    /// release version of firmware
    BASIC_DEV_FW_RELEASE_VERSION = 2,
    /// customer_id
    BASIC_DEV_CUSTOMER_ID = 3,
    /// vendor_id
    BASIC_DEV_VENDOR_ID = 4,
    /// product_id
    BASIC_DEV_PRODUCT_ID = 5,
    /// product number
    BASIC_DEV_INFO_GET_PN = 6,
    /// serial number
    BASIC_DEV_INFO_GET_SN = 7,
    /// the version of ISP algorithm parameter
    BASIC_DEV_ISP_VERSION = 8,
    /// the version of abstract data
    BASIC_DEV_ABSTRACT_VERSION = 9,
    /// the version of system config data
    BASIC_DEV_SYSTEM_VERSION = 10,
    /// the version of calibration data
    BASIC_DEV_CALI_VERSION = 11,
    /// the version of whole data
    BASIC_DEV_WHOLE_DATA_VERSION = 12,
    /// the version of whole package
    BASIC_DEV_WHOLE_PACKAGE_VERSION = 13,
    /// the sensor type
    BASIC_DEV_SENSOR_TYPE = 14,
    /// the sensor id
    BASIC_DEV_SENSOR_ID = 15,
}basic_dev_info_type_e;


/**
 * @brief Get device's information
 *
 * @param[in] handle ircmd's handle
 * @param[in] device_info_type the device's information type, see basic_dev_info_type_e
 * @param[out] data[100] the device's information data
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_device_info_get(IrcmdHandle_t* handle, int device_info_type, char* data);


/**
 * @brief Gets the device powered on time
 *
 * @param[in]  handle ircmd's handle
 * @param[out] time the powered on time, unit: second
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_powered_time_get(IrcmdHandle_t* handle, int* time);


//======================================
//           DEVICE STATUS command
//======================================


/**
 * @brief Get device's temperature
 *
 * @param[in]  handle ircmd's handle
 * @param[out] temperature module's temperature, unit: celsius
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_device_temp_get(IrcmdHandle_t* handle, float* temperature);

/**
 * @brief device realtime status type
 */
typedef enum
{
    /// ir sensor temp
    ADV_IR_SENSOR_VTEMP = 0,
    // fpc defrost temp
    ADV_IR_FPC_TEMP = 1,
}adv_realtime_status_type_e;


/**
 * @brief Set device realtime status
 *
 * @param[in] handle ircmd's handle
 * @param[in] status_type status information type,see adv_realtime_status_type_e
 * @param[in] data status information value
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_device_realtime_status_set(IrcmdHandle_t* handle, int status_type, uint32_t data);


/**
 * @brief Get device realtime status
 *
 * @param[in] handle ircmd's handle
 * @param[in] status_type status information type,see adv_realtime_status_type_e
 * @param[out] data status information value
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_device_realtime_status_get(IrcmdHandle_t* handle, int status_type, uint32_t* data);


/**
 * @brief Read the firmware log data
 *
 * @param[in] handle ircmd's handle
 * @param[out] log_data firmware log data(length:1024 * 1024Byte)
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_firmware_log_data_get(IrcmdHandle_t* handle, uint8_t* log_data);


/**
 * @brief Get current command channel type
 *
 * @param[in] handle ircmd's handle
 * @param[out] command_channel_type current command channel,see command_channel_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_command_channel_type_get(IrcmdHandle_t* handle, int* command_channel_type);


/**
 * @brief current status of device status
 */
typedef enum
{
    /// Initialization of device data status after start-up
    BASIC_STARTUP_MODE = 0,
    /// The initialization of the device has been completed, the device is out of the map, but the stream is not output
    BASIC_PREVIEW_MODE = 1,
    /// Normal output stream of the device
    BASIC_VIDEO_OUT_MODE = 2,
    /// The device is in upgrade mode
    BASIC_UPDATE_MODE = 3,
    /// The device is not in the above status or failed to get status
    BASIC_DEVICE_ERROR_MODE = 4,
}basic_device_status_e;


/**
 * @brief Get current device status
 *
 * @param[in] handle ircmd's handle
 * @param[out] status the status of device,see basic_device_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_device_current_status_get(IrcmdHandle_t* handle, int* status);


/**
 * @brief Get coordinates of the max and min temperature in the region of interest
 *
 * @param[in]   handle ircmd's handle
 * @param[in]   rect_pos start and end coordinates of interest region
 * @param[out]  rect_temp_info information about the region of interest
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_temp_coordinate_of_interest_get(IrcmdHandle_t* handle, IrcmdRect_t rect_pos, LineRectTempInfo_t* rect_temp_info);


//======================================
//           DEVICE SET command
//======================================


/**
* @brief the status of enable
*/
typedef enum {
    BASIC_DISABLE = 0,
    BASIC_ENABLE = 1,
}basic_enable_status_e;


/**
 * @brief Set device sleep status
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  status see basic_enable_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_device_sleep_set(IrcmdHandle_t* handle, int status);


/**
 * @brief Get device sleep status
 *
 * @param[in]   handle ircmd's handle
 * @param[out]  status see basic_enable_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_device_sleep_get(IrcmdHandle_t* handle, int* status);


/**
* @brief rtc time (high accuracy)
*/
typedef struct {
    uint16_t year;
    uint8_t month;
    uint8_t day;
    uint8_t hour;
    uint8_t minute;
    uint8_t second;
    uint16_t millisecond;
    uint16_t microsecond;
}Rtc_Time_t;


/**
 * @brief Set the current time of the device
 *
 * @param[in] handle ircmd's handle
 * @param[in] time rtc start time,see Rtc_Time_t
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_rtc_current_time_set(IrcmdHandle_t* handle, Rtc_Time_t* rtc_time);


/**
 * @brief Get the current time of the device
 *
 * @param[in] handle ircmd's handle
 * @param[out] time record the power-on time,see Rtc_Time_t
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_rtc_current_time_get(IrcmdHandle_t* handle, Rtc_Time_t* time);


/**
* @brief the type of voltage
*/
typedef enum {
    ADV_LOW_VOLTAGE = 0,    //LOW_VOLTAGE = 1.8V
    ADV_HIGH_VOLTAGE = 1,   //HIGH_VOLTAGE = 3.3V
}adv_voltage_type_e;


/**
 * @brief Set DVP/I2C voltage
 *
 * @param[in] handle ircmd's handle
 * @param[in] voltage see adv_voltage_type_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_voltage_change_set(IrcmdHandle_t* handle, int voltage);


/**
 * @brief Get DVP/UART voltage
 *
 * @param[in]  handle ircmd's handle
 * @param[out] voltage see adv_voltage_type_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_voltage_change_get(IrcmdHandle_t* handle, int* voltage);


/**
 * @brief Write FPGA algorithm parameters data for X3
 *
 * @param[in] handle ircmd's handle
 * @param[in] reg_address start address of the data in the register
 * @param[in] length the length of algorithm parameters data,must be a multiple of 4.range:0-64
 * @param[in] data algorithm parameters data
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_algorithm_parameters_write(IrcmdHandle_t* handle, uint16_t reg_address, uint16_t length, uint8_t* data);


/**
 * @brief Read FPGA algorithm parameters data for X3
 *
 * @param[in] handle ircmd's handle
 * @param[in] reg_address start address of the data in the register
 * @param[in] length the length of algorithm parameters data,must be a multiple of 4.range:0-64
 * @param[out] data algorithm parameters data
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_algorithm_parameters_read(IrcmdHandle_t* handle, uint16_t reg_address, uint16_t length, uint8_t* data);


/**
 * @brief Get the version of fpga
 *
 * @param[in] handle ircmd's handle
 * @param[out] data[4] the version of fpga
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_fpga_version_get(IrcmdHandle_t* handle, uint8_t* data);


/**
 * @brief Open device with its device node
 *
 * @param[in] handle ircmd's handle
 * @param[in] params device's node
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_device_open(IrcmdHandle_t* handle, void* params);


/**
 * @brief init device, use this interface set some useful paramters for device
 *
 * @param[in] handle ircmd's handle
 * @param[in] params useful paramters for device, it likes int[64], eg: for uart device the int[0] is baudrate
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_device_init(IrcmdHandle_t* handle, void* params);

/**
 * @brief Close device
 *
 * @param[in] handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_device_close(IrcmdHandle_t* handle);


/**
 * @brief Reset to rom, prepare for update firmware
 *
 * @param[in] handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_reset_to_rom(IrcmdHandle_t* handle);


/**
 * @brief download firmware
 *
 * @param[in] handle ircmd's handle
 * @param[in] file_name the name of firmware file
 * @param[in] data data of firmware file
 * @param[in] file_len size of firmware file
 *
 * @return see IruartError_e
 */
DLLEXPORT IrlibError_e basic_firmware_download(IrcmdHandle_t* handle, char* file_name, uint8_t* data, uint32_t file_len);


/**
 * @brief enter reboot mode
 *
 * @param[in] handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_enter_reboot_mode(IrcmdHandle_t* handle);


/**
 * @brief clear cache to avoid increased boot time
 *
 * @param[in] handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_cache_clear(IrcmdHandle_t* handle);


//======================================
//           PREVIEW START SET command
//======================================


/**
* @brief driver's type
*/
typedef enum {
    IDLE_DRIVER = 0,
    MIPI_DRIVER = 1,
    USB_DRIVER = 2,
    DVP_DRIVER = 3,
}driver_type_e;


/**
 * @brief Stop stream
 *
 * @param[in] handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_preview_stop(IrcmdHandle_t* handle);


/**
 * @brief When the device is in automatic plotting mode, let the stream flow out
 *
 * @param[in] handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_video_stream_continue(IrcmdHandle_t* handle);


/**
 * @brief When the device is in automatic drawing mode, the data stream is temporarily not flowed out.
 *
 * @param[in] handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_video_stream_pause(IrcmdHandle_t* handle);


/**
 * @brief Vc channel type when the device gets stream for MIPI
 */
typedef enum
{
    ADV_DOUBLIE_VC_CHANNEL = 0,
    ADV_SINGLE_VC_CHANNEL = 1,
}adv_mipi_channel_type_e;


/**
 * @brief Set different vc channel when the device gets stream for MIPI
 *
 * @param[in] handle ircmd's handle
 * @param[in] channel_type mipi vc channel type,see adv_mipi_channel_type_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_mipi_channel_type_set(IrcmdHandle_t* handle, int channel_type);


/**
 * @brief Y16 mode source stream modes
 */
typedef enum
{
    /// ir sensor mode source
    ADV_IR_SOURCE_MODE = 0,
    /// KB correction mode source
    ADV_KBC_SOURCE_MODE = 1,
    /// time noise reduction mode source
    ADV_TNR_SOURCE_MODE = 2,
    /// dead pixel correction and horizontal band correction mode source
    ADV_HBC_DPC_SOURCE_MODE = 3,
    /// vertical band correction mode source
    ADV_VBC_SOURCE_MODE = 4,
    /// space noise reduction mode source
    ADV_SNR_SOURCE_MODE = 5,
    /// digital detail enhance mode source
    ADV_DDE_SOURCE_MODE = 6,
    /// automatic gain control mode source
    ADV_AGC_SOURCE_MODE = 7,
    /// GAMMA mode source
    ADV_GAMMA_SOURCE_MODE = 8,
    /// temperature mode source
    ADV_TPD_SOURCE_MODE = 9,
    /// mirror mode source
    ADV_MIRROR_SOURCE_MODE = 10,
    /// yuv mode source(stream source after all algorithms)
    ADV_PICTURE_SOURCE_MODE = 11,
}adv_stream_source_mode_e;


/**
 * @brief Set intermediate stream source mode.
 * For image frame only, the image frame is switched, the default mode is ADV_PICTURE_SOURCE_MODE;
 * for double image frame, Non-image frame is switched, it cannot be switched to ADV_PICTURE_SOURCE_MODE.
 *
 * @param[in] handle ircmd's handle
 * @param[in] src_stream_mode source video streaming mode,see adv_stream_source_mode_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_stream_source_mode_set(IrcmdHandle_t* handle, int src_stream_mode);


/**
 * @brief Get intermediate stream source mode
 *
 * @param[in] handle ircmd's handle
 * @param[out] src_stream_mode source video streaming mode,see adv_stream_source_mode_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_stream_source_mode_get(IrcmdHandle_t* handle, int* src_stream_mode);


/**
* @brief the rate of output frame
*/
typedef enum {
    ADV_LOW_RATE = 30,
    ADV_HIGH_RATE = 60,
}adv_output_frame_rate_e;

/**
* @brief the rate of output frame for mini2256
*/
typedef enum {
    ADV_RATE_25 = 25,
    ADV_RATE_50 = 50,
}adv_mini2256_output_frame_rate_e;

/**
 * @brief Set the rate of output frame
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  rate mini2256 see adv_mini2256_output_frame_rate_e, others see adv_output_frame_rate_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_output_frame_rate_set(IrcmdHandle_t* handle, int rate);


/**
 * @brief Get the rate of output frame
 *
 * @param[in]  handle ircmd's handle
 * @param[out] rate see adv_output_frame_rate_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_output_frame_rate_get(IrcmdHandle_t* handle, int* rate);


/**
* @brief boot logo status
*/
typedef enum {
    BASIC_BOOT_LOGO_DISABLE = 0,
    BASIC_BOOT_LOGO_ENABLE = 1,
}basic_boot_logo_status_e;


/**
 * @brief Set boot logo status
 *
 * @param[in] handle ircmd's handle
 * @param[in] status boot logo status, see basic_boot_logo_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_boot_logo_status_set(IrcmdHandle_t* handle, int status);


/**
 * @brief Get boot logo status
 *
 * @param[in]  handle ircmd's handle
 * @param[out] status boot logo status, see basic_boot_logo_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_boot_logo_status_get(IrcmdHandle_t* handle, int* status);


/**
* @brief the format of YUV
*/
typedef enum {
    ADV_UYVY = 0,
    ADV_VYUY = 1,
    ADV_YUYV = 2,
    ADV_YVYU = 3,
}adv_YUV_format_e;


/**
 * @brief Set the format of YUV
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  format see adv_YUV_format_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_yuv_format_set(IrcmdHandle_t* handle, int format);


/**
 * @brief Get the format of YUV
 *
 * @param[in]  handle ircmd's handle
 * @param[out] format see adv_YUV_format_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_yuv_format_get(IrcmdHandle_t* handle, int* format);


/**
* @brief the format of video output
* ADV_BT656: Line by line form
* ADV_BT656_INTERLACED: Interlaced form
*/
typedef enum {
    ADV_USB_FORMAT = 0,
    ADV_DVP_FORMAT = 1,
    ADV_BT656_FORMAT = 2,
    ADV_MIPI_FORMAT = 3,
    ADV_BT656_INTERLACED_FORMAT = 4,
    ADV_NTSC_FORMAT = 5,        //only for analog video output
    ADV_PAL_FORMAT = 6,         //only for analog video output
    ADV_VO_SPI_FORMAT = 7,      //only for Tiny2C
}adv_video_output_format_e;

/**
* @brief the number of video output
*/
typedef enum {
    SINGLE_VIDEO = 0,       //only image data
    DOUBLE_VIDEO = 1,       //image and temp data
}video_output_num_e;


/**
* @brief video output info
*/
typedef struct {
    int video_output_status;            //see basic_enable_status_e
    int video_output_format;            //see adv_video_output_format_e
    int video_output_fps;               //range:1-60
    int video_output_num;               //see video_output_num_e
}VideoOutputInfo_t;

/**
 * @brief Choose the appropriate mode to start stream
 * @support Tiny2C USB,DVP,MIPI,VO_SPI
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  output_info see VideoOutputInfo_t, dont need to fill video_output_fps
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_preview_start(IrcmdHandle_t* handle, VideoOutputInfo_t output_info);

/**
 * @brief Set the digital video output
 * @support MINI2 USB,DVP,BT656,MIPI
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  output_info see VideoOutputInfo_t, dont need to fill video_output_num
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_digital_video_output_set(IrcmdHandle_t* handle, VideoOutputInfo_t output_info);

/**
 * @brief Get the digital video output
 *
 * @param[in]  handle ircmd's handle
 * @param[out] output_info see VideoOutputInfo_t
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_digital_video_output_get(IrcmdHandle_t* handle, VideoOutputInfo_t* output_info);

/**
 * @brief Set the analog video output info
 * @support MINI2 NTSC,PAL
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  status see basic_enable_status_e
 * @param[in]  format see adv_video_output_format_e, only support ADV_NTSC_FORMAT and ADV_PAL_FORMAT
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_analog_video_output_set(IrcmdHandle_t* handle, int status, int format);

/**
 * @brief Get the analog video output info
 *
 * @param[in]  handle ircmd's handle
 * @param[out] status see basic_enable_status_e
 * @param[out] format see adv_video_output_format_e, only support ADV_NTSC_FORMAT and ADV_PAL_FORMAT
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_analog_video_output_get(IrcmdHandle_t* handle, int* status, int* format);

/**
 * @brief Set external synchronization status
 * @support MINI2
 * @param[in]  handle ircmd's handle
 * @param[in]  status see basic_enable_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_external_synchronization_set(IrcmdHandle_t* handle, int status);


/**
 * @brief Get external synchronization status
 * @support MINI2
 * @param[in]  handle ircmd's handle
 * @param[in]  status see basic_enable_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_external_synchronization_get(IrcmdHandle_t* handle, int* status);

/**
 * @brief Set 7393 analog video output info
 * @support MINI2
 * @param[in]  handle ircmd's handle
 * @param[in]  status see basic_enable_status_e
 * @param[in]  format see adv_video_output_format_e, only support ADV_NTSC_FORMAT and ADV_PAL_FORMAT
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_7393_analog_set(IrcmdHandle_t* handle, int status, int format);

/**
 * @brief Get 7393 analog video output info
 * @support MINI2
 * @param[in]  handle ircmd's handle
 * @param[in]  status see basic_enable_status_e
 * @param[in]  format see adv_video_output_format_e, only support ADV_NTSC_FORMAT and ADV_PAL_FORMAT
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_7393_analog_get(IrcmdHandle_t* handle, int* status, int* format);

/**
 * @brief Set the status of picture freeze
 * When freeze is enable, the device does not respond to zoom_set, mirror_flip_set and image_scene_set commands
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  status see basic_enable_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_picture_freeze_status_set(IrcmdHandle_t* handle, int status);


/**
 * @brief Get the status of picture freeze
 *
 * @param[in]  handle ircmd's handle
 * @param[out] status see basic_picture_freeze_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_picture_freeze_status_get(IrcmdHandle_t* handle, int* status);


/**
 * @brief preview infomation
 */
typedef enum
{
    /// current preview fps
    ADV_CURRENT_FPS = 1,
    /// current preview width
    ADV_CURRENT_WIDTH = 2,
    /// current preview height
    ADV_CURRENT_HEIGHT = 3,
}adv_preview_info_e;


/**
 * @brief Get basic preview info
 *
 * @param[in] handle ircmd's handle
 * @param[in] preview_info see adv_preview_info_e
 * @param[out] data  information value
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_device_preview_info_get(IrcmdHandle_t* handle, int preview_info, uint32_t* data);

//======================================
//           IMAGE FORMAT command
//======================================


/**
* @brief image flip and mirror's type
*/
typedef enum {
    BASIC_NO_MIRROR_OR_FLIP = 0,
    BASIC_ONLY_MIRROR,
    BASIC_ONLY_FLIP,
    BASIC_BOTH_MIRROR_AND_FLIP
}basic_mirror_and_flip_status_e;


/**
 * @brief Set mirror and flip status
 *
 * @param[in] handle ircmd's handle
 * @param[in] value the mirror and flip status, see basic_mirror_and_flip_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_mirror_and_flip_status_set(IrcmdHandle_t* handle, int value);


/**
 * @brief Get mirror and flip status
 *
 * @param[in] handle ircmd's handle
 * @param[out] value the mirror and flip status, see basic_mirror_and_flip_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_mirror_and_flip_status_get(IrcmdHandle_t* handle, int* value);


/**
 * @brief Set the scale
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  scale user-set scale, range:1-8 (accuracy: 0.1)
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_center_zoom_set(IrcmdHandle_t* handle, float scale);


/**
 * @brief Get the scale
 *
 * @param[in]  handle ircmd's handle
 * @param[out] scale user-set scale, range:1-8 (accuracy: 0.1)
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_center_zoom_get(IrcmdHandle_t* handle, float* scale);


/**
 * @brief Set the scale
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  point_pos the point's position
 * @param[in]  scale user-set scale, range:1-8
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_coordinate_zoom_set(IrcmdHandle_t* handle, IrcmdPoint_t point_pos, float scale);


/**
 * @brief Get the scale
 *
 * @param[in]  handle ircmd's handle
 * @param[out] point_pos the point's position
 * @param[out] scale user-set scale, range:1-8
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_coordinate_zoom_get(IrcmdHandle_t* handle, IrcmdPoint_t* point_pos, float* scale);


//=========================================
//           ALGORITHM PARAMETERS command
//=========================================


/**
 * @brief Set image brightness level
 *
 * @param[in] handle ircmd's handle
 * @param[in] level brightness level,range:0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_image_brightness_level_set(IrcmdHandle_t* handle, int level);


/**
 * @brief Get image brightness level
 *
 * @param[in] handle ircmd's handle
 * @param[out] level Brightness level,range:0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_current_brightness_level_get(IrcmdHandle_t* handle, int* level);


/**
 * @brief Get attribute of brightness level
 *
 * @param[in] handle ircmd's handle
 * @param[out] attribute attribute of brightness level
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_attribute_of_brightness_level_get(IrcmdHandle_t* handle, param_attribute_t* attribute);


/**
 * @brief Set image contrast level.The contrast corresponding to brightness
 *
 * @param[in] handle ircmd's handle
 * @param[in] level Contrast level,range:0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_image_contrast_level_set(IrcmdHandle_t* handle, int level);


/**
 * @brief Get image contrast level.The contrast corresponding to brightness
 *
 * @param[in] handle ircmd's handle
 * @param[out] level Contrast level,range:0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_current_contrast_level_get(IrcmdHandle_t* handle, int* level);


/**
 * @brief Get attribute of contrast level.The contrast corresponding to brightness
 *
 * @param[in] handle ircmd's handle
 * @param[out] attribute attribute of contrast level
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_attribute_of_contrast_level_get(IrcmdHandle_t* handle, param_attribute_t* attribute);


/**
 * @brief Set global contrast level
 *
 * @param[in] handle ircmd's handle
 * @param[in] level Contrast level,range:0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_global_contrast_level_set(IrcmdHandle_t* handle, int level);


/**
 * @brief Get global contrast level
 *
 * @param[in] handle ircmd's handle
 * @param[out] level Contrast level,range:0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_global_contrast_level_get(IrcmdHandle_t* handle, int* level);


/**
 * @brief Get attribute of global contrast level
 *
 * @param[in] handle ircmd's handle
 * @param[out] attribute attribute of contrast level
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_attribute_of_global_contrast_level_get(IrcmdHandle_t* handle, param_attribute_t* attribute);


/**
 * @brief Set image detail enhance level
 *
 * @param[in] handle ircmd's handle
 * @param[in] level detail enhance level,range:0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_image_detail_enhance_level_set(IrcmdHandle_t* handle, int level);


/**
 * @brief Get image detail enhance level
 *
 * @param[in] handle ircmd's handle
 * @param[out] level Detail enhance level,range:0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_current_detail_enhance_level_get(IrcmdHandle_t* handle, int* level);


/**
 * @brief Get attribute of detail enhance level
 *
 * @param[in] handle ircmd's handle
 * @param[out] attribute attribute of detail enhance level
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_attribute_of_detail_enhance_level_get(IrcmdHandle_t* handle, param_attribute_t* attribute);


/**
 * @brief Set level of image noise reduction
 * For tiny2C, this function only works in professional mode
 *
 * @param[in] handle ircmd's handle
 * @param[in] level level of noise reduction ,range:0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_image_noise_reduction_level_set(IrcmdHandle_t* handle, int level);


/**
 * @brief Get current level of image noise reduction
 *
 * @param[in] handle ircmd's handle
 * @param[out] level level of noise reduction,range:0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_current_image_noise_reduction_level_get(IrcmdHandle_t* handle, int* level);


/**
 * @brief Get attribute of image noise reduction
 *
 * @param[in] handle ircmd's handle
 * @param[out] attribute attribute of image noise reduction
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_attribute_of_noise_reduction_level_get(IrcmdHandle_t* handle, param_attribute_t* attribute);

/**
* @brief the level of roi
*/
typedef enum {
    BASIC_ROI_DISABLE = 0,              //roi level is 0%
    BASIC_ROI_THIRD = 1,                //roi level is 33%
    BASIC_ROI_HALF = 2,                 //roi level is 50%
}basic_roi_level_e;

/**
 * @brief Set image parameters roi level
 *
 * @param[in] handle ircmd's handle
 * @param[in] level level of roi,range:0-2, see basic_roi_level_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_image_roi_level_set(IrcmdHandle_t* handle, int level);


/**
 * @brief Get image parameters roi level
 *
 * @param[in] handle ircmd's handle
 * @param[out] level level of roi,range:0-2, see basic_roi_level_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_current_image_roi_level_get(IrcmdHandle_t* handle, int* level);


/**
 * @brief Get attribute of roi level
 *
 * @param[in]  handle ircmd's handle
 * @param[out] attribute attribute of roi level
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_attribute_of_roi_level_get(IrcmdHandle_t* handle, param_attribute_t* attribute);


/**
 * @brief Set image parameters agc level,support for AC02
 *
 * @param[in] handle ircmd's handle
 * @param[in] level level of agc,range:0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_image_agc_level_set(IrcmdHandle_t* handle, int level);


/**
 * @brief Get image parameters agc level,support for AC02
 *
 * @param[in]  handle ircmd's handle
 * @param[out] level level of agc,range:0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_current_agc_level_get(IrcmdHandle_t* handle, int* level);


/**
 * @brief Get attribute of agc level,support for AC02
 *
 * @param[in]  handle ircmd's handle
 * @param[out] attribute attribute of agc level
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_attribute_of_agc_level_get(IrcmdHandle_t* handle, param_attribute_t* attribute);


/**
* @brief The mode of the image scene
*/
typedef enum {
    BASIC_DEFAULT_MODE = 0,             //for G1280s CS640 MINI2 Tiny2C
    BASIC_PATROL_MODE = 1,              //for G1280s CS640
    BASIC_MANUAL_MODE = 2,              //for G1280s CS640
    BASIC_LOW_TEMP_MODE = 3,            //for MINI2
    BASIC_LINEAR_MODE = 4,              //for MINI2
    BASIC_LOW_CONTRAST_MODE = 5,        //for MINI2
    BASIC_HIGH_CONTRAST_MODE = 6,       //for MINI2
    BASIC_HIGH_LIGHT_MODE = 7,          //for MINI2
    BASIC_RESERVE1_MODE = 8,            //for MINI2 Tiny2C
    BASIC_RESERVE2_MODE = 9,            //for MINI2
    BASIC_RESERVE3_MODE = 10,           //for MINI2
    BASIC_RESERVE4_MODE = 11,           //for MINI2
    BASIC_RESCUE_MODE = 12,             //not in use
    BASIC_URBAN_MODE = 13,              //not in use
    BASIC_TEMP_MEASURE_MODE = 14,       //for Tiny2C
    BASIC_OUTDOORS_MODE = 15,           //for Tiny2C
}basic_image_scene_mode_e;


/**
 * @brief Set image scene mode
 *
 * @param[in] handle ircmd's handle
 * @param[in] mode mode of image scene,see basic_image_scene_mode_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_image_scene_mode_set(IrcmdHandle_t* handle, int mode);


/**
 * @brief Get current image scene mode
 *
 * @param[in]  handle ircmd's handle
 * @param[out] mode current image image mode
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_current_image_scene_mode_get(IrcmdHandle_t* handle, int* mode);


/**
 * @brief Get basic attribute of image scene mode
 *
 * @param[in]  handle ircmd's handle
 * @param[out] attribute attribute of scene mode
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_attribute_of_scene_mode_get(IrcmdHandle_t* handle, param_attribute_t* attribute);

/**
* @brief The professional mode
*/
typedef enum {
    ADV_NORMAL_MODE = 0,
    ADV_PROFESSIONAL_MODE = 1,
}adv_profession_mode_e;

/**
 * @brief Set professional mode in scene mode
 * @support    Tiny2C
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  mode professional mode in scene mode, see adv_profession_mode_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_profession_mode_set(IrcmdHandle_t* handle, int mode);


/**
 * @brief Get professional mode in scene mode
 * @support    Tiny2C
 *
 * @param[in]  handle ircmd's handle
 * @param[out] mode professional mode in scene mode, see adv_profession_mode_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_profession_mode_get(IrcmdHandle_t* handle, int* mode);

/**
 * @brief Set the gamma rate
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  rate gamma rate, range: 0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_gamma_rate_set(IrcmdHandle_t* handle, int rate);


/**
 * @brief Get the attribute of gamma level
 *
 * @param[in]  handle ircmd's handle
 * @param[out] attribute attribute of gamma level
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_attribute_of_gamma_level_get(IrcmdHandle_t* handle, param_attribute_t* attribute);

/**
 * @brief Get the current gamma level
 *
 * @param[in]  handle ircmd's handle
 * @param[out] rate gamma rate, range: 0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_current_gamma_level_get(IrcmdHandle_t* handle, int* rate);

/**
 * @brief algorithm module
 */
typedef enum
{
    KBC_BYPASS = 1,
    TNR_BYPASS = 2,
    HBC_BYPASS = 3,
    VBC_BYPASS = 4,
    SNR1_BYPASS = 5,
    AGC_BYPASS = 6,
    DDE_BYPASS = 7,
    GAMMA_BYPASS = 8,
    CROSS_BYPASS = 9,
    PIC_MIRROR_BYPASS = 10,
    TMP_MIRRPR_BYPASS = 11,
}adv_algorithm_module_e;

/**
 * @brief algorithm module switch status
 */
typedef enum
{
    ADV_ALGORITHM_MODULE_NORMAL = 0,
    ADV_ALGORITHM_MODULE_BYPASS = 1,
}adv_algorithm_module_switch_status_e;

/**
 * @brief Set the algorithm module switch
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  module select the module to be bypassed, see adv_algorithm_module_e
 * @param[in]  status normal or bypass, see adv_algorithm_module_switch_status_e(normal:0,bypass:1)
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_algorithm_module_bypass_set(IrcmdHandle_t* handle, adv_algorithm_module_e module, int status);

/**
 * @brief Get the current gamma level
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  module select the module you want to get, see adv_algorithm_module_e
 * @param[out] status normal or bypass, see adv_algorithm_module_switch_status_e(normal:0,bypass:1)
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_algorithm_module_bypass_get(IrcmdHandle_t* handle, adv_algorithm_module_e module, int* status);


/**
 * @brief Set the time noise reduce level
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  level time noise reduce level, range: 0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_time_noise_reduce_level_set(IrcmdHandle_t* handle, int level);


/**
 * @brief Get the time noise reduce level
 *
 * @param[in]  handle ircmd's handle
 * @param[out] level time noise reduce level, range: 0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_time_noise_reduce_level_get(IrcmdHandle_t* handle, int* level);


/**
 * @brief Get the attribute of time noise reduce level
 *
 * @param[in]  handle ircmd's handle
 * @param[out] attribute attribute of time noise reduce level
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_time_noise_reduce_attribute_get(IrcmdHandle_t* handle, param_attribute_t* attribute);


/**
 * @brief Set the space noise reduce level
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  level space noise reduce level, range: 0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_space_noise_reduce_level_set(IrcmdHandle_t* handle, int level);


/**
 * @brief Get the space noise reduce level
 *
 * @param[in]  handle ircmd's handle
 * @param[out] level space noise reduce level, range: 0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_space_noise_reduce_level_get(IrcmdHandle_t* handle, int* level);


/**
 * @brief Get the attribute of space noise reduce level
 *
 * @param[in]  handle ircmd's handle
 * @param[out] attribute attribute of space noise reduce level
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_space_noise_reduce_attribute_get(IrcmdHandle_t* handle, param_attribute_t* attribute);


/**
 * @brief The level of enhance
 */
typedef enum
{
    ADV_ZEROTH_LEVEL = 0,
    ADV_FIRST_LEVEL = 1,
    ADV_SECOND_LEVEL = 2,
}adv_edge_enhance_level_e;

/**
 * @brief      Set enhance level of objects' edges
 * @attention  This function takes effect only when the current scene mode supports it
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  level enhance level, the higher the level, the more pronounced the edges of objects, see adv_edge_enhance_level_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_edge_enhance_set(IrcmdHandle_t* handle, int level);


/**
 * @brief      Get enhance level of objects' edges
 * @attention  This function takes effect only when the current scene mode supports it
 *
 * @param[in]  handle ircmd's handle
 * @param[out] level enhance level, the higher the level, the more pronounced the edges of objects, see adv_edge_enhance_level_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_edge_enhance_get(IrcmdHandle_t* handle, int* level);

/**
 * @brief      Set cel level
 * @support    Tiny2C
 *
 * @param[in]   handle ircmd's handle
 * @param[out]  level cel level, range:0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_cel_level_set(IrcmdHandle_t* handle, int level);


/**
 * @brief      Restore cel level
 * @support    Tiny2C
 *
 * @param[in]   handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_cel_level_restore(IrcmdHandle_t* handle);

//======================================
//          PALETTE and ISOTHERMAL command
//======================================


/**
 * @brief Get the total number of palette color tables supported by the device
 *
 * @param[in] handle ircmd's handle
 * @param[out] color_number the total number of palette color tables
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_palette_num_get(IrcmdHandle_t* handle, int* color_number);


/**
 * @brief Get palette color name
 *
 * @param[in] handle ircmd's handle
 * @param[in] color_index palette color index
 * @param[out] color_name[10] palette color name.eg:YP0500
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_palette_name_get(IrcmdHandle_t* handle, int color_index, char* color_name);


/**
 * @brief Get current palette color index
 *
 * @param[in] handle ircmd's handle
 * @param[out] color_index  palette color index
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_palette_idx_get(IrcmdHandle_t* handle, int* color_index);


/**
 * @brief Set palette color index
 *
 * @param[in] handle ircmd's handle
 * @param[in] color_index  palette color index
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_palette_idx_set(IrcmdHandle_t* handle, int color_index);

/**
 * @brief Set palette color switch
 * @support Tiny2C
 *
 * @param[in] handle ircmd's handle
 * @param[in] palette_switch  palette color switch, see basic_enable_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_palette_switch_set(IrcmdHandle_t* handle, int palette_switch);


/**
 * @brief isothermal switch status
 */
typedef enum
{
    BASIC_ISOTHERMAL_DISABLE = 0,
    BASIC_ISOTHERMAL_ENABLE = 1,
}adv_isothermal_status_e;


/**
 * @brief Get isothermal switch
 *
 * @param[in] handle ircmd's handle
 * @param[out] isothermal_switch_type the switch of iosthermal,see adv_isothermal_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_isothermal_switch_get(IrcmdHandle_t* handle, int* isothermal_switch_type);


/**
 * @brief Set isothermal switch
 *
 * @param[in] handle ircmd's handle
 * @param[in] isothermal_switch_type the switch of iosthermal,see adv_isothermal_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_isothermal_switch_set(IrcmdHandle_t* handle, int isothermal_switch_type);


/**
* @brief the mode of pseudo color
*/
typedef enum {
    ///Mapping pseudo-color table according to AGC output when isothermal mode
    ADV_IOSTHERMAL_MODE_0,
    ///Mapping pseudo-color table according to AGC output
    ADV_IOSTHERMAL_MODE_1,
    ///Mapping pseudo-color table according to TPD output
    ADV_IOSTHERMAL_MODE_2,
    ///full color compress mode, other points are the same color
    ADV_IOSTHERMAL_MODE_3,
    ///uncompress color mode, points remain black and white
    ADV_IOSTHERMAL_MODE_4,
    ///full color compress mode, other points remain black and white
    ADV_IOSTHERMAL_MODE_5,
}adv_isothermal_mode_e;


/**
 * @brief Get isothermal mode
 *
 * @param[in] handle ircmd's handle
 * @param[out] value the mode of isothermal,see adv_isothermal_mode_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_isothermal_mode_get(IrcmdHandle_t* handle, int* value);


/**
 * @brief Set isothermal mode
 *
 * @param[in] handle ircmd's handle
 * @param[in] value the mode of isothermal,see adv_isothermal_mode_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_isothermal_mode_set(IrcmdHandle_t* handle, int value);


/**
 * @brief Get isothermal analysis range.usually low gain:-50~650;high gain:-70~200
 *
 * @param[in] handle ircmd's handle
 * @param[out] high_temp the high temp of isothermal analysis range,unit: Celcius
 * @param[out] low_temp the low temp of isothermal analysis range,unit: Celcius
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_isothermal_limit_get(IrcmdHandle_t* handle, float* high_temp, float* low_temp);


/**
 * @brief Set isothermal analysis range.usually low gain:-50~650;high gain:-70~200, suggest to use adv_isothermal_limit_get to get the range
 *  Floating point numbers are not currently supported,and floating point numbers will be forced to integers.
 *
 * @param[in] handle ircmd's handle
 * @param[in] high_temp the high temp of isothermal analysis range,unit: Celcius
 * @param[in] low_temp the low temp of isothermal analysis range,unit: Celcius
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_isothermal_limit_set(IrcmdHandle_t* handle, float high_temp, float low_temp);


/**
 * @brief Get isothermal analysis range.usually low gain:-50~650;high gain:-70~200
 *
 * @param[in] handle ircmd's handle
 * @param[out] high_temp the high temp of isothermal analysis range,unit: Celcius
 * @param[out] low_temp the low temp of isothermal analysis range,unit: Celcius
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_isothermal_limit_get_high_precision(IrcmdHandle_t* handle, float* high_temp, float* low_temp);


/**
 * @brief Set isothermal analysis range.usually low gain:-50~650;high gain:-70~200, suggest to use adv_isothermal_limit_get to get the range
 *  Floating point numbers are supported.
 *
 * @param[in] handle ircmd's handle
 * @param[in] high_temp the high temp of isothermal analysis range,unit: Celcius
 * @param[in] low_temp the low temp of isothermal analysis range,unit: Celcius
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_isothermal_limit_set_high_precision(IrcmdHandle_t* handle, float high_temp, float low_temp);


//======================================
//          SECOND CALI command
//======================================


/**
 * @brief the status of second cali flag
 */
typedef enum
{
    ADV_SECOND_CALI_DISABLE = 0,
    ADV_SECOND_CALI_ENABLE = 1,
}adv_second_calib_status_e;


/**
 * @brief the type of second cali flag_index
 */
typedef enum
{
    ADV_SECOND_CALI_FLAG_TEC_K = 0,
    ADV_SECOND_CALI_FLAG_KT = 1,
    ADV_SECOND_CALI_FLAG_BT = 2,
    ADV_SECOND_CALI_FLAG_RMV = 3,
    ADV_SECOND_CALI_FLAG_NUC_T = 4,
    ADV_SECOND_CALI_FLAG_DPC = 5,
}adv_second_calib_flag_status_e;


/**
 * @brief Set second cali flag status
 * If status is ADV_SECOND_CALI_DISABLE, the default data is used based on flag_index
 * eg:status is ADV_SECOND_CALI_DISABLE, flag_index is ADV_SECOND_CALI_FLAG_KT, use default kt data
 * If status is ADV_SECOND_CALI_ENSABLE, the second cali data is used based on flag_index
 * eg:status is ADV_SECOND_CALI_ENSABLE, flag_index is ADV_SECOND_CALI_FLAG_KT, use second cali kt data
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  flag_index the index of flag that needed to set
 * @param[in]  status see adv_second_calib_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_second_cali_flag_set(IrcmdHandle_t* handle, int flag_index, int status);


/**
 * @brief Get second cali flag status
 *
 * @param[in]   handle ircmd's handle
 * @param[in]   flag_index the index of flag that needed to set
 * @param[out]  status see adv_second_calib_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_second_cali_flag_get(IrcmdHandle_t* handle, int flag_index, int* status);


//======================================
//          K CALI command
//======================================


/**
 * @brief calib K value
 */
typedef enum
{
    BASIC_LOW_TEMP_OBJECT = 0,            //Select this status when aiming at a low temperature object
    BASIC_HIGH_TEMP_OBJECT = 1,           //Select this status when aiming at a high temperature object
    BASIC_CALCULATION = 2,                //After aiming at low and high temperature objects, select this status to calculate
}adv_K_calib_status_e;


/**
 * @brief K value calibration
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  status see adv_K_calib_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_k_value_calibration(IrcmdHandle_t* handle, int status);


/**
 * @brief Cancel K value calibration, restore old data
 *
 * @param[in]  handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_k_value_calibration_cancel(IrcmdHandle_t* handle);


/**
 * @brief Clear K value calibration
 *
 * @param[in]  handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_k_value_clear(IrcmdHandle_t* handle);


//======================================
//          RMCOVER CALI command
//======================================


/**
 * @brief Auto remove cover calibration
 *
 * @param[in] handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_auto_rmcover_cali(IrcmdHandle_t* handle);


/**
 * @brief Cancel rmcover calib data
 *
 * @param[in]  handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_rmcover_calib_cancel(IrcmdHandle_t* handle);


/**
 * @brief Clear rmcover calib data
 *
 * @param[in]  handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_rmcover_calib_clear(IrcmdHandle_t* handle);


//======================================
//          DPC CALI command
//======================================


/**
 * @brief Auto dead pixel calibration
 *
 * @param[in] handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_auto_dpc_cali(IrcmdHandle_t* handle);

/**
 * @brief Cancel dpc calib data
 *
 * @param[in]  handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_dpc_calib_data_cancel(IrcmdHandle_t* handle);


/**
 * @brief Set cursor position to a dead pixel
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  point_pos the cursor point's position
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_set_cursor_position_to_dpc(IrcmdHandle_t* handle, IrcmdPoint_t point_pos);


/**
 * @brief Set cursor position to a non-dead pixel
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  point_pos the cursor point's position
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_set_cursor_position_to_non_dpc(IrcmdHandle_t* handle, IrcmdPoint_t point_pos);


/**
 * @brief cursor switch status
 */
typedef enum
{
    ADV_CURSOR_DISABLE = 0,
    ADV_CURSOR_ENABLE = 1,
}adv_cursor_switch_status_e;


/**
 * @brief Set cursor switch status
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  status see adv_cursor_switch_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_cursor_switch_status_set(IrcmdHandle_t* handle, int status);


/**
 * @brief Get cursor switch status
 *
 * @param[in]  handle ircmd's handle
 * @param[out] status see adv_cursor_switch_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_cursor_switch_status_get(IrcmdHandle_t* handle, int* status);


/**
 * @brief Set cursor position
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  point_pos the point's position
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_cursor_position_set(IrcmdHandle_t* handle, IrcmdPoint_t point_pos);


/**
 * @brief Get cursor position
 *
 * @param[in]  handle ircmd's handle
 * @param[out] point_pos the point's position
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_cursor_position_get(IrcmdHandle_t* handle, IrcmdPoint_t* point_pos);

/**
 * @brief Clear dead pixel cell calib data
 *
 * @param[in]  handle ircmd's handle
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_dpc_calib_data_clear(IrcmdHandle_t* handle);

//======================================
//          GAIN MODE command
//======================================


/**
* @brief the device's gain value
*/
typedef enum {
    /// low gain
    BASIC_LOW_GAIN = 0,
    /// high gain
    BASIC_HIGH_GAIN,
    /// low-low gain
    BASIC_2LOW_GAIN,
    /// high-high gain
    BASIC_2HIGH_GAIN,
}basic_gain_status_e;


/**
 * @brief Set gain value of the device
 *
 * @param[in] handle ircmd's handle
 * @param[in] value the device's gain value,see basic_gain_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_gain_set(IrcmdHandle_t* handle, int value);


/**
 * @brief Get gain value of the device
 *
 * @param[in] handle ircmd's handle
 * @param[out] gain_value the device's gain value,see basic_gain_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_gain_get(IrcmdHandle_t* handle, int* gain_value);

//======================================
//          INFO LINE command
//======================================

/**
 * @brief the index of information line
 */
typedef enum
{
    ADV_SW_MODEL_INFO = 0,
    ADV_SW_FRAME_INFO = 1,
    ADV_SW_MODEL_STATUS = 3,
    ADV_SW_SHUTTER_STATUS = 4,
    ADV_SW_AUTO_SHUTTER_STATUS = 5,
    ADV_SW_ISP_INFO_VERSION = 6,
    ADV_SW_TPD_FORMAT = 7,
    ADV_SW_MIRROR_ENALBE = 8,
    ADV_SW_FLIP_ENALBE = 9,
    ADV_SW_TEMP_AREA = 10,
}adv_info_line_index_e;


/**
 * @brief Get info line information
 * @support    Tiny2C
 *
 * @param[in]  handle ircmd's handle
 * @param[in]  offset the offset against index
 * @param[in]  index  see adv_info_line_index_e, select the index corresponding to the information you want
 * @param[in]  length the lenghth of data to be read
 * @param[out] data   store the read data from info line
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e adv_info_line_get(IrcmdHandle_t* handle, int offset, int index, int length, uint32_t* data);


//======================================
//          SUN DETECT command
//======================================


/**
 * @brief sun detect switch status
 */
typedef enum
{
    BASIC_SUN_DETECT_DISABLE = 0,
    BASIC_SUN_DETECT_ENABLE = 1,
}basic_sun_detect_status_e;


/**
 * @brief Get current sun detect switch status
 *
 * @param[in] handle ircmd's handle
 * @param[out] status the switch status of sun detect,see basic_sun_detect_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_sun_detect_switch_get(IrcmdHandle_t* handle, int* status);


/**
 * @brief Set sun detect switch for sunburn protection
 *
 * @param[in] handle ircmd's handle
 * @param[in] status the switch status of sun detect,see basic_sun_detect_status_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_sun_detect_switch_set(IrcmdHandle_t* handle, int status);


/**
 * @brief Get the range of the current saturation pixel ratio
 *
 * @param[in] handle ircmd's handle
 * @param[out] max_pixel_ratio maximum ratio of saturated pixels, must be bigger than min_pixel_ratio ,range:0-100
 * @param[out] min_pixel_ratio minimum ratio of saturated pixels, must be smaller than max_pixel_ratio ,range:0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_sun_detect_pixel_ratio_get(IrcmdHandle_t* handle, int* max_pixel_ratio, int* min_pixel_ratio);


/**
 * @brief Set the range of saturation pixel ratio for sun detect. The more the ratio, the more saturated pixel.
 * When the saturation pixel ratio is within the setting range, it is determined that the saturation is caused by the sun
 *
 * @param[in] handle ircmd's handle
 * @param[in] max_pixel_ratio maximum ratio of saturated pixels, must be bigger than min_pixel_ratio ,range:0-100
 * @param[in] min_pixel_ratio minimum ratio of saturated pixels, must be smaller than max_pixel_ratio ,range:0-100
 *
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_sun_detect_pixel_ratio_set(IrcmdHandle_t* handle, int max_pixel_ratio, int min_pixel_ratio);


/**
 * @brief Get the range of the current roundness level
 *
 * @param[in] handle ircmd's handle
 * @param[out] max_roundness_level maximum level of roundness
 * @param[out] min_roundness_level minimum level of roundness
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_sun_detect_roundness_level_get(IrcmdHandle_t* handle, int* max_roundness_level, int* min_roundness_level);


/**
 * @brief Set the range of roundness level for sun detect. The lower the level, the more round the object is.
 * When the roundness level of the object is within the setting range, the object is judged to be sun
 *
 * @param[in] handle ircmd's handle
 * @param[in] max_roundness_level maximum level of roundness, must be higher than min_roundness_level ,range:0-100
 * @param[in] min_roundness_level maximum level of roundness, must be lower than max_roundness_level ,range:0-100
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_sun_detect_roundness_level_set(IrcmdHandle_t* handle, int max_roundness_level, int min_roundness_level);


//======================================
//          RESTORE command
//======================================


/**
* @brief the type of data to restore
*/
typedef enum {
    /// include dead pxiel data,recover data and tpd data, support for 640 and 1280
    BASIC_All_RECAL_DATA = 0,
    /// restore algorithm parameters to default, support for AC02
    /// include denoise level, detail level, roi level, brightness level, contrast level
    BASIC_RESTORE_ALGORITHM_PARAMETERS = 1,
    /// restore the system function parameter, support for mini2384 and P2L
    /// for mini2384, system function parameters include: video output format, image data source, uart voltage settings and more
    /// for P2L, system function parameters include: scene mode, palette index, shutter param, image data source and more
    BASIC_RESTORE_SYSTEM_PARAMETERS = 2,
    /// restore k value to default, support for mini2384 and P2L
    BASIC_RESTORE_K_VALUE = 3,
    /// restore dpc calib data to default, support for mini2384 and P2L
    BASIC_RESTROE_DPC_DATA = 4,
    /// restore rmcover calib data to default, support for mini2384 and P2L
    BASIC_RESTROE_RMCOVER_DATA = 5,
    /// restore one point tpd calib data to default, support for P2L
    BASIC_RESTORE_TPD_DATA = 6,
    /// restore profession scene level, support for Tiny2C
    BASIC_RESTORE_PROFESSION_SCENE_LEVEL = 7,
    /// restore two points tpd calib data, support for Tiny2C
    BASIC_RESTORE_DOUBLE_TPD_DATA = 8,
}basic_restore_type_e;


/**
 * @brief Restore default data
 *
 * @param[in] handle ircmd's handle
 * @param[in] type the type of restore data,see basic_restore_type_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_restore_default_data(IrcmdHandle_t* handle, int type);


//======================================
//          SAVE command
//======================================


/**
* @brief the type of data to save
*/
typedef enum {
    /// save changed algorithm parameters, support for AC02
    /// for AC02, algorithm parameters include: denoise level, detail level, roi level, brightness level, contrast level
    BASIC_SAVE_ALGORITHM_PARAMETERS = 0,
    /// save the changed system function parameter, support for mini2384 and P2L
    /// for mini2384, system function parameters include: image data source, uart voltage settings and more
    /// for P2L, system function parameters include: scene mode, palette index, shutter param, image data source and more
    BASIC_SAVE_SYSTEM_PARAMETERS = 1,
    /// save k value after calib, support for mini2384 and P2L
    BASIC_SAVE_K_VALUE = 2,
    /// save dpc calib data after calib, support for mini2384 and P2L
    BASIC_SAVE_DPC_DATA = 3,
    /// save rmcover calib data after calib, support for mini2384 and P2L
    BASIC_SAVE_RMCOVER_DATA = 4,
    /// save one point tpd calib data after calib, support for P2L
    BASIC_SAVE_TPD_DATA = 5,
    /// save video output format
    BASIC_SAVE_VIDEO_OUTPUT_FORMAT = 6,
    /// save profession scene level, support for Tiny2C
    BASIC_SAVE_PROFESSION_SCENE_LEVLE = 7,
    /// save two points tpd calib data after calib, support for Tiny2C
    BASIC_SAVE_DOUBLE_TPD_DATA = 8,
}basic_save_type_e;

/**
 * @brief Save data, include algorithm parameters, system parameters, calib data
 *
 * @param[in] handle ircmd's handle
 * @param[in] type the type of save data,see basic_save_type_e
 *
 * @return see IrlibError_e
 */
DLLEXPORT IrlibError_e basic_save_data(IrcmdHandle_t* handle, int type);


//======================================
//          DEVELOPING command
//======================================


/**
 * @brief Set ISP source mode
 *
 * @param[in] handle ircmd's handle
 * @param[in] isp_source_mode ISP source mode type, see basic_isp_source_mode_e
 *
 * @return see IrlibError_e
 */
#ifndef COMMAND_IN_DEVELOPING
DLLEXPORT IrlibError_e basic_isp_source_set(IrcmdHandle_t* handle, int isp_source_mode);//TODO
#endif


/**
 * @brief Get ISP source mode
 *
 * @param[in] handle ircmd's handle
 * @param[out] isp_source_mode ISP source mode type, see basic_isp_source_mode_e
 *
 * @return see IrlibError_e
 */
#ifndef COMMAND_IN_DEVELOPING
DLLEXPORT IrlibError_e basic_isp_source_get(IrcmdHandle_t* handle, int* isp_source_mode);//TODO
#endif


/**
 * @brief Set image parameter
 *
 * @param[in] handle ircmd's handle
 * @param[in] image_param_type image parameter type
 * @param[in] data image parameter value
 *
 * @return see IrlibError_e
 */
#ifndef COMMAND_IN_DEVELOPING
DLLEXPORT IrlibError_e basic_image_params_set(IrcmdHandle_t* handle, int image_param_type, void* data);//TODO
#endif


/**
 * @brief Get image parameter
 *
 * @param[in] handle ircmd's handle
 * @param[in] image_param_type image parameter type
 * @param[out] data image parameter value
 *ss
 * @return see IrlibError_e
 */
#ifndef COMMAND_IN_DEVELOPING
DLLEXPORT IrlibError_e basic_image_params_get(IrcmdHandle_t* handle, int image_param_type, void* data);//TODO
#endif

#ifdef __cplusplus
}
#endif

#endif
