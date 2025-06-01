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
    }
    
    // Native method declarations
    private external fun nativeInit(fileDescriptor: Int): Boolean
    private external fun nativeCleanup()
    private external fun nativeGetLastError(): Int
    private external fun nativeGetLastErrorMessage(): String
    private external fun nativePerformFFC(): Int
    
    // State tracking
    private var isInitialized = false
    
    /**
     * Initialize the IrcmdManager with a file descriptor from a USB device
     * @param fileDescriptor The file descriptor from the USB device connection
     * @return true if initialization was successful, false otherwise
     */
    fun init(fileDescriptor: Int): Boolean {
        if (isInitialized) {
            Log.w(TAG, "IrcmdManager already initialized")
            return true
        }
        
        Log.i(TAG, "Initializing IrcmdManager with file descriptor $fileDescriptor")
        val success = nativeInit(fileDescriptor)
        
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
     * Perform a Flat Field Correction (FFC) update.
     * @return 0 on success, negative error code on failure
     */
    fun performFFC(): Int {
        return nativePerformFFC()
    }
} 