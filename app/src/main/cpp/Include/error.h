#ifndef _ERROR_H_
#define _ERROR_H_

/**
* @brief Error type in libraries
* For the return value of the error report, the first two indicate different libraries, and the last two indicate the type of error.
* For example -201, 2 means the error of libircmd library, 01 means the error type is dynamic alloc memory failed.
* For example -1000, 10 means the error of libirupgrade library, 00 means the error type is parameter error.
*/
typedef enum
{
	/** Function execute success */
	IRLIB_SUCCESS = 0,

	// libircam
	/** Invalid parameter */
	IRCAM_PARAM_ERROR = -100,
	/** Dynamic alloc memory failed */
	IRCAM_MEMORY_ALLOC_FAILED = -101,

	//libircmd
	/** Invalid parameter */
	IRCMD_PARAM_ERROR = -200,
	/** Dynamic alloc memory failed */
	IRCMD_MEMORY_ALLOC_FAILED = -201,
	/** Crc check failed */
	IRCMD_CRC_CHECK_FAILED = -202,
	/** Read data function execute failed */
	IRCMD_READ_FUNC_EXECUTE_FAILED = -203,
	/** Write data function execute failed */
	IRCMD_WRITE_FUNC_EXECUTE_FAILED = -204,
	/** Command execute failed */
	IRCMD_CMD_EXECUTE_FAILED = -205,
	/** Command not supported  */
	IRCMD_CMD_UNSUPPORTED = -206,

	//libirdfu
	/** Invalid parameter */
	IRDFU_PARAM_ERROR = -300,
	/** Dynamic alloc memory failed */
	IRDFU_MEMORY_ALLOC_FAILED = -301,
	/** Find DFU capable USB device failed */
	IRDFU_FIND_DFU_DEV_FAILED = -302,

	//libiri2c
	/** Invalid parameter */
	IRI2C_PARAM_ERROR = -400,
	/** Dynamic alloc memory failed  */
	IRI2C_MEMORY_ALLOC_FAILED = -401,
	/** Crc check failed */
	IRI2C_CRC_CHECK_FAILED = -402,
	/** Crc16 compare failed */
	IRI2C_CRC16_CMP_FAILED = -403,
	/** Function not supported  */
	IRI2C_FUNCTION_UNSUPPORTED = -404,
	/** Device control transfer error */
	IRI2C_CONTROL_TRANSFER_FAILED = -405,
	/** Set command header or data failed */
	IRI2C_SET_CMD_HEADER_OR_DATA_FAILED = -406,
	/** Get command data failed  */
	IRI2C_GET_CMD_DATA_FAILED = -407,
	/** Fill command header failed  */
	IRI2C_FILL_CMD_HEADER_FAILED = -408,
	/** Fetch command header failed  */
	IRI2C_FETCH_CMD_HEADER_FAILED = -409,
	/** Read one transfer data failed */
	IRI2C_READ_ONE_TRANSFER_DATA_FAILED = -410,
	/** Write one transfer data failed  */
	IRI2C_WRITE_ONE_TRANSFER_DATA_FAILED = -411,
	/** Write command with no return status failed */
	IRI2C_WRITE_CMD_WITHOUT_RETURN_FAILED = -412,
	/** Get command execute status failed */
	IRI2C_GET_STATUS_FAILED = -413,
	/** Get command execute status timeout */
	IRI2C_GET_CMD_STATUS_TIMEOUT = -414,
	/** Standard read command execute failed */
	IRI2C_READ_STD_CMD_FAILED = -415,
	/** Standard write command execute failed */
	IRI2C_WRITE_STD_CMD_FAILED = -416,
	/** Standard write command with no status return execute failed */
	IRI2C_WRITE_STD_CMD_WITHOUT_RETURN_FAILED = -417,
	/** Command execution result is error */
	IRI2C_CMD_EXECUTE_STATUS_ERROR = -418,
	/** Command execute failed */
	IRI2C_CMD_EXECUTE_FAILED = -419,

	//libiruart
	/** Invalid parameter */
	IRUART_PARAM_ERROR = -500,
	/** Dynamic alloc memory failed */
	IRUART_MEMORY_ALLOC_FAILED = -501,
	/** Crc check failed */
	IRUART_CRC_CHECK_FAILED = -502,
	/** Crc16 compare failed */
	IRUART_CRC16_CMP_FAILED = -503,
	/** Function not supported  */
	IRUART_FUNCTION_UNSUPPORTED = -504,
	/** Device read control transfer failed */
	IRUART_CONTROL_TRANSFER_READ_FAILED = -505,
	/** Device write control transfer failed */
	IRUART_CONTROL_TRANSFER_WRITE_FAILED = -506,
	/** Device read control transfer timeout */
	IRUART_CONTROL_TRANSFER_READ_TIMEOUT = -507,
	/** Magic number verify failed */
	IRUART_MAGIC_NUM_CHECK_FAILED = -508,
	/** Open device failed */
	IRUART_OPEN_DEV_FAILED = -509,
	/** Close device failed */
	IRUART_CLOSE_DEV_FAILED = -510,
	/** Set com configuration failed in windows*/
	IRUART_SETCOMMSTATE_FAILED = -511,
	/** Get com configuration failed in windows*/
	IRUART_GETCOMMSTATE_FAILED = -512,
	/** Set the read/write buffer failed */
	IRUART_SETUPCOMM_FAILED = -513,
	/** Clear com failed in libiruart*/
	IRUART_CLOSECOMMERROR_FAILED = -514,
	/** Set com configuration failed in linux*/
	IRUART_TCSETATTR_FAILED = -515,
	/** Get com configuration failed in linux*/
	IRUART_TCGETATTR_FAILED = -516,
	/** Get command execute status failed */
	IRUART_GET_STATUS_FAILED = -517,
	/** Command execution result is error */
	IRUART_CMD_EXECUTE_STATUS_ERROR = -518,
	/** Command execute failed */
	IRUART_CMD_EXECUTE_FAILED = -519,
	/** Set command header or data failed */
	IRUART_SET_CMD_HEADER_OR_DATA_FAILED = -520,
	/** Get command data failed */
	IRUART_GET_CMD_DATA_FAILED = -521,
	/** firmware download failed */
	IRUART_FIRMWARE_DOWNLOAD_FAILED = -522,
	/** set baudrate failed*/
	IRUART_SET_BAUDRATE_FAILED = -523,

	//libiruvc
	/** Parameters error */
	IRUVC_PARAM_ERROR = -600,
	/** Dynamic alloc memory failed */
	IRUVC_MEMORY_ALLOC_FAILED = -601,
	/** Crc check failed */
	IRUVC_CRC_CHECK_FAILED = -602,
	/** Crc16 compare failed */
	IRUVC_CRC16_CMP_FAILED = -603,
	/** Error io */
	IRUVC_IO_ERROR = -604,
	/** Error access */
	IRUVC_ACCESS_ERROR = -605,
	/** Find device failed */
	IRUVC_FIND_DEVICE_FAILED = -606,
	/** Entity not found */
	IRUVC_NOT_FOUND_ERROR = -607,
	/** Resource busy */
	IRUVC_RESOURCE_BUSY = -608,
	/** Operation timed out */
	IRUVC_OPERATION_TIMEOUT = -609,
	/** Overflow */
	IRUVC_OVERFLOW_ERROR = -610,
	/** Pipe error */
	IRUVC_PIPE_ERROR = -611,
	/** System call interrupted */
	IRUVC_INTERRUPTED_ERROR = -612,
	/** Insufficient memory */
	IRUVC_NO_MEM_ERROR = -613,
	/** Operation not supported */
	IRUVC_OPERATION_UNSUPPORTED = -614,
	/** Libiruvc's service context initialize failed */
	IRUVC_UVC_INIT_FAILED = -615,
	/** Get device list failed */
	IRUVC_GET_DEV_LIST_FAILED = -616,
	/** Get device info failed */
	IRUVC_GET_DEV_INFO_FAILED = -617,
	/** Open device fail */
	IRUVC_OPEN_DEV_FAILED = -618,
	/** Get device's index failed */
	IRUVC_GET_DEV_INDEX_FAILED = -619,
	/** Get device's descriptor failed */
	IRUVC_GET_DEV_DESCRIPTOR_FAILED = -620,
	/** Device is already opened */
	IRUVC_DEV_OPENED = -621,
	/** Device doesn't provide a matching stream */
	IRUVC_GET_FORMAT_FAILED = -622,
	/** User's callback fucntion is empty */
	IRUVC_USER_CALLBACK_EMPTY = -623,
	/** Device start streaming failed */
	IRUVC_START_STREAMING_FAILED = -624,
	/** Over time when getting frame */
	IRUVC_GET_FRAME_TIMEOUT = -625,
	/** Control transter failed */
	IRUVC_CONTROL_TRANSFER_FAILED = -626,
	/** Get command execute status failed */
	IRUVC_GET_STATUS_FAILED = -627,
	/** Set command header or data failed */
	IRUVC_SET_CMD_HEADER_OR_DATA_FAILED = -628,
	/** Get command data failed */
	IRUVC_GET_CMD_DATA_FAILED = -629,
	/** Checking vdcmd send failed */
	IRUVC_CHECK_DONE_FAILED = -630,
	/** Detect device failed */
	IRUVC_DETECT_DEVICE_FAILED = -631,
	/** Device is not UVC-compliant */
	IRUVC_INVALID_DEV_ERROR = -632,
	/** Mode not supported */
	IRUVC_INVALID_MODE_ERROR = -633,
	/** Resource has a callback (can't use polling and async) */
	IRUVC_CALLBACK_EXISTS_ERROR = -634,
	/** Status error */
	IRUVC_STATUS_ERROR = -635,
	/** Undefined error */
	IRUVC_OTHER_ERROR = -636,

	//libirv4l2
	/** Invalid parameter */
	IRV4L2_PARAM_ERROR = -700,
	/** Dynamic alloc memory failed */
	IRV4L2_MEMORY_ALLOC_FAILED = -701,
	/** Crc check failed */
	IRV4L2_CRC_CHECK_FAILED = -702,
	/** Crc16 compare failed */
	IRV4L2_CRC16_CMP_FAILED = -703,
	/** Function not supported */
	IRV4L2_FUNCTION_UNSUPPORTED = -704,
	/** Device control transfer error */
	IRV4L2_CONTROL_TRANSFER_FAILED = -705,
	/** Open device video node failed */
	IRV4L2_OPEN_VIDEO_NODE_FAILED = -706,
	/** Close device video node failed */
	IRV4L2_CLOSE_VIDEO_NODE_FAILED = -707,
	/** Ioctl VIDIOC_QUERYCAP failed */
	IRV4L2_IOCTL_VIDIOC_QUERYCAP_FAILED = -708,
	/** Ioctl VIDIOC_REQBUFS failed */
	IRV4L2_IOCTL_VIDIOC_REQBUFS_FAILED = -709,
	/** Ioctl VIDIOC_DQBUF failed */
	IRV4L2_IOCTL_VIDIOC_DQBUF_FAILED = -710,
	/** Ioctl VIDIOC_QBUF failed */
	IRV4L2_IOCTL_VIDIOC_QBUF_FAILED = -711,
	/** Mmap failed */
	IRV4L2_MMAP_FAILED = -712,
	/** Ioctl VIDIOC_S_FMT failed */
	IRV4L2_IOCTL_VIDIOC_S_FMT_FAILED = -713,
	/** Ioctl VIDIOC_G_FMT failed */
	IRV4L2_IOCTL_VIDIOC_G_FMT_FAILED = -714,
	/** Ioctl VIDIOC_S_PARAM failed */
	IRV4L2_IOCTL_VIDIOC_S_PARAM_FAILED = -715,
	/** Ioctl VIDIOC_G_PARAM failed */
	IRV4L2_IOCTL_VIDIOC_G_PARAM_FAILED = -716,
	/** Start stream failed */
	IRV4L2_IOCTL_VIDIOC_STREAMON_FAILED = -717,
	/** Get frame data failed */
	IRV4L2_SELECT_FRAME_FAILED = -718,
	/** Get frame data timeout */
	IRV4L2_SELECT_FRAME_TIMEOUT = -719,
	/** Open device control node failed */
	IRV4L2_OPEN_CONTROL_NODE_FAILED = -720,
	/** Open device control node failed */
	IRV4L2_CLOSE_CONTROL_NODE_FAILED = -721,
	/** Set command header or data failed */
	IRV4L2_SET_CMD_HEADER_OR_DATA_FAILED = -722,
	/** Get command data failed  */
	IRV4L2_GET_CMD_DATA_FAILED = -723,
	/** Get command execute status failed */
	IRV4L2_GET_STATUS_FAILED = -724,
	/** Command execution result is error */
	IRV4L2_CMD_EXECUTE_STATUS_ERROR = -725,
	/** Device status is not idle */
	IRV4L2_DEVICE_STATUS_NOT_IDLE = -726,
	/** Get command execute status timeout */
	IRV4L2_GET_CMD_STATUS_TIMEOUT = -727,
	/** Command execute failed */
	IRV4L2_CMD_EXECUTE_FAILED = -728,

	//libirupgrade
	/** Invalid parameter */
	IRUPGRADE_PARAM_ERROR = -900,
	/** Dynamic alloc memory failed */
	IRUPGRADE_MEMORY_ALLOC_FAILED = -901,
	/** Open config file failed */
	IRUPGRADE_OPEN_CFG_FILE_FAILED = -902,
	/** Close config file failed */
	IRUPGRADE_CLOSE_CFG_FILE_FAILED = -903,
	/** Open device failed */
	IRUPGRADE_OPEN_DEV_FAILED = -904,
	/** Close device failed */
	IRUPGRADE_CLOSE_DEV_FAILED = -905,
	/** Character check failed */
	IRUPGRADE_CHARACTER_CHECK_FAILED = -906,
	/** Hash check failed */
	IRUPGRADE_HASH_CHECK_FAILED = -907,
	/** Version check failed */
	IRUPGRADE_VERSION_CHECK_FAILED = -908,
	/** Package check failed */
	IRUPGRADE_PACKAGE_CHECK_FAILED = -909,
	/** Command channel unsupported */
	IRUPGRADE_CMD_CHANNEL_UNSUPPORTED = -910,
	/** Record device status failed */
	IRUPGRADE_RECORD_DEV_STATUS_FAILED = -911,
	/** Read whole package json data failed */
	IRUPGRADE_READ_JSON_DATA_FAILED = -912,
	/** Get information from zip package failed */
	IRUPGRADE_GET_ZIP_INFO_FAILED = -913,
	/** Get information from device failed */
	IRUPGRADE_GET_DEV_INFO_FAILED = -914,
	/** Modified data version failed */
	IRUPGRADE_SET_DATA_VERSION_FAILED = -915,
	/** Download firmware failed */
	IRUPGRADE_DOWNLOAD_FIRMWARE_FAILED = -916,
	/** Enter data update mode failed */
	IRUPGRADE_ENTER_DATA_UPGRADE_MODE_FAILED = -917,
	/** Leave data update mode failed */
	IRUPGRADE_LEAVE_DATA_UPGRADE_MODE_FAILED = -918,
	/** Parse device information failed */
	IRUPGRADE_PARSE_DEV_INFO_FAILED = -919,
	/** Parse whole package json failed */
	IRUPGRADE_PARSE_WHOLE_PACKAGE_JSON_FAILED = -920,
	/** Parse update package json failed */
	IRUPGRADE_PARSE_UPDATE_PACKAGE_JSON_FAILED = -921,
	/** Calculate file hash failed */
	IRUPGRADE_CALC_FILE_HASH_FAILED = -922,
	/** Write single file failed */
	IRUPGRADE_WRITE_SINGLE_FILE_FAILED = -923,
	/** Upgrade failed */
	IRUPGRADE_UPGRADE_FAILED = -924,
	/** Read single file failed*/
	IRUPGRADE_READ_SINGLE_FILE_FAILED = -925,
	/** uart device set param failed*/
	IRUPGRADE_UART_DEVICE_SET_PARAM_FAILED = -926,

	//libirparse
	/** Invalid parameter */
	IRPARSE_PARAM_ERROR = -1000,

	//libirtemp
	/** Invalid parameter */
	IRTEMP_PARAM_ERROR = -1100,
	/** Dynamic alloc memory failed  */
	IRTEMP_MEMORY_ALLOC_FAILED = -1101,
	/** File operation failed */
	IRTEMP_OPERATION_FILE_FAILED = -1102,
	/** Get pixel square array */
	IRTEMP_GET_PIXEL_SQUARE_FAILED = -1103,
	/** Get array average value failed */
	IRTEMP_GET_AVERAGE_VALUE_FAILED = -1104,
	/** Get pixel horizontal neighb array failed */
	IRTEMP_GET_PIXEL_HORZ_NEIGHB_FAILED = -1105,
	/** Get pixel vertical neighb array failed */
	IRTEMP_GET_PIXEL_VERT_NEIGHB_FAILED = -1106,
	/** Get pixel slope neighb array failed */
	IRTEMP_GET_PIXEL_SLOPE_NEIGHB_FAILED = -1107,
	/** Get point set of slope line failed */
	IRTEMP_GET_POINT_OF_SLOPE_LINE_FAILED = -1108,
	/** Get horizontal line temperture failed */
	IRTEMP_GET_HORZ_LINE_FAILED = -1109,
	/** Get vertical line temperture failed */
	IRTEMP_GET_VERT_LINE_FAILED = -1110,
	/** Get slope line temperture failed */
	IRTEMP_GET_SLOPE_LINE_FAILED = -1111,
	/** Reverse enhance distance temp correct failed */
	IRTEMP_REVERSE_TEMP_FAILED = -1112,
	/** Reverse nuc value with nuc table failed */
	IRTEMP_REVERSE_NUC_VALUE_FAILED = -1113,
	/** Calculate temperature environment factor with distance and environment temperature failed */
	IRTEMP_CALC_ENV_FACTOR_FAILED = -1114,
	/** Get index of kt array and bt array according vtemp failed */
	IRTEMP_GET_KTBT_INDEX_FAILED = -1115,
	/** Ktbt correct process failed */
	IRTEMP_CORRECT_KTBT_FAILED = -1116,
	/** Update kt and bt array by double point failed */
	IRTEMP_UPDATE_KTBT_BY_DOUBLE_POINT_FAILED = -1117,
	/** Calculate sitf and offset failed in libirtemp*/
	IRTEMP_CALC_SITF_AND_OFFSET_FAILED = -1118,
	/** Calculate target sitf and offset before ktbt correct failed */
	IRTEMP_CALC_SITF_AND_OFFSET_BEFORE_KTBT_FAILED = -1119,
	/** Get humidity read/write index depend on humidity failed */
	IRTEMP_GET_HUM_INDEX_FAILED = -1120,
	/** Get temperature read/write index depend on environment temperature failed */
	IRTEMP_GET_TEMP_INDEX_FAILED = -1121,
	/** Get distance read/write index depend on distance failed */
	IRTEMP_GET_DIST_INDEX_FAILED = -1122,
	/** Get compatible ems failed */
	IRTEMP_GET_EMS_FAILED = -1123,
	/** Get compatible tau with target temperature and distance failed */
	IRTEMP_GET_TAU_FAILED = -1124,
	/** Recalculate tau failed */
	IRTEMP_RECALC_TAU_FAILED = -1125,
	/** Read tau failed */
	IRTEMP_READ_TAU_FAILED = -1126,
	/** Temperature correct failed */
	IRTEMP_CORRECT_TEMP_FAILED = -1127,
	/** Reverse temperature correct failed */
	IRTEMP_REVERSE_TEMP_CORRECT_FAILED = -1128,
	/** Calculate vapor pressure failed */
	IRTEMP_CALC_VAPOR_PRESSURE_FAILED = -1129,
	/** Calculate coefficient failed */
	IRTEMP_CALC_COEF_FAILED = -1130,
	/** Calculate new nuc table by  multi-point */
	IRTEMP_CALC_MULTI_POINT_FAILED = -1131,
	/** LinearInterp failed */
	IRTEMP_LINEARINTERP_FAILED = -1132,
	/** Remap temp failed */
	IRTEMP_REMAP_TEMP_FAILED = -1133,

	//libirprocess
	/** Invalid parameter */
	IRPROC_PARAM_ERROR = -1200,
	/** Dynamic alloc memory failed */
	IRPROC_MEMORY_ALLOC_FAILED = -1201,
	/** Format unsupported */
	IRPROC_FMT_UNSUPPORTED = -1202,
	/** Get histogram result of image_y14 */
	IRPROC_GET_HISTOGRAM_FAILED = -1203,
	/** Get agc limit parameters via image_hist failed */
	IRPROC_GET_AGC_LIMIT_FAILED = -1204,
	/** Get limited accumulative histogram result failed */
	IRPROC_GET_LIMITED_ACC_HISTOGRAM_FAILED = -1205,
	/** Get new map list of the cumulative histogram failed */
	IRPROC_GET_NEW_MAP_FAILED = -1206,
	/** Get gauss kernel parameters failed */
	IRPROC_GET_GAUSS_KERNEL_FAILED = -1207,
	/** Gaussian filter failed */
	IRPROC_GAUSSIAN_FILTER_FAILED = -1208,
	/** Dde coefficient calculate failed */
	IRPROC_CALC_DDE_COEF_FAILED = -1209,
	/** Y14 image extract detail failed */
	IRPROC_EXTRACT_DETAIL_FAILED = -1210,
	/** y14 image auto gain control failed */
	IRPROC_CONTROL_AUTO_GAIN_FAILED = -1211,
	/** Y14 image digital detail enhance failed */
	IRPROC_ENHANCE_DIGITAL_DETAIL_FAILED = -1212,

	//libir_infoparse
	/** Invalid parameter */
	IRINFOPARSE_PARAM_ERROR = -1300,
	/** Crc check failed */
	IRINFOPARSE_CRC_CHECK_FAILED = -13401,
	/** Crc16 compare failed */
	IRINFOPARSE_CRC8_CMP_FAILED = -1302,

	//libircmdchannel
	/** Invalid parameter */
	IRCMDCHANNEL_PARAM_ERROR = -1400,
	/** init failed */
	IRCMDCHANNEL_INIT_FAILED = -1401,
	/** firmware upgrade failed */
	IRCMDCHANNEL_UPGRADE_FAILED = -1402,
	/** set value failed */
	IRCMDCHANNEL_SET_VALUE_FAILED = -1403,
	/** get value failed */
	IRCMDCHANNEL_GET_VALUE_FAILED = -1404,
	/** camera not init */
	IRCMDCHANNEL_CAMERA_NOT_INIT = -1405,
	/** camera start failed */
	IRCMDCHANNEL_CAMERA_START_FAILED = -1406,
	/** camera stop failed */
	IRCMDCHANNEL_CAMERA_STOP_FAILED = -1407,
	/** camera release failed*/
	IRCMDCHANNEL_CAMERA_RELEASE_FAILED = -1408,
	/** set target detect open failed*/
	IRCMDCHANNEL_SET_TARGET_DETECT_OPEN_FAILED = -1409,
	/** open shutter failed*/
	IRCMDCHANNEL_OPEN_SHUTTER_FAILED = -1410,
	/** closed shutter failed*/
	IRCMDCHANNEL_CLOSED_SHUTTER_FAILED = -1411,
	/** update shutter failed*/
	IRCMDCHANNEL_UPDATE_SHUTTER_FAILED = -1412,
} IrlibError_e;

#endif
