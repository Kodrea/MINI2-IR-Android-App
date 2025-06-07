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
            System.loadLibrary("ircmdircmd_handle_handle")
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
        
        // Camera function enumerations, must match C++ enum values
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
    }
    
    // Native method declarations
    private external fun nativeInit(fileDescriptor: Int, deviceType: Int): Boolean
    private external fun nativeCleanup()
    private external fun nativeGetLastError(): Int
    private external fun nativeGetLastErrorMessage(): String
    private external fun nativeExecuteGetFunction(functionId: Int, result: MutableIntWrapper): Int
    private external fun nativeExecuteSetFunction(functionId: Int, value: Int): Int
    private external fun nativeExecuteActionFunction(functionId: Int): Int
    
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
} 