package com.example.ircmd_handle

import android.util.Log

/**
 * Kotlin interface to the native IrcmdManager implementation.
 * This class provides a safe, Kotlin-friendly way to interact with the IRCMD SDK.
 */
class IrcmdManager private constructor() {
    companion object {
        private const val TAG = "IrcmdManager"
        
        // Load the native library
        init {
            System.loadLibrary("ircmd_handle")
        }
        
        // Singleton instance
        @Volatile
        private var instance: IrcmdManager? = null
        
        /**
         * Get the singleton instance of IrcmdManager
         */
        fun getInstance(): IrcmdManager {
            return instance ?: synchronized(this) {
                instance ?: IrcmdManager().also { instance = it }
            }
        }

        // Error codes
        const val ERROR_SUCCESS = 0
        const val ERROR_INVALID_PARAM = -1
        const val ERROR_NOT_INITIALIZED = -2
        const val ERROR_USB_WRITE = -3
        const val ERROR_USB_READ = -4
        const val ERROR_TIMEOUT = -5
        const val ERROR_UNKNOWN = -99
        
        // Registry error codes
        const val ERROR_FUNCTION_NOT_FOUND = -1001
        const val ERROR_INVALID_HANDLE = -1002
        const val ERROR_REGISTRY_ERROR = -1004
        
        // Function types for registry
        const val FUNCTION_TYPE_SET = 0
        const val FUNCTION_TYPE_GET = 1
        const val FUNCTION_TYPE_ACTION = 2
        
        // Camera function enumerations - LEGACY (keeping for backward compatibility)
        object CameraFunction {
            // Getter functions
            const val GET_BRIGHTNESS = 0
            
            // Setter functions
            const val SET_BRIGHTNESS = 1
            const val SET_CONTRAST = 2
            
            // Action functions
            const val PERFORM_FFC = 3
            
            // Palette functions
            const val SET_PALETTE = 4
            
            // Scene mode functions
            const val SET_SCENE_MODE = 5

            // Noise reduction and enhancement functions
            const val SET_NOISE_REDUCTION = 6
            const val SET_TIME_NOISE_REDUCTION = 7
            const val SET_SPACE_NOISE_REDUCTION = 8
            const val SET_DETAIL_ENHANCEMENT = 9
            const val SET_GLOBAL_CONTRAST = 10
        }
        
        // NEW: Registry-based function IDs (matches C++ CameraFunctionId enum)
        object CameraFunctionId {
            // Image processing functions
            const val BRIGHTNESS = 1000
            const val CONTRAST = 1001
            const val GLOBAL_CONTRAST = 1002
            const val DETAIL_ENHANCEMENT = 1003
            const val NOISE_REDUCTION = 1004
            const val ROI_LEVEL = 1005
            const val AGC_LEVEL = 1006
            
            // Scene and palette functions
            const val SCENE_MODE = 2000
            const val PALETTE_INDEX = 2001
            
            // Action functions
            const val FFC_UPDATE = 3000
            
            // Advanced functions for future expansion
            const val GAMMA_LEVEL = 4000
            const val EDGE_ENHANCE = 4001
            const val TIME_NOISE_REDUCTION = 4002
            const val SPACE_NOISE_REDUCTION = 4003
            
            // Device control functions (MINI2-compatible SET only)
            const val DEVICE_SLEEP = 5000
            const val ANALOG_VIDEO_OUTPUT = 5001
            const val OUTPUT_FRAME_RATE = 5002
            const val YUV_FORMAT = 5003
            const val SHUTTER_STATUS = 5004
            const val PICTURE_FREEZE = 5005
            const val MIRROR_AND_FLIP = 5006
            const val AUTO_FFC_STATUS = 5007
            const val ALL_FFC_FUNCTION_STATUS = 5008
        }
    }
    
    // Native method declarations - LEGACY (for backward compatibility)
    private external fun nativeInit(fileDescriptor: Int, deviceType: Int): Boolean
    private external fun nativeCleanup()
    private external fun nativeGetLastError(): Int
    private external fun nativeGetLastErrorMessage(): String
    private external fun nativeExecuteGetFunction(functionId: Int, result: MutableIntWrapper): Int
    private external fun nativeExecuteSetFunction(functionId: Int, value: Int): Int
    private external fun nativeExecuteActionFunction(functionId: Int): Int
    
    // NEW: Registry-based native method declarations
    private external fun nativeExecuteRegistrySetFunction(functionId: Int, value: Int): Int
    private external fun nativeExecuteRegistrySetFunction2(functionId: Int, value1: Int, value2: Int): Int
    private external fun nativeExecuteRegistryGetFunction(functionId: Int, result: MutableIntWrapper): Int
    private external fun nativeExecuteRegistryActionFunction(functionId: Int): Int
    private external fun nativeIsFunctionSupported(functionType: Int, functionId: Int): Boolean
    private external fun nativeGetRegisteredFunctionCount(): Int
    
    // Wrapper class for passing reference values via JNI
    class MutableIntWrapper(var value: Int)
    
    // State tracking
    private var isInitialized = false
    
    // Track last set values
    private var lastBrightnessValue = 50
    private var lastContrastValue = 50
    
    /**
     * Initialize the IrcmdManager with a file descriptor from a USB device
     * @param fileDescriptor The file descriptor from the USB device connection
     * @param deviceType The device type (3 for MINI2-384, 7 for MINI2-256)
     * @return true if initialization was successful, false otherwise
     */
    fun init(fileDescriptor: Int, deviceType: Int): Boolean {
        if (isInitialized) {
            Log.w(TAG, "IrcmdManager already initialized")
            return true
        }
        
        Log.i(TAG, "Initializing IrcmdManager with file descriptor $fileDescriptor and device type $deviceType")
        val success = nativeInit(fileDescriptor, deviceType)
        
        if (success) {
            isInitialized = true
            Log.i(TAG, "IrcmdManager initialized successfully")
        } else {
            val error = nativeGetLastError()
            val errorMsg = nativeGetLastErrorMessage()
            Log.e(TAG, "Failed to initialize IrcmdManager: $errorMsg (code: $error)")
        }
        
        return success
    }
    
    /**
     * Clean up resources used by the IrcmdManager
     */
    fun cleanup() {
        if (!isInitialized) {
            Log.d(TAG, "IrcmdManager not initialized, nothing to cleanup")
            return
        }
        
        Log.i(TAG, "Cleaning up IrcmdManager")
        nativeCleanup()
        isInitialized = false
    }
    
    /**
     * Get the last error code from the native implementation
     */
    fun getLastError(): Int = nativeGetLastError()
    
    /**
     * Get a human-readable error message for the last error
     */
    fun getLastErrorMessage(): String = nativeGetLastErrorMessage()
    
    /**
     * Check if the manager is currently initialized
     */
    fun isInitialized(): Boolean = isInitialized

    /**
     * Get the current brightness level
     * @return a pair of (result code, brightness value) where result code is 0 on success
     */
    fun getCurrentBrightness(): Pair<Int, Int> {
        val result = MutableIntWrapper(0)
        val code = nativeExecuteGetFunction(CameraFunction.GET_BRIGHTNESS, result)
        
        // Return the stored value if the native call fails
        return if (code == ERROR_SUCCESS) {
            Pair(code, result.value)
        } else {
            Pair(code, lastBrightnessValue)
        }
    }
    
    /**
     * Set the image brightness level
     * @param level The brightness level to set
     * @return 0 on success, negative error code on failure
     */
    fun setBrightness(level: Int): Int {
        val result = nativeExecuteSetFunction(CameraFunction.SET_BRIGHTNESS, level)
        if (result == ERROR_SUCCESS) {
            lastBrightnessValue = level
        }
        return result
    }
    
    /**
     * Set the image contrast level
     * @param level The contrast level to set
     * @return 0 on success, negative error code on failure
     */
    fun setContrast(level: Int): Int {
        val result = nativeExecuteSetFunction(CameraFunction.SET_CONTRAST, level)
        if (result == ERROR_SUCCESS) {
            lastContrastValue = level
        }
        return result
    }

    /**
     * Perform a Flat Field Correction (FFC) update.
     * @return 0 on success, negative error code on failure
     */
    fun performFFC(): Int {
        return nativeExecuteActionFunction(CameraFunction.PERFORM_FFC)
    }
    
    /**
     * Get the last set brightness value (without calling the device)
     * @return the last brightness value that was successfully set
     */
    fun getLastSetBrightness(): Int {
        return lastBrightnessValue
    }
    
    /**
     * Get the last set contrast value (without calling the device)
     * @return the last contrast value that was successfully set
     */
    fun getLastSetContrast(): Int {
        return lastContrastValue
    }

    /**
     * Set the color palette index
     * @param index The palette index (0-11)
     * @return 0 on success, negative error code on failure
     */
    fun setPalette(index: Int): Int {
        // Ensure value is within valid range (0-11)
        if (index < 0 || index > 11) {
            Log.e(TAG, "Invalid palette index: $index")
            return ERROR_INVALID_PARAM
        }
        return nativeExecuteSetFunction(CameraFunction.SET_PALETTE, index)
    }

    /**
     * Set the scene mode
     * @param mode The scene mode (0-11)
     * @return 0 on success, negative error code on failure
     */
    fun setSceneMode(mode: Int): Int {
        // Ensure value is within valid range (0-11)
        if (mode < 0 || mode > 11) {
            Log.e(TAG, "Invalid scene mode: $mode")
            return ERROR_INVALID_PARAM
        }
        return nativeExecuteSetFunction(CameraFunction.SET_SCENE_MODE, mode)
    }

    /**
     * Set the noise reduction level
     * @param level The noise reduction level (0-100)
     * @return 0 on success, negative error code on failure
     */
    fun setNoiseReduction(level: Int): Int {
        // Ensure value is within valid range (0-100)
        if (level < 0 || level > 100) {
            Log.e(TAG, "Invalid noise reduction level: $level")
            return ERROR_INVALID_PARAM
        }
        return nativeExecuteSetFunction(CameraFunction.SET_NOISE_REDUCTION, level)
    }

    /**
     * Set the time noise reduction level
     * @param level The time noise reduction level (0-100)
     * @return 0 on success, negative error code on failure
     */
    fun setTimeNoiseReduction(level: Int): Int {
        // Ensure value is within valid range (0-100)
        if (level < 0 || level > 100) {
            Log.e(TAG, "Invalid time noise reduction level: $level")
            return ERROR_INVALID_PARAM
        }
        return nativeExecuteSetFunction(CameraFunction.SET_TIME_NOISE_REDUCTION, level)
    }

    /**
     * Set the space noise reduction level
     * @param level The space noise reduction level (0-100)
     * @return 0 on success, negative error code on failure
     */
    fun setSpaceNoiseReduction(level: Int): Int {
        // Ensure value is within valid range (0-100)
        if (level < 0 || level > 100) {
            Log.e(TAG, "Invalid space noise reduction level: $level")
            return ERROR_INVALID_PARAM
        }
        return nativeExecuteSetFunction(CameraFunction.SET_SPACE_NOISE_REDUCTION, level)
    }

    /**
     * Set the detail enhancement level
     * @param level The detail enhancement level (0-100)
     * @return 0 on success, negative error code on failure
     */
    fun setDetailEnhancement(level: Int): Int {
        // Ensure value is within valid range (0-100)
        if (level < 0 || level > 100) {
            Log.e(TAG, "Invalid detail enhancement level: $level")
            return ERROR_INVALID_PARAM
        }
        return nativeExecuteSetFunction(CameraFunction.SET_DETAIL_ENHANCEMENT, level)
    }

    /**
     * Set the global contrast level
     * @param level The global contrast level (0-100)
     * @return 0 on success, negative error code on failure
     */
    fun setGlobalContrast(level: Int): Int {
        // Ensure value is within valid range (0-100)
        if (level < 0 || level > 100) {
            Log.e(TAG, "Invalid global contrast level: $level")
            return ERROR_INVALID_PARAM
        }
        return nativeExecuteSetFunction(CameraFunction.SET_GLOBAL_CONTRAST, level)
    }

    /**
     * Execute a set function with a value parameter
     * @param functionId The function ID from CameraFunction
     * @param value The value to set
     * @return 0 on success, negative error code on failure
     */
    fun executeSetFunction(functionId: Int, value: Int): Int {
        if (!isInitialized) {
            Log.e(TAG, "Cannot execute function: IrcmdManager not initialized")
            return ERROR_NOT_INITIALIZED
        }
        return nativeExecuteSetFunction(functionId, value)
    }
    
    // ===== NEW REGISTRY-BASED FUNCTIONS =====
    
    /**
     * Execute a registry-based set function with a value parameter
     * @param functionId The function ID from CameraFunctionId
     * @param value The value to set
     * @return 0 on success, negative error code on failure
     */
    fun executeRegistrySetFunction(functionId: Int, value: Int): Int {
        if (!isInitialized) {
            Log.e(TAG, "Cannot execute registry function: IrcmdManager not initialized")
            return ERROR_NOT_INITIALIZED
        }
        
        Log.d(TAG, "Executing registry SET function $functionId with value $value")
        return nativeExecuteRegistrySetFunction(functionId, value)
    }
    
    /**
     * Execute a registry-based set function with two value parameters
     * @param functionId The function ID from CameraFunctionId
     * @param value1 The first value to set
     * @param value2 The second value to set
     * @return 0 on success, negative error code on failure
     */
    fun executeRegistrySetFunction2(functionId: Int, value1: Int, value2: Int): Int {
        if (!isInitialized) {
            Log.e(TAG, "Cannot execute registry function: IrcmdManager not initialized")
            return ERROR_NOT_INITIALIZED
        }
        
        Log.d(TAG, "Executing registry SET2 function $functionId with values $value1, $value2")
        return nativeExecuteRegistrySetFunction2(functionId, value1, value2)
    }
    
    /**
     * Execute a registry-based get function
     * @param functionId The function ID from CameraFunctionId
     * @return a pair of (result code, value) where result code is 0 on success
     */
    fun executeRegistryGetFunction(functionId: Int): Pair<Int, Int> {
        if (!isInitialized) {
            Log.e(TAG, "Cannot execute registry function: IrcmdManager not initialized")
            return Pair(ERROR_NOT_INITIALIZED, 0)
        }
        
        val result = MutableIntWrapper(0)
        val code = nativeExecuteRegistryGetFunction(functionId, result)
        
        Log.d(TAG, "Executed registry GET function $functionId: code=$code, value=${result.value}")
        return Pair(code, result.value)
    }
    
    // ===== FRAMERATE CONTROL FUNCTIONS =====
    
    /**
     * Set the camera output framerate
     * @param frameRate The framerate to set (25 or 50 for MINI2-256)
     * @return 0 on success, negative error code on failure
     */
    fun setFrameRate(frameRate: Int): Int {
        if (!isInitialized) {
            Log.e(TAG, "Cannot set framerate: IrcmdManager not initialized")
            return ERROR_NOT_INITIALIZED
        }
        
        Log.i(TAG, "Setting camera framerate to ${frameRate}fps")
        
        if (frameRate != 25 && frameRate != 50) {
            Log.e(TAG, "Invalid framerate for MINI2-256: $frameRate. Valid values are 25 or 50")
            return ERROR_INVALID_PARAM
        }
        
        // Try different approaches based on SDK enum definitions
        Log.i(TAG, "Attempting framerate change with multiple strategies...")
        
        // Strategy 1: Use direct MINI2-256 enum values (25, 50)
        Log.i(TAG, "Strategy 1: Direct MINI2-256 values (ADV_RATE_25=25, ADV_RATE_50=50)")
        var result = executeRegistrySetFunction(CameraFunctionId.OUTPUT_FRAME_RATE, frameRate)
        
        if (result == ERROR_SUCCESS) {
            Log.i(TAG, "✅ Strategy 1 succeeded: Direct MINI2-256 enum values")
            return result
        }
        
        // Strategy 2: Map to standard enum values (25→30, 50→60)
        val mappedRate = if (frameRate == 25) 30 else 60
        Log.i(TAG, "Strategy 2: Mapped to standard values (${frameRate}fps → ${mappedRate}fps)")
        result = executeRegistrySetFunction(CameraFunctionId.OUTPUT_FRAME_RATE, mappedRate)
        
        if (result == ERROR_SUCCESS) {
            Log.i(TAG, "✅ Strategy 2 succeeded: Mapped to standard enum values")
            return result
        }
        
        // Strategy 3: Try alternative enum indices (0=low, 1=high)
        val indexRate = if (frameRate == 25) 0 else 1
        Log.i(TAG, "Strategy 3: Enum indices (${frameRate}fps → index $indexRate)")
        result = executeRegistrySetFunction(CameraFunctionId.OUTPUT_FRAME_RATE, indexRate)
        
        if (result == ERROR_SUCCESS) {
            Log.i(TAG, "✅ Strategy 3 succeeded: Enum index values")
            return result
        }
        
        Log.e(TAG, "❌ All strategies failed. SDK may not support framerate control for this device.")
        return result // Return the last error code
    }
    
    /**
     * Check if framerate control is supported for this device
     * @return true if framerate control is supported
     */
    fun isFrameRateControlSupported(): Boolean {
        return isFunctionSupported(FUNCTION_TYPE_SET, CameraFunctionId.OUTPUT_FRAME_RATE)
    }
    
    /**
     * Execute a registry-based action function
     * @param functionId The function ID from CameraFunctionId
     * @return 0 on success, negative error code on failure
     */
    fun executeRegistryActionFunction(functionId: Int): Int {
        if (!isInitialized) {
            Log.e(TAG, "Cannot execute registry function: IrcmdManager not initialized")
            return ERROR_NOT_INITIALIZED
        }
        
        Log.d(TAG, "Executing registry ACTION function $functionId")
        return nativeExecuteRegistryActionFunction(functionId)
    }
    
    /**
     * Check if a function is supported by the registry
     * @param functionType The function type (FUNCTION_TYPE_SET, FUNCTION_TYPE_GET, FUNCTION_TYPE_ACTION)
     * @param functionId The function ID from CameraFunctionId
     * @return true if supported, false otherwise
     */
    fun isFunctionSupported(functionType: Int, functionId: Int): Boolean {
        return nativeIsFunctionSupported(functionType, functionId)
    }
    
    /**
     * Get the total number of registered functions
     * @return the number of registered functions
     */
    fun getRegisteredFunctionCount(): Int {
        return nativeGetRegisteredFunctionCount()
    }
    
    /**
     * NEW: Set brightness using registry-based approach
     * @param level The brightness level (0-100)
     * @return 0 on success, negative error code on failure
     */
    fun setRegistryBrightness(level: Int): Int {
        return executeRegistrySetFunction(Companion.CameraFunctionId.BRIGHTNESS, level)
    }
    
    /**
     * NEW: Get brightness using registry-based approach
     * @return a pair of (result code, brightness value) where result code is 0 on success
     */
    fun getRegistryBrightness(): Pair<Int, Int> {
        return executeRegistryGetFunction(Companion.CameraFunctionId.BRIGHTNESS)
    }
    
    /**
     * NEW: Set contrast using registry-based approach
     * @param level The contrast level (0-100)
     * @return 0 on success, negative error code on failure
     */
    fun setRegistryContrast(level: Int): Int {
        return executeRegistrySetFunction(Companion.CameraFunctionId.CONTRAST, level)
    }
    
    /**
     * NEW: Perform FFC using registry-based approach
     * @return 0 on success, negative error code on failure
     */
    fun performRegistryFFC(): Int {
        return executeRegistryActionFunction(Companion.CameraFunctionId.FFC_UPDATE)
    }
    
    /**
     * NEW: Set palette using registry-based approach
     * @param index The palette index (0-11)
     * @return 0 on success, negative error code on failure
     */
    fun setRegistryPalette(index: Int): Int {
        if (index < 0 || index > 11) {
            Log.e(TAG, "Invalid palette index: $index")
            return ERROR_INVALID_PARAM
        }
        return executeRegistrySetFunction(Companion.CameraFunctionId.PALETTE_INDEX, index)
    }
    
    /**
     * NEW: Set scene mode using registry-based approach
     * @param mode The scene mode (0-11)
     * @return 0 on success, negative error code on failure
     */
    fun setRegistrySceneMode(mode: Int): Int {
        if (mode < 0 || mode > 11) {
            Log.e(TAG, "Invalid scene mode: $mode")
            return ERROR_INVALID_PARAM
        }
        return executeRegistrySetFunction(Companion.CameraFunctionId.SCENE_MODE, mode)
    }
    
    /**
     * NEW: Set noise reduction using registry-based approach
     * @param level The noise reduction level (0-100)
     * @return 0 on success, negative error code on failure
     */
    fun setRegistryNoiseReduction(level: Int): Int {
        if (level < 0 || level > 100) {
            Log.e(TAG, "Invalid noise reduction level: $level")
            return ERROR_INVALID_PARAM
        }
        return executeRegistrySetFunction(Companion.CameraFunctionId.NOISE_REDUCTION, level)
    }
    
    /**
     * NEW: Set detail enhancement using registry-based approach
     * @param level The detail enhancement level (0-100)
     * @return 0 on success, negative error code on failure
     */
    fun setRegistryDetailEnhancement(level: Int): Int {
        if (level < 0 || level > 100) {
            Log.e(TAG, "Invalid detail enhancement level: $level")
            return ERROR_INVALID_PARAM
        }
        return executeRegistrySetFunction(Companion.CameraFunctionId.DETAIL_ENHANCEMENT, level)
    }
    
    /**
     * NEW: Set global contrast using registry-based approach
     * @param level The global contrast level (0-100)
     * @return 0 on success, negative error code on failure
     */
    fun setRegistryGlobalContrast(level: Int): Int {
        if (level < 0 || level > 100) {
            Log.e(TAG, "Invalid global contrast level: $level")
            return ERROR_INVALID_PARAM
        }
        return executeRegistrySetFunction(Companion.CameraFunctionId.GLOBAL_CONTRAST, level)
    }
    
    // ===== NEW MINI2 DEVICE CONTROL FUNCTIONS =====
    
    /**
     * Set device sleep status
     * @param status 0=disable sleep, 1=enable sleep
     * @return 0 on success, negative error code on failure
     */
    fun setDeviceSleep(status: Int): Int {
        if (status < 0 || status > 1) {
            Log.e(TAG, "Invalid device sleep status: $status")
            return ERROR_INVALID_PARAM
        }
        return executeRegistrySetFunction(Companion.CameraFunctionId.DEVICE_SLEEP, status)
    }
    
    /**
     * Set analog video output configuration
     * @param status 0=disable, 1=enable
     * @param format Video format (0=NTSC, 1=PAL)
     * @return 0 on success, negative error code on failure
     */
    fun setAnalogVideoOutput(status: Int, format: Int): Int {
        if (status < 0 || status > 1) {
            Log.e(TAG, "Invalid analog video output status: $status")
            return ERROR_INVALID_PARAM
        }
        if (format < 0 || format > 1) {
            Log.e(TAG, "Invalid analog video output format: $format")
            return ERROR_INVALID_PARAM
        }
        return executeRegistrySetFunction2(Companion.CameraFunctionId.ANALOG_VIDEO_OUTPUT, status, format)
    }
    
    /**
     * Set output frame rate
     * @param rate Frame rate setting (device-specific values)
     * @return 0 on success, negative error code on failure
     */
    fun setOutputFrameRate(rate: Int): Int {
        return executeRegistrySetFunction(Companion.CameraFunctionId.OUTPUT_FRAME_RATE, rate)
    }
    
    /**
     * Set YUV format
     * @param format YUV format (0=YUV422, 1=YUV420, 2=YUV444)
     * @return 0 on success, negative error code on failure
     */
    fun setYuvFormat(format: Int): Int {
        if (format < 0 || format > 2) {
            Log.e(TAG, "Invalid YUV format: $format")
            return ERROR_INVALID_PARAM
        }
        return executeRegistrySetFunction(Companion.CameraFunctionId.YUV_FORMAT, format)
    }
    
    /**
     * Set shutter status
     * @param status 0=close shutter, 1=open shutter
     * @return 0 on success, negative error code on failure
     */
    fun setShutterStatus(status: Int): Int {
        if (status < 0 || status > 1) {
            Log.e(TAG, "Invalid shutter status: $status")
            return ERROR_INVALID_PARAM
        }
        return executeRegistrySetFunction(Companion.CameraFunctionId.SHUTTER_STATUS, status)
    }
    
    /**
     * Set picture freeze status
     * @param status 0=unfreeze, 1=freeze
     * @return 0 on success, negative error code on failure
     */
    fun setPictureFreeze(status: Int): Int {
        if (status < 0 || status > 1) {
            Log.e(TAG, "Invalid picture freeze status: $status")
            return ERROR_INVALID_PARAM
        }
        return executeRegistrySetFunction(Companion.CameraFunctionId.PICTURE_FREEZE, status)
    }
    
    /**
     * Set mirror and flip configuration
     * @param value Bit field: bit0=horizontal flip, bit1=vertical flip
     * @return 0 on success, negative error code on failure
     */
    fun setMirrorAndFlip(value: Int): Int {
        if (value < 0 || value > 3) {
            Log.e(TAG, "Invalid mirror and flip value: $value")
            return ERROR_INVALID_PARAM
        }
        return executeRegistrySetFunction(Companion.CameraFunctionId.MIRROR_AND_FLIP, value)
    }
    
    /**
     * Set auto FFC status
     * @param status 0=disable auto FFC, 1=enable auto FFC
     * @return 0 on success, negative error code on failure
     */
    fun setAutoFfcStatus(status: Int): Int {
        if (status < 0 || status > 1) {
            Log.e(TAG, "Invalid auto FFC status: $status")
            return ERROR_INVALID_PARAM
        }
        return executeRegistrySetFunction(Companion.CameraFunctionId.AUTO_FFC_STATUS, status)
    }
    
    /**
     * Set all FFC function status
     * @param status 0=disable all FFC functions, 1=enable all FFC functions
     * @return 0 on success, negative error code on failure
     */
    fun setAllFfcFunctionStatus(status: Int): Int {
        if (status < 0 || status > 1) {
            Log.e(TAG, "Invalid all FFC function status: $status")
            return ERROR_INVALID_PARAM
        }
        return executeRegistrySetFunction(Companion.CameraFunctionId.ALL_FFC_FUNCTION_STATUS, status)
    }
} 