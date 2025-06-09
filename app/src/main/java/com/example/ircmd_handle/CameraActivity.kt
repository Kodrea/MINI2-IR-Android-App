package com.example.ircmd_handle

import android.Manifest
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.Toast
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.ViewCompat
import androidx.core.widget.NestedScrollView
import android.content.pm.ActivityInfo
import android.view.WindowManager
import android.graphics.Color
import android.view.Window
import android.graphics.SurfaceTexture
import android.content.res.Configuration
import android.widget.TextView
import android.app.AlertDialog
import android.widget.EditText
import android.widget.Spinner
import com.google.android.material.textfield.TextInputEditText
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import android.view.LayoutInflater
import android.widget.AdapterView
import android.widget.ArrayAdapter
import org.tensorflow.lite.Interpreter
import android.graphics.Bitmap
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.ByteOrder
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.*
import com.example.ircmd_handle.databinding.ActivityCameraBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*
import kotlinx.coroutines.delay
import androidx.lifecycle.lifecycleScope
import kotlin.math.pow

class CameraActivity : AppCompatActivity() {

    init {
        Log.i(TAG, "CameraActivity class being initialized")
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val ACTION_USB_PERMISSION = "android.hardware.usb.action.USB_PERMISSION"
        private const val PERMISSION_REQUEST_TIMEOUT = 5000L // 5 seconds
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        private const val STORAGE_PERMISSION_REQUEST_CODE = 1002
        private const val AUDIO_PERMISSION_REQUEST_CODE = 1003
        
        // Thermal Camera Co.,Ltd device IDs
        private const val VENDOR_ID = 13428  // 0x3474
        private const val DEVICE_CLASS = 239
        private const val DEVICE_SUBCLASS = 2

        // Intent extra to find device manually
        const val EXTRA_FIND_DEVICE = "com.example.ircmd_handle.FIND_DEVICE"
        
        // Palette names and limits
        private val PALETTE_NAMES = arrayOf(
            "White Hot",           /* 0 */
            "Reserved",           /* 1 */
            "Sepia",             /* 2 */
            "Ironbow",           /* 3 */
            "Rainbow",           /* 4 */
            "Night",             /* 5 */
            "Aurora",            /* 6 */
            "Red Hot",           /* 7 */
            "Jungle",            /* 8 */
            "Medical",           /* 9 */
            "Black Hot",         /* 10 */
            "Golden Red Glory"   /* 11 */
        )
        private const val MAX_PALETTE_INDEX = 11
        private const val MIN_PALETTE_INDEX = 0
        
        // Scene mode names and limits
        private val SCENE_MODE_NAMES = arrayOf(
            "Low",                /* 0 */
            "Linear Stretch",     /* 1 */
            "Low Contrast",       /* 2 */
            "General Mode",       /* 3 */
            "High Contrast",      /* 4 */
            "Highlight",         /* 5 */
            "Reserved 1",        /* 6 */
            "Reserved 2",        /* 7 */
            "Reserved 3",        /* 8 */
            "Outline Mode",      /* 9 */
            "unkown",        /* 10 */
            "unkown",        /* 11 */
        )
        private const val MAX_SCENE_MODE_INDEX = 11
        private const val MIN_SCENE_MODE_INDEX = 0
        
        // Load the native library
        init {
            System.loadLibrary("ircmd_handle")
        }
        
        // Add flag to prevent activity recreation
        private var isHandlingOrientationChange = false
        
        // Add flag to track if we're handling a USB device attachment
        private var isHandlingUsbAttachment = false
    }

    // ViewBinding
    private lateinit var binding: ActivityCameraBinding
    
    // TensorFlow Lite for super resolution
    private var fsrcnnInterpreter: Interpreter? = null
    
    // Video recording
    private lateinit var videoRecorder: VideoRecorder
    private var recordingDurationUpdateJob: Job? = null
    private var displaySurface: Surface? = null
    private var recordingSurface: Surface? = null
    
    // Native methods for raw frame capture
    private external fun nativeSetCaptureFlag(capture: Boolean)
    private external fun nativeHasCapturedFrame(): Boolean
    private external fun nativeGetCapturedFrame(): ByteArray?
    
    // Native methods for UVC framerate control
    private external fun nativeGetSupportedFrameRates(width: Int, height: Int): IntArray?
    private external fun nativeSetFrameRate(width: Int, height: Int, fps: Int): Boolean
    private external fun nativeGetCurrentFrameRate(): Int
    private external fun nativeEnumerateAllFrameRates()
    
    private lateinit var usbManager: UsbManager
    private var deviceConnection: UsbDeviceConnection? = null
    private var currentDevice: UsbDevice? = null
    private var permissionRequestTime: Long = 0

    // 1) A PendingIntent that we'll use when calling requestPermission(...)
    private lateinit var permissionIntent: PendingIntent

    // 2) BroadcastReceiver to catch the user's response (Allow / Deny) to the permission dialog
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "usbPermissionReceiver: Received broadcast: ${intent.action}")
            if (intent.action == ACTION_USB_PERMISSION) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    val granted: Boolean = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.i(TAG, "usbPermissionReceiver: device=$device, granted=$granted")

                    if (device != null && device == currentDevice) {
                        if (granted) {
                            Log.i(TAG, "usbPermissionReceiver: Permission granted, opening camera")
                            openUsbCamera(device)
                        } else {
                            Log.i(TAG, "usbPermissionReceiver: Permission denied")
                            showError("USB permission denied. Camera cannot be opened.") {
                                finish()
                            }
                        }
                    } else {
                        Log.w(TAG, "usbPermissionReceiver: Device mismatch or null. Current: $currentDevice, Received: $device")
                    }

                    try {
                        unregisterReceiver(this)
                        Log.i(TAG, "usbPermissionReceiver: Unregistered receiver")
                    } catch (e: Exception) {
                        Log.e(TAG, "usbPermissionReceiver: Error unregistering receiver", e)
                    }
                }
            }
        }
    }

    // Use WeakReference to prevent memory leaks
    private var permissionCheckJob: Job? = null

    // Add IrcmdManager instance
    private lateinit var ircmdManager: IrcmdManager

    private var isFullscreen = false
    private lateinit var windowInsetsController: WindowInsetsControllerCompat
    private var originalCameraParams: ConstraintLayout.LayoutParams? = null

    private var currentPaletteIndex = 0
    private var currentSceneModeIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: Starting CameraActivity initialization")
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Hide the action bar by default
        supportActionBar?.hide()
        
        Log.i(TAG, "onCreate: Starting activity initialization")
        
        // Prevent activity recreation on orientation change
        if (savedInstanceState != null) {
            isHandlingOrientationChange = savedInstanceState.getBoolean("isHandlingOrientationChange", false)
        }
        
        try {
            // Initialize window insets controller for immersive mode
            windowInsetsController = WindowCompat.getInsetsController(window, window.decorView)
            // Configure the behavior of the hidden system bars
            windowInsetsController.systemBarsBehavior = 
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            
            // Add a listener to update the behavior of the toggle fullscreen button
            ViewCompat.setOnApplyWindowInsetsListener(window.decorView) { view, windowInsets ->
                // Update button behavior based on system bars visibility
                if (windowInsets.isVisible(WindowInsetsCompat.Type.navigationBars()) ||
                    windowInsets.isVisible(WindowInsetsCompat.Type.statusBars())) {
                    binding.fullscreenButton.setImageResource(R.drawable.ic_fullscreen)
                } else {
                    binding.fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit)
                }
                ViewCompat.onApplyWindowInsets(view, windowInsets)
            }
            
            // Initialize IrcmdManager
            ircmdManager = IrcmdManager.getInstance()
            
            // Views are now available through binding
            Log.i(TAG, "onCreate: Views initialized through ViewBinding")
            
            // Store original camera container parameters
            originalCameraParams = binding.cameraContainer.layoutParams as ConstraintLayout.LayoutParams
            
            // Set up UI click listeners using ViewBinding
            binding.fullscreenButton.setOnClickListener {
                toggleFullscreen()
            }

            binding.ffcButton.setOnClickListener {
                performFFC()
            }

            binding.captureEnhanceButton.setOnClickListener {
                captureAndEnhanceFrame()
            }

            binding.recordButton.setOnClickListener {
                if (videoRecorder.isRecording()) {
                    stopVideoRecording()
                } else {
                    startVideoRecording()
                }
            }

            binding.pauseResumeButton.setOnClickListener {
                if (videoRecorder.isPaused()) {
                    resumeVideoRecording()
                } else {
                    pauseVideoRecording()
                }
            }

            binding.setBrightnessButton.setOnClickListener {
                setBrightness(binding.brightnessSlider.progress)
            }

            binding.setContrastButton.setOnClickListener {
                setContrast(binding.contrastSlider.progress)
            }

            binding.setNoiseReductionButton.setOnClickListener {
                setNoiseReduction(binding.noiseReductionSlider.progress)
            }

            binding.setTimeNoiseReductionButton.setOnClickListener {
                setTimeNoiseReduction(binding.timeNoiseReductionSlider.progress)
            }

            binding.setSpaceNoiseReductionButton.setOnClickListener {
                setSpaceNoiseReduction(binding.spaceNoiseReductionSlider.progress)
            }

            binding.setDetailEnhancementButton.setOnClickListener {
                setDetailEnhancement(binding.detailEnhancementSlider.progress)
            }

            binding.setGlobalContrastButton.setOnClickListener {
                setGlobalContrast(binding.globalContrastSlider.progress)
            }
            
            binding.nextPaletteButton.setOnClickListener {
                setPalette((currentPaletteIndex + 1).coerceIn(MIN_PALETTE_INDEX, MAX_PALETTE_INDEX))
            }
            
            binding.prevPaletteButton.setOnClickListener {
                setPalette((currentPaletteIndex - 1).coerceIn(MIN_PALETTE_INDEX, MAX_PALETTE_INDEX))
            }
            
            binding.nextSceneModeButton.setOnClickListener {
                setSceneMode((currentSceneModeIndex + 1).coerceIn(MIN_SCENE_MODE_INDEX, MAX_SCENE_MODE_INDEX))
            }
            
            binding.prevSceneModeButton.setOnClickListener {
                setSceneMode((currentSceneModeIndex - 1).coerceIn(MIN_SCENE_MODE_INDEX, MAX_SCENE_MODE_INDEX))
            }
            
            binding.frameRate25Button.setOnClickListener {
                setFrameRate(25)
            }
            
            binding.frameRate50Button.setOnClickListener {
                setFrameRate(50)
            }

            // Set up expandable groups
            setupExpandableGroup(binding.imageSettingsHeader, binding.imageSettingsContent)
            setupExpandableGroup(binding.noiseReductionHeader, binding.noiseReductionContent)

            // 2) Get the system UsbManager
            usbManager = getSystemService(UsbManager::class.java)
            Log.i(TAG, "onCreate: Got UsbManager instance")

            // 3) Register our BroadcastReceiver for ACTION_USB_PERMISSION
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(
                        usbPermissionReceiver,
                        filter,
                        Context.RECEIVER_NOT_EXPORTED
                    )
                } else {
                    registerReceiver(usbPermissionReceiver, filter)
                }
                Log.i(TAG, "onCreate: Successfully registered USB permission receiver")
            } catch (e: Exception) {
                Log.e(TAG, "onCreate: Failed to register receiver", e)
                showError("Failed to register USB receiver: ${e.message}") {
                    finish()
                }
                return
            }

            // 4) Handle the Intent that launched this Activity
            Log.i(TAG, "onCreate: received intent: $intent")
            if (intent != null) {
                if (intent.getBooleanExtra(EXTRA_FIND_DEVICE, false)) {
                    // Button click requested to find the device
                    checkAndRequestCameraPermission {
                        findAndConnectCamera()
                    }
                } else if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                    // USB device was attached
                    isHandlingUsbAttachment = true
                    checkAndRequestCameraPermission {
                        handleIncomingUsbIntent(intent)
                    }
                } else {
                    // Normal USB intent handling
                    checkAndRequestCameraPermission {
                        handleIncomingUsbIntent(intent)
                    }
                }
            }
            
            // Initialize TensorFlow Lite model for super resolution
            initializeSuperResolutionModel()
            
            // Initialize video recorder
            initializeVideoRecorder()
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            showError("Error initializing camera: ${e.message}") {
                finish()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        Log.i(TAG, "onNewIntent: received intent: $intent")
        handleIncomingUsbIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("isHandlingOrientationChange", isHandlingOrientationChange)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Handle configuration changes manually
        if (isHandlingOrientationChange) {
            updateLayoutForOrientation(newConfig.orientation)
        }
    }

    private fun updateLayoutForOrientation(orientation: Int) {
        if (isFullscreen) {
            // In fullscreen mode, we want to show 4:3 video centered in landscape
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels
            
            // Calculate dimensions to maintain 4:3 aspect ratio
            val targetWidth: Int
            val targetHeight: Int
            
            if (screenWidth > screenHeight) { // Landscape
                // In landscape, height is the limiting factor
                targetHeight = screenHeight
                targetWidth = (targetHeight * 4) / 3
            } else { // Portrait
                // In portrait, width is the limiting factor
                targetWidth = screenWidth
                targetHeight = (targetWidth * 3) / 4
            }
            
            // Create layout params for the camera container
            val params = ConstraintLayout.LayoutParams(
                targetWidth,
                targetHeight
            ).apply {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                // Add margins to ensure it's not cut off
                setMargins(0, 48, 0, 48)
            }
            binding.cameraContainer.layoutParams = params
            
            // Update TextureView to fill the container while maintaining aspect ratio
            val textureParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            binding.cameraView.layoutParams = textureParams
            
            Log.i(TAG, "Fullscreen dimensions - Screen: ${screenWidth}x${screenHeight}, Video: ${targetWidth}x${targetHeight}")
            
        } else {
            // Portrait mode - use constraints from layout file
            val params = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                0
            ).apply {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToTop = binding.controlsContainer.id
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                // Add top margin to ensure it's not cut off
                topMargin = 48
                
                // Restore the camera's aspect ratio if we have dimensions
                val dimensions = nativeGetCameraDimensions()
                if (dimensions != null) {
                    dimensionRatio = "${dimensions.first}:${dimensions.second}"
                }
            }
            binding.cameraContainer.layoutParams = params
            
            val textureParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            binding.cameraView.layoutParams = textureParams
        }
    }

    private fun isOurDevice(device: UsbDevice): Boolean {
        // Only check vendor ID and device class/subclass
        // Remove product ID check to allow any device from our vendor
        return device.vendorId == VENDOR_ID &&
               device.deviceClass == DEVICE_CLASS &&
               device.deviceSubclass == DEVICE_SUBCLASS
    }

    private fun getDeviceConfig(device: UsbDevice): DeviceConfig? {
        return DeviceConfigs.configs[device.productId]
    }
    
    private fun showError(message: String, action: (() -> Unit)? = null) {
        val snackbar = Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG)
        action?.let { 
            snackbar.setAction("Retry") { it() }
        }
        snackbar.show()
    }
    
    private fun showSuccess(message: String) {
        Snackbar.make(binding.root, message, Snackbar.LENGTH_SHORT).show()
    }
    
    private fun executeCameraCommand(
        commandName: String,
        value: Int,
        operation: suspend () -> Int
    ) {
        if (!ircmdManager.isInitialized()) {
            Log.e(TAG, "$commandName failed: Camera not initialized")
            updateLastCommand(commandName, value, false, "Camera not initialized", IrcmdManager.ERROR_NOT_INITIALIZED)
            showError("$commandName failed: Camera not initialized")
            return
        }

        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    operation()
                }
                
                when (result) {
                    IrcmdManager.ERROR_SUCCESS -> {
                        Log.i(TAG, "$commandName set to $value successfully")
                        updateLastCommand(commandName, value, true)
                        showSuccess("$commandName set to $value")
                    }
                    IrcmdManager.ERROR_NOT_INITIALIZED -> {
                        Log.e(TAG, "$commandName failed: Camera not initialized (code: $result)")
                        updateLastCommand(commandName, value, false, "Camera not initialized", result)
                        showError("$commandName failed: Camera not initialized")
                    }
                    else -> {
                        val errorMsg = ircmdManager.getLastErrorMessage()
                        Log.e(TAG, "$commandName failed: $errorMsg (code: $result)")
                        updateLastCommand(commandName, value, false, errorMsg, result)
                        showError("$commandName failed: $errorMsg") {
                            executeCameraCommand(commandName, value, operation)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error executing $commandName", e)
                updateLastCommand(commandName, value, false, e.message ?: "Unknown error", -99)
                showError("$commandName failed: ${e.message}") {
                    executeCameraCommand(commandName, value, operation)
                }
            }
        }
    }

    private fun handleIncomingUsbIntent(intent: Intent) {
        Log.i(TAG, "handleIncomingUsbIntent: Starting USB intent handling for action: ${intent.action}")
        
        // Extract the UsbDevice from either the action or extras
        val device: UsbDevice? = when {
            intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
            }
            intent.hasExtra(UsbManager.EXTRA_DEVICE) -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
            }
            else -> null
        }

        if (device != null) {
            Log.i(TAG, "Device detected - VID: 0x${device.vendorId.toString(16)}, PID: 0x${device.productId.toString(16)}, Class: ${device.deviceClass}, Subclass: ${device.deviceSubclass}")
            
            // Store the device and update display immediately
            currentDevice = device
            updateDeviceInfo(device)
            
            // Check if this is our supported device
            if (!isOurDevice(device)) {
                Log.w(TAG, "Device is not compatible with this app")
                showError("This device is not compatible with the thermal camera app.") {
                    finish()
                }
                return
            }
            
            // Check if we have a configuration for this device
            val deviceConfig = getDeviceConfig(device)
            if (deviceConfig == null) {
                Log.w(TAG, "No configuration found for device PID: 0x${device.productId.toString(16)}")
                showError("Camera model not recognized. Please add device configuration in Main Activity.") {
                    // Go back to main activity to add device config
                    finish()
                }
                return
            }
            
            Log.i(TAG, "Device configuration found: ${deviceConfig.width}x${deviceConfig.height} @ ${deviceConfig.fps}fps")
            
            // Check if we already have permission
            if (usbManager.hasPermission(device)) {
                Log.i(TAG, "Device permission already granted, opening camera")
                openUsbCamera(device)
            } else {
                Log.i(TAG, "Device permission not granted, requesting permission")
                // Request USB permission
                requestUsbPermission(device)
            }
        } else {
            Log.w(TAG, "No USB device found in intent, trying to find device manually")
            // Try to find the device manually as a fallback
            findAndConnectCamera()
        }
    }

    override fun onResume() {
        super.onResume()
        // Update device info if we have a current device
        currentDevice?.let { device ->
            Log.i(TAG, "onResume: Updating device info for current device: ${device.deviceName}")
            updateDeviceInfo(device)
        } ?: run {
            Log.w(TAG, "onResume: No current device available")
        }
    }

    private fun openUsbCamera(device: UsbDevice) {
        val deviceConfig = DeviceConfigs.configs[device.productId]
        if (deviceConfig == null) {
            Log.e(TAG, "No configuration found for device PID: 0x${device.productId.toString(16)}")
            showError("Unsupported camera model. Please add device configuration.") {
                finish()
            }
            return
        }

        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager

        // This method should only be called when permission is already granted
        if (!usbManager.hasPermission(device)) {
            Log.e(TAG, "openUsbCamera called without USB permission")
            showError("USB permission required. Please grant permission and try again.") {
                requestUsbPermission(device)
            }
            return
        }

        val connection = usbManager.openDevice(device)
        if (connection == null) {
            Log.e(TAG, "Could not open USB device connection")
            showError("Failed to connect to camera. Please check USB connection and try again.") {
                // Retry opening the camera
                openUsbCamera(device)
            }
            return
        }

        try {
            val fd = connection.fileDescriptor
            if (fd == -1) {
                Log.e(TAG, "Invalid file descriptor from USB connection")
                showError("Camera connection error. Please disconnect and reconnect the USB device.") {
                    finish()
                }
                return
            }

            // Pass the device configuration to native code
            if (!nativeOpenUvcCamera(fd, deviceConfig.width, deviceConfig.height, deviceConfig.fps)) {
                Log.e(TAG, "Failed to initialize UVC camera with native library")
                showError("Camera initialization failed. Please check device compatibility.") {
                    finish()
                }
                return
            }

            // Set up the video surface
            setupVideoSurface()

            // Initialize IrcmdManager with the device configuration
            val ircmdManager = IrcmdManager.getInstance()
            if (!ircmdManager.init(fd, deviceConfig.deviceType)) {
                Log.e(TAG, "Failed to initialize thermal camera control interface")
                showError("Thermal camera controls failed to initialize. Basic video streaming may still work.") {
                    // Allow continuing with just video streaming
                }
                return
            }
            Log.i(TAG, "Camera initialized successfully with device type: ${deviceConfig.deviceType}")
            
            // Test registry functionality
            logRegistryStatus()
            
            // Enumerate all supported frame rates
            nativeEnumerateAllFrameRates()
            
            // Get supported frame rates for current resolution
            val supportedFps = nativeGetSupportedFrameRates(deviceConfig.width, deviceConfig.height)
            if (supportedFps != null && supportedFps.isNotEmpty()) {
                Log.i(TAG, "📊 Supported frame rates for ${deviceConfig.width}x${deviceConfig.height}: ${supportedFps.joinToString(", ")}")
                
                // Check if current fps is actually supported
                if (!supportedFps.contains(deviceConfig.fps)) {
                    Log.w(TAG, "⚠️ Configured FPS ${deviceConfig.fps} not in supported list. Using first supported: ${supportedFps[0]}")
                    // Update config to use first supported framerate
                    val updatedConfig = deviceConfig.copy(fps = supportedFps[0])
                    DeviceConfigs.configs = DeviceConfigs.configs + (device.productId to updatedConfig)
                    updateFrameRateButtonColors(supportedFps[0])
                } else {
                    updateFrameRateButtonColors(deviceConfig.fps)
                }
            } else {
                Log.w(TAG, "⚠️ Could not query supported frame rates")
                updateFrameRateButtonColors(deviceConfig.fps)
            }
            
            showSuccess("Camera connected successfully!")

        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera", e)
            showError("Camera initialization error: ${e.message}") {
                finish()
            }
        }
    }

    private fun setupVideoSurface() {
        // Force software rendering to avoid Vulkan issues
        binding.cameraView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
        Log.i(TAG, "Set TextureView to software rendering to avoid Vulkan issues")
        
        // Configure TextureView for thermal camera format
        binding.cameraView.isOpaque = false
        
        // Try to set a compatible surface format
        try {
            // Get current surface texture and configure it
            val surfaceTexture = binding.cameraView.surfaceTexture
            surfaceTexture?.setDefaultBufferSize(384, 288) // Default thermal camera size
            Log.i(TAG, "Configured surface texture with default buffer size 384x288")
        } catch (e: Exception) {
            Log.w(TAG, "Could not configure surface texture: ${e.message}")
        }
        
        // Set up a surface texture listener
        binding.cameraView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                Log.i(TAG, "Surface texture available: ${width}x${height}")
                
                // Start streaming when surface is available
                val surface = Surface(texture)
                Log.i(TAG, "Created surface from texture, starting native streaming...")
                
                if (nativeStartStreaming(surface)) {
                    Log.i(TAG, "✅ UVC streaming started successfully")
                    
                    // Get the camera dimensions from native code
                    val dimensions = nativeGetCameraDimensions()
                    if (dimensions != null) {
                        Log.i(TAG, "✅ Camera dimensions: ${dimensions.first}x${dimensions.second}")
                        
                        // Update the container's aspect ratio to match camera
                        val params = binding.cameraContainer.layoutParams as ConstraintLayout.LayoutParams
                        if (!isFullscreen) {
                            // Only set aspect ratio in portrait mode
                            params.dimensionRatio = "${dimensions.first}:${dimensions.second}"
                            binding.cameraContainer.layoutParams = params
                            Log.i(TAG, "Updated container aspect ratio to ${dimensions.first}:${dimensions.second}")
                        }
                    } else {
                        Log.w(TAG, "Could not get camera dimensions from native code")
                    }
                } else {
                    Log.e(TAG, "❌ Failed to start UVC streaming - check USB connection and permissions")
                    showError("Failed to start camera streaming") {
                        finish()
                    }
                }
            }
            
            override fun onSurfaceTextureSizeChanged(texture: SurfaceTexture, width: Int, height: Int) {
                // Handle surface size changes if needed
                Log.i(TAG, "Surface size changed: width=$width, height=$height")
            }
            
            override fun onSurfaceTextureDestroyed(texture: SurfaceTexture): Boolean {
                // Stop streaming when surface is destroyed
                nativeStopStreaming()
                return true
            }
            
            override fun onSurfaceTextureUpdated(texture: SurfaceTexture) {
                // Handle surface updates if needed
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Cancel permission check job to prevent memory leaks
        permissionCheckJob?.cancel()
        
        // Stop streaming if it's active
        nativeStopStreaming()
        
        // Close the UVC camera
        nativeCloseUvcCamera()
        
        // Clean up IrcmdManager
        ircmdManager.cleanup()
        
        // Close the USB connection
        deviceConnection?.close()
        deviceConnection = null
        
        // Unregister receiver
        try {
            unregisterReceiver(usbPermissionReceiver)
        } catch (ignored: IllegalArgumentException) {
            // Receiver was already unregistered
        }
        
        // Clean up TensorFlow Lite interpreter
        fsrcnnInterpreter?.close()
        fsrcnnInterpreter = null
        
        // Stop any ongoing video recording
        if (::videoRecorder.isInitialized && videoRecorder.isRecording()) {
            videoRecorder.stopRecording()
        }
        
        // Cancel recording duration updates
        recordingDurationUpdateJob?.cancel()
    }

    /**
     * Your native bridge (JNI) that wraps the file descriptor in libuvc calls.
     * Implement this in C/C++ in your NDK code.
     */
    private external fun nativeOpenUvcCamera(fd: Int, width: Int, height: Int, fps: Int): Boolean
    private external fun nativeStartStreaming(surface: Surface): Boolean
    private external fun nativeStopStreaming()
    private external fun nativeCloseUvcCamera()
    private external fun nativeGetCameraDimensions(): Pair<Int, Int>?

    /**
     * Find a compatible camera device that's already connected and attempt to connect to it.
     * This can be used when launching the activity from a button rather than a USB intent.
     */
    private fun findAndConnectCamera() {
        val deviceList = usbManager.deviceList
        
        if (deviceList.isEmpty()) {
            showError("No USB devices found") {
                finish()
            }
            return
        }
        
        // Find our camera in the device list
        for ((_, device) in deviceList) {
            if (isOurDevice(device)) {
                // Store the device and update display immediately
                currentDevice = device
                updateDeviceInfo(device)
                
                if (usbManager.hasPermission(device)) {
                    openUsbCamera(device)
                } else {
                    requestUsbPermission(device)
                }
                return
            }
        }
        
        showError("No compatible camera found") {
            finish()
        }
    }

    private fun updateLastCommand(command: String, value: Int, success: Boolean = true, errorMessage: String? = null, errorCode: Int? = null) {
        runOnUiThread {
            val status = if (success) "Success" else "Failed"
            val errorText = if (!success && errorMessage != null) {
                val codeText = if (errorCode != null) " (code: $errorCode)" else ""
                " - $errorMessage$codeText"
            } else ""
            binding.lastCommandText.text = "Last Command: $command = $value ($status$errorText)"
        }
    }

    private fun performFFC() {
        if (!ircmdManager.isInitialized()) {
            Log.e(TAG, "FFC failed: Camera not initialized (code: ${IrcmdManager.ERROR_NOT_INITIALIZED})")
            updateLastCommand("FFC", 0, false, "Camera not initialized", IrcmdManager.ERROR_NOT_INITIALIZED)
            return
        }

        // Disable button during FFC
        binding.ffcButton.isEnabled = false
        binding.ffcButton.text = "FFC in progress..."

        // Use lifecycleScope to run FFC operation
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ircmdManager.performFFC()
                }
                
                // UI updates automatically happen on main thread
                binding.ffcButton.isEnabled = true
                binding.ffcButton.text = "FFC"
                
                when (result) {
                    IrcmdManager.ERROR_SUCCESS -> {
                        updateLastCommand("FFC", 0, true)
                        showSuccess("FFC completed successfully")
                    }
                    IrcmdManager.ERROR_NOT_INITIALIZED -> {
                        Log.e(TAG, "FFC failed: Camera not initialized (code: $result)")
                        updateLastCommand("FFC", 0, false, "Camera not initialized", result)
                        showError("FFC failed: Camera not initialized")
                    }
                    IrcmdManager.ERROR_USB_WRITE -> {
                        Log.e(TAG, "FFC failed: USB write error (code: $result)")
                        updateLastCommand("FFC", 0, false, "Failed to send FFC command", result)
                        showError("FFC failed: USB communication error") {
                            performFFC()
                        }
                    }
                    else -> {
                        Log.e(TAG, "FFC failed: ${ircmdManager.getLastErrorMessage()} (code: $result)")
                        updateLastCommand("FFC", 0, false, ircmdManager.getLastErrorMessage(), result)
                        showError("FFC failed: ${ircmdManager.getLastErrorMessage()}") {
                            performFFC()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing FFC", e)
                binding.ffcButton.isEnabled = true
                binding.ffcButton.text = "FFC"
                updateLastCommand("FFC", 0, false, e.message ?: "Unknown error", -99)
                showError("FFC failed: ${e.message}") {
                    performFFC()
                }
            }
        }
    }
    
    private fun setBrightness(level: Int) {
        // Test both legacy and registry approaches
        Log.i(TAG, "Testing both legacy and registry brightness setting")
        
        // Legacy approach
        executeCameraCommand("Brightness (Legacy)", level) {
            ircmdManager.setBrightness(level)
        }
        
        // NEW: Registry approach test
        lifecycleScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    ircmdManager.setRegistryBrightness(level)
                }
                
                when (result) {
                    IrcmdManager.ERROR_SUCCESS -> {
                        Log.i(TAG, "Registry brightness set to $level successfully")
                        showSuccess("Registry brightness set to $level")
                    }
                    IrcmdManager.ERROR_NOT_INITIALIZED -> {
                        Log.e(TAG, "Registry brightness failed: Camera not initialized")
                        showError("Registry brightness failed: Camera not initialized")
                    }
                    else -> {
                        Log.e(TAG, "Registry brightness failed with error code: $result")
                        showError("Registry brightness failed: Error $result")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Exception in registry brightness", e)
                showError("Registry brightness failed: ${e.message}")
            }
        }
    }
    
    private fun setContrast(level: Int) {
        executeCameraCommand("Contrast", level) {
            ircmdManager.setContrast(level)
        }
    }
    
    /**
     * DISABLED: This method causes a SIGSEGV crash in the SDK code
     * 
     * TODO: Fix this method by:
     * 1. Investigating proper SDK initialization for read operations
     * 2. Checking if there's a specific sequence of calls needed
     * 3. Contacting the SDK vendor for proper usage guidelines
     * 4. Potentially implementing a workaround using device state tracking
     */
    private fun getBrightness() {
        // Original implementation - kept for reference
        /*
        if (!ircmdManager.isInitialized()) {
            Toast.makeText(this, "Camera not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        // Run in background thread
        Thread {
            try {
                val (result, level) = ircmdManager.getCurrentBrightness()
                
                // Update UI on main thread
                runOnUiThread {
                    if (result == IrcmdManager.ERROR_SUCCESS) {
                        Log.i(TAG, "Current brightness level: $level")
                        brightnessSlider.progress = level
                        Toast.makeText(this, "Current brightness: $level", Toast.LENGTH_SHORT).show()
                    } else {
                        // Use the last set value instead
                        val lastValue = ircmdManager.getLastSetBrightness()
                        Log.w(TAG, "Using last set brightness value: $lastValue")
                        brightnessSlider.progress = lastValue
                        Toast.makeText(this, "Using last set brightness: $lastValue", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error getting brightness", e)
                runOnThread {
                    Toast.makeText(this, "Error getting brightness: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
        */
    }

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        isHandlingOrientationChange = true
        
        if (isFullscreen) {
            // Enter fullscreen
            // Force landscape orientation without recreating activity
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            
            // Hide system bars using the official approach
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            
            // Ensure action bar is hidden
            supportActionBar?.hide()
            
            // Hide controls
            binding.controlsContainer.visibility = View.GONE
            
            // Update layout for landscape with 4:3 aspect ratio
            updateLayoutForOrientation(Configuration.ORIENTATION_LANDSCAPE)
            
        } else {
            // Exit fullscreen
            // Force portrait orientation without recreating activity
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            
            // Show system bars using the official approach
            windowInsetsController.show(WindowInsetsCompat.Type.systemBars())
            
            // Show action bar
            supportActionBar?.show()
            
            // Show controls
            binding.controlsContainer.visibility = View.VISIBLE
            
            // Update layout for portrait
            updateLayoutForOrientation(Configuration.ORIENTATION_PORTRAIT)
        }
        
        // Reset orientation change flag after a short delay
        Handler(Looper.getMainLooper()).postDelayed({
            isHandlingOrientationChange = false
        }, 500)
    }

    override fun onBackPressed() {
        if (isFullscreen) {
            toggleFullscreen()
        } else {
            super.onBackPressed()
        }
    }

    private fun setPalette(newIndex: Int) {
        // Handle wrapping around
        currentPaletteIndex = when {
            newIndex > MAX_PALETTE_INDEX -> MIN_PALETTE_INDEX
            newIndex < MIN_PALETTE_INDEX -> MAX_PALETTE_INDEX
            else -> newIndex
        }

        executeCameraCommand("Palette", currentPaletteIndex) {
            ircmdManager.setPalette(currentPaletteIndex)
        }
    }

    private fun setSceneMode(newIndex: Int) {
        // Handle wrapping around
        currentSceneModeIndex = when {
            newIndex > MAX_SCENE_MODE_INDEX -> MIN_SCENE_MODE_INDEX
            newIndex < MIN_SCENE_MODE_INDEX -> MAX_SCENE_MODE_INDEX
            else -> newIndex
        }

        executeCameraCommand("Scene Mode", currentSceneModeIndex) {
            ircmdManager.setSceneMode(currentSceneModeIndex)
        }
    }

    private fun updateDeviceInfo(device: UsbDevice? = currentDevice) {
        // This function is now a no-op since we removed the device info views
        Log.i(TAG, "updateDeviceInfo called with device: ${device?.deviceName ?: "null"}")
    }

    private fun showManageDevicesDialog() {
        // This function is now a no-op since we removed the device management from camera activity
        Log.i(TAG, "Device management is not available in camera activity")
    }

    private fun showAddDeviceDialog() {
        // This function is now a no-op since we removed the device management from camera activity
        Log.i(TAG, "Device management is not available in camera activity")
    }

    private fun checkAndRequestCameraPermission(onGranted: () -> Unit) {
        when {
            // Permission is already granted
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                Log.i(TAG, "Camera permission already granted")
                onGranted()
            }
            // Should show explanation
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.CAMERA) -> {
                Log.i(TAG, "Should show camera permission rationale")
                AlertDialog.Builder(this)
                    .setTitle("Camera Permission Required")
                    .setMessage("This app needs camera permission to access the thermal camera. Please grant camera permission to continue.")
                    .setPositiveButton("Grant Permission") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.CAMERA),
                            CAMERA_PERMISSION_REQUEST_CODE
                        )
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                        showError("Camera permission is required to use the thermal camera") {
                            finish()
                        }
                    }
                    .setCancelable(false)
                    .show()
            }
            // Request permission
            else -> {
                Log.i(TAG, "Requesting camera permission")
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    CAMERA_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Camera permission granted")
                    // Permission granted, proceed with USB permission request if we have a device
                    currentDevice?.let { device ->
                        if (!usbManager.hasPermission(device)) {
                            requestUsbPermission(device)
                        } else if (isOurDevice(device) && !isHandlingUsbAttachment) {
                            // Only open camera if we're not handling a USB attachment
                            openUsbCamera(device)
                        }
                    }
                } else {
                    Log.w(TAG, "Camera permission denied")
                    showError("Camera permission is required to use the thermal camera") {
                        finish()
                    }
                }
            }
            STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Storage permission granted")
                    showSuccess("Storage permission granted! You can now save enhanced images.")
                } else {
                    Log.w(TAG, "Storage permission denied")
                    showError("Storage permission denied. Cannot save images to gallery.")
                }
            }
            AUDIO_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.i(TAG, "Audio permission granted")
                    showSuccess("Audio permission granted! Video recording will include audio.")
                } else {
                    Log.w(TAG, "Audio permission denied")
                    showError("Audio permission denied. Video recording will be silent.")
                }
            }
        }
    }

    private fun requestUsbPermission(device: UsbDevice) {
        permissionIntent = PendingIntent.getBroadcast(
            this,
            0,
            Intent(ACTION_USB_PERMISSION).apply {
                putExtra(UsbManager.EXTRA_DEVICE, device)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        usbManager.requestPermission(device, permissionIntent)
        permissionRequestTime = System.currentTimeMillis()
        
        // Start permission check with Coroutines instead of Handler
        permissionCheckJob = lifecycleScope.launch {
            while (isActive) {
                delay(1000)
                val currentTime = System.currentTimeMillis()
                
                if (currentTime - permissionRequestTime > PERMISSION_REQUEST_TIMEOUT) {
                    Log.w(TAG, "Permission request timed out after ${PERMISSION_REQUEST_TIMEOUT}ms")
                    showError("USB permission request timed out. Please try connecting the camera again.") {
                        currentDevice?.let { device ->
                            requestUsbPermission(device)
                        } ?: finish()
                    }
                    return@launch
                }

                // Check if we got permission while waiting
                currentDevice?.let { permissionDevice ->
                    if (usbManager.hasPermission(permissionDevice)) {
                        Log.i(TAG, "Permission granted while waiting")
                        openUsbCamera(permissionDevice)
                        return@launch
                    }
                }
            }
        }
    }

    private fun setNoiseReduction(level: Int) {
        executeCameraCommand("Noise Reduction", level) {
            ircmdManager.setNoiseReduction(level)
        }
    }

    private fun setTimeNoiseReduction(level: Int) {
        executeCameraCommand("Time Noise Reduction", level) {
            ircmdManager.setTimeNoiseReduction(level)
        }
    }

    private fun setSpaceNoiseReduction(level: Int) {
        executeCameraCommand("Space Noise Reduction", level) {
            ircmdManager.setSpaceNoiseReduction(level)
        }
    }

    private fun setDetailEnhancement(level: Int) {
        executeCameraCommand("Detail Enhancement", level) {
            ircmdManager.setDetailEnhancement(level)
        }
    }

    private fun setGlobalContrast(level: Int) {
        executeCameraCommand("Global Contrast", level) {
            ircmdManager.setGlobalContrast(level)
        }
    }
    
    private fun setFrameRate(fps: Int) {
        currentDevice?.let { device ->
            val deviceConfig = DeviceConfigs.configs[device.productId]
            if (deviceConfig == null) {
                Log.e(TAG, "No device configuration found")
                showError("No device configuration found")
                return
            }
            
            lifecycleScope.launch {
                try {
                    // Update UI to show processing
                    updateLastCommand("Frame Rate", fps, true, "Changing to ${fps}fps via UVC...", 0)
                    binding.frameRate25Button.isEnabled = false
                    binding.frameRate50Button.isEnabled = false
                    
                    // Use UVC-native framerate control
                    val success = withContext(Dispatchers.IO) {
                        nativeSetFrameRate(deviceConfig.width, deviceConfig.height, fps)
                    }
                    
                    if (success) {
                        // Verify the actual framerate
                        val actualFps = withContext(Dispatchers.IO) {
                            nativeGetCurrentFrameRate()
                        }
                        
                        // Update device config with actual framerate
                        val updatedConfig = deviceConfig.copy(fps = actualFps)
                        DeviceConfigs.configs = DeviceConfigs.configs + (device.productId to updatedConfig)
                        
                        Log.i(TAG, "✅ UVC frame rate changed: requested ${fps}fps, actual ${actualFps}fps")
                        updateLastCommand("Frame Rate", actualFps, true)
                        showSuccess("Frame rate changed to ${actualFps}fps via UVC")
                        
                        // Update button colors to show current selection
                        updateFrameRateButtonColors(actualFps)
                        
                    } else {
                        Log.e(TAG, "❌ UVC framerate change failed")
                        updateLastCommand("Frame Rate", fps, false, "UVC control failed", -1)
                        showError("UVC framerate change failed - camera may not support ${fps}fps")
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error changing frame rate via UVC", e)
                    updateLastCommand("Frame Rate", fps, false, e.message ?: "Unknown error", -99)
                    showError("UVC framerate change failed: ${e.message}")
                } finally {
                    // Re-enable buttons
                    binding.frameRate25Button.isEnabled = true
                    binding.frameRate50Button.isEnabled = true
                }
            }
        } ?: run {
            Log.e(TAG, "No current device for framerate change")
            showError("No device connected")
        }
    }
    
    private fun restartCameraWithNewFrameRate(device: UsbDevice, deviceConfig: DeviceConfig) {
        try {
            Log.i(TAG, "🔄 Restarting camera with new framerate: ${deviceConfig.fps}fps")
            
            // Stop current streaming
            nativeStopStreaming()
            
            // Close current UVC camera
            nativeCloseUvcCamera()
            
            // Short delay to ensure clean shutdown
            Thread.sleep(200)
            
            // Re-open camera with new framerate parameters
            val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
            val connection = usbManager.openDevice(device)
            
            if (connection != null) {
                val fd = connection.fileDescriptor
                
                // Re-initialize with new framerate
                if (nativeOpenUvcCamera(fd, deviceConfig.width, deviceConfig.height, deviceConfig.fps)) {
                    // Re-setup video surface
                    setupVideoSurface()
                    Log.i(TAG, "✅ Camera restarted successfully with ${deviceConfig.fps}fps")
                } else {
                    Log.e(TAG, "❌ Failed to re-initialize camera with new framerate")
                    showError("Failed to restart camera with new framerate")
                }
            } else {
                Log.e(TAG, "❌ Failed to re-open USB device connection")
                showError("Failed to re-open camera connection")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error restarting camera", e)
            showError("Error restarting camera: ${e.message}")
        }
    }
    
    private fun updateFrameRateButtonColors(currentFps: Int) {
        runOnUiThread {
            // Reset both buttons to default color
            binding.frameRate25Button.backgroundTintList = getColorStateList(android.R.color.holo_blue_bright)
            binding.frameRate50Button.backgroundTintList = getColorStateList(android.R.color.holo_blue_bright)
            
            // Highlight the current framerate button
            when (currentFps) {
                25 -> binding.frameRate25Button.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
                50 -> binding.frameRate50Button.backgroundTintList = getColorStateList(android.R.color.holo_green_dark)
            }
        }
    }

    private fun setupExpandableGroup(header: TextView, content: LinearLayout) {
        // Initially show content
        content.visibility = View.VISIBLE
        
        header.setOnClickListener {
            // Toggle content visibility
            content.visibility = if (content.visibility == View.VISIBLE) View.GONE else View.VISIBLE
            
            // Update the expand/collapse icon
            val icon = if (content.visibility == View.VISIBLE) {
                R.drawable.ic_expand_less
            } else {
                R.drawable.ic_expand_more
            }
            header.setCompoundDrawablesWithIntrinsicBounds(0, 0, icon, 0)
        }
    }
    
    private fun logRegistryStatus() {
        try {
            val functionCount = ircmdManager.getRegisteredFunctionCount()
            Log.i(TAG, "=== Camera Function Registry Status ===")
            Log.i(TAG, "Total registered functions: $functionCount")
            
            // Test some function support checks
            val brightnessSetSupported = ircmdManager.isFunctionSupported(
                IrcmdManager.FUNCTION_TYPE_SET, 
                IrcmdManager.Companion.CameraFunctionId.BRIGHTNESS
            )
            val brightnessGetSupported = ircmdManager.isFunctionSupported(
                IrcmdManager.FUNCTION_TYPE_GET, 
                IrcmdManager.Companion.CameraFunctionId.BRIGHTNESS
            )
            val ffcSupported = ircmdManager.isFunctionSupported(
                IrcmdManager.FUNCTION_TYPE_ACTION, 
                IrcmdManager.Companion.CameraFunctionId.FFC_UPDATE
            )
            
            Log.i(TAG, "Brightness SET supported: $brightnessSetSupported")
            Log.i(TAG, "Brightness GET supported: $brightnessGetSupported")
            Log.i(TAG, "FFC ACTION supported: $ffcSupported")
            Log.i(TAG, "===================================")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error checking registry status", e)
        }
    }
    
    // ===== TENSORFLOW LITE SUPER RESOLUTION =====
    
    private fun initializeSuperResolutionModel() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.i(TAG, "🔄 Loading FSRCNN model...")
                val assetFileDescriptor = assets.openFd("models/fsrcnn_x2.tflite")
                val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
                val fileChannel = fileInputStream.channel
                val startOffset = assetFileDescriptor.startOffset
                val declaredLength = assetFileDescriptor.declaredLength
                val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
                
                Log.i(TAG, "📊 Model file size: ${declaredLength} bytes")
                
                val options = Interpreter.Options()
                options.setNumThreads(4) // Optimize for Pixel 7
                
                fsrcnnInterpreter = Interpreter(modelBuffer, options)
                
                // Get model input/output details
                val inputShape = fsrcnnInterpreter!!.getInputTensor(0).shape()
                val outputShape = fsrcnnInterpreter!!.getOutputTensor(0).shape()
                Log.i(TAG, "📊 Model input shape: [${inputShape.joinToString(",")}]")
                Log.i(TAG, "📊 Model output shape: [${outputShape.joinToString(",")}]")
                
                withContext(Dispatchers.Main) {
                    Log.i(TAG, "✅ FSRCNN model loaded successfully!")
                    binding.lastCommandText.text = "Last Command: FSRCNN model loaded for super resolution"
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to load FSRCNN model: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    showError("Failed to load super resolution model: ${e.message}")
                }
            }
        }
    }
    
    private fun captureAndEnhanceFrame() {
        lifecycleScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                
                // Update UI to show processing
                binding.lastCommandText.text = "Last Command: 🎯 Capturing raw thermal frame..."
                binding.captureEnhanceButton.text = "🔄 Processing..."
                binding.captureEnhanceButton.isEnabled = false
                
                // 🎯 CAPTURE RAW THERMAL FRAME (256x192 YUYV)
                val rawFrameData = captureRawThermalFrame()
                if (rawFrameData == null) {
                    showError("Failed to capture raw thermal frame")
                    return@launch
                }
                
                Log.i(TAG, "🎯 Captured raw thermal frame: ${rawFrameData.width}x${rawFrameData.height}, ${rawFrameData.data.size} bytes")
                
                // Convert raw YUYV to display bitmap for comparison
                val originalBitmap = convertYUYVToBitmap(rawFrameData)
                
                // Apply super resolution to raw data
                val enhancedBitmap = withContext(Dispatchers.IO) {
                    enhanceWithFSRCNNFromRaw(rawFrameData)
                }
                
                val processingTime = System.currentTimeMillis() - startTime
                
                if (enhancedBitmap != null && originalBitmap != null) {
                    Log.i(TAG, "🔥 Enhanced frame: ${enhancedBitmap.width}x${enhancedBitmap.height} (${processingTime}ms)")
                    
                    // Show results with raw vs enhanced comparison
                    showEnhancementResults(originalBitmap, enhancedBitmap, processingTime)
                    
                } else {
                    showError("Super resolution processing failed")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error in capture and enhance", e)
                showError("Capture and enhance failed: ${e.message}")
            } finally {
                // Reset button
                binding.captureEnhanceButton.text = "📸 Capture & Enhance"
                binding.captureEnhanceButton.isEnabled = true
            }
        }
    }
    
    // Data class for raw frame
    data class RawFrameData(
        val width: Int,
        val height: Int,
        val data: ByteArray
    )
    
    private suspend fun captureRawThermalFrame(): RawFrameData? = withContext(Dispatchers.IO) {
        try {
            Log.i(TAG, "🎯 Setting capture flag for next frame...")
            nativeSetCaptureFlag(true)
            
            // Wait for frame to be captured (max 200ms)
            var attempts = 0
            while (attempts < 10 && !nativeHasCapturedFrame()) {
                delay(20) // Wait 20ms between checks (50fps = 20ms per frame)
                attempts++
            }
            
            if (!nativeHasCapturedFrame()) {
                Log.w(TAG, "Timeout waiting for frame capture")
                return@withContext null
            }
            
            val frameData = nativeGetCapturedFrame()
            if (frameData == null || frameData.size < 8) {
                Log.e(TAG, "Invalid frame data received")
                return@withContext null
            }
            
            // Extract width and height from first 8 bytes
            val width = ByteBuffer.wrap(frameData, 0, 4).order(ByteOrder.LITTLE_ENDIAN).int
            val height = ByteBuffer.wrap(frameData, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
            
            // Extract YUYV data (skip first 8 bytes)
            val yuvData = frameData.copyOfRange(8, frameData.size)
            
            Log.i(TAG, "📸 Raw frame captured: ${width}x${height}, ${yuvData.size} bytes YUYV")
            
            RawFrameData(width, height, yuvData)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error capturing raw frame: ${e.message}", e)
            null
        }
    }
    
    private fun convertYUYVToBitmap(rawFrame: RawFrameData): Bitmap? {
        try {
            val bitmap = Bitmap.createBitmap(rawFrame.width, rawFrame.height, Bitmap.Config.ARGB_8888)
            val yuvData = rawFrame.data
            
            // For thermal cameras, the Y (luminance) channel contains the thermal data
            // Extract Y values and apply proper thermal visualization
            val yValues = IntArray(rawFrame.width * rawFrame.height)
            var minY = 255
            var maxY = 0
            
            // Extract Y values and find range
            for (y in 0 until rawFrame.height) {
                for (x in 0 until rawFrame.width) {
                    val yIndex = if (x % 2 == 0) {
                        (y * rawFrame.width + x) * 2
                    } else {
                        (y * rawFrame.width + (x - 1)) * 2 + 2
                    }
                    
                    if (yIndex < yuvData.size) {
                        val yValue = yuvData[yIndex].toInt() and 0xFF
                        yValues[y * rawFrame.width + x] = yValue
                        minY = minOf(minY, yValue)
                        maxY = maxOf(maxY, yValue)
                    }
                }
            }
            
            Log.i(TAG, "🌡️ Thermal Y range: [$minY, $maxY]")
            
            // Apply thermal colormap (simple grayscale with contrast enhancement)
            val range = maxY - minY
            for (y in 0 until rawFrame.height) {
                for (x in 0 until rawFrame.width) {
                    val yValue = yValues[y * rawFrame.width + x]
                    
                    // Contrast-enhanced grayscale
                    val normalized = if (range > 0) {
                        ((yValue - minY).toFloat() / range).coerceIn(0f, 1f)
                    } else {
                        yValue / 255f
                    }
                    
                    val gray = (normalized * 255).toInt()
                    val color = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                    bitmap.setPixel(x, y, color)
                }
            }
            
            Log.i(TAG, "✅ Converted thermal YUYV to enhanced bitmap: ${bitmap.width}x${bitmap.height}")
            return bitmap
            
        } catch (e: Exception) {
            Log.e(TAG, "Error converting YUYV to bitmap: ${e.message}", e)
            return null
        }
    }
    
    private fun yuvToRgb(y: Int, u: Int, v: Int): Int {
        // YUV to RGB conversion
        val c = y - 16
        val d = u - 128
        val e = v - 128
        
        val r = ((298 * c + 409 * e + 128) shr 8).coerceIn(0, 255)
        val g = ((298 * c - 100 * d - 208 * e + 128) shr 8).coerceIn(0, 255)
        val b = ((298 * c + 516 * d + 128) shr 8).coerceIn(0, 255)
        
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
    
    private fun enhanceWithFSRCNNFromRaw(rawFrame: RawFrameData): Bitmap? {
        val interpreter = fsrcnnInterpreter
        if (interpreter == null) {
            Log.e(TAG, "FSRCNN interpreter not initialized")
            return null
        }
        
        return try {
            Log.i(TAG, "🤖 Starting FSRCNN enhancement: ${rawFrame.width}x${rawFrame.height} → ${rawFrame.width * 2}x${rawFrame.height * 2}")
            
            // Convert YUYV to grayscale array directly (more efficient)
            val inputArray = preprocessYUYVToArray(rawFrame)
            
            // Prepare output array [1, 384, 512, 1] for 2x scaling
            val outputHeight = rawFrame.height * 2
            val outputWidth = rawFrame.width * 2
            val outputArray = Array(1) { Array(outputHeight) { Array(outputWidth) { FloatArray(1) } } }
            
            Log.i(TAG, "📊 Model input: [1, ${rawFrame.height}, ${rawFrame.width}, 1]")
            Log.i(TAG, "📊 Model output: [1, $outputHeight, $outputWidth, 1]")
            
            // Sample input to verify preprocessing
            Log.i(TAG, "🔍 Input sample values: [0,0]=${inputArray[0][0][0][0]}, [10,10]=${inputArray[0][10][10][0]}, [50,50]=${inputArray[0][50][50][0]}")
            
            // Run inference
            val inferenceStart = System.currentTimeMillis()
            try {
                interpreter.run(inputArray, outputArray)
                val inferenceTime = System.currentTimeMillis() - inferenceStart
                Log.i(TAG, "🔥 FSRCNN inference completed successfully in ${inferenceTime}ms")
                
                // Sample output to verify inference worked
                Log.i(TAG, "🔍 Output sample values: [0,0]=${outputArray[0][0][0][0]}, [100,100]=${outputArray[0][100][100][0]}, [200,200]=${outputArray[0][200][200][0]}")
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ FSRCNN inference failed: ${e.message}", e)
                throw e
            }
            
            // Convert output back to bitmap
            val result = postprocessToBitmap(outputArray)
            Log.i(TAG, "✅ FSRCNN enhancement complete: ${result.width}x${result.height}")
            result
            
        } catch (e: Exception) {
            Log.e(TAG, "FSRCNN enhancement from raw failed: ${e.message}", e)
            null
        }
    }
    
    private fun preprocessYUYVToArray(rawFrame: RawFrameData): Array<Array<Array<FloatArray>>> {
        val width = rawFrame.width
        val height = rawFrame.height
        val inputArray = Array(1) { Array(height) { Array(width) { FloatArray(1) } } }
        val yuvData = rawFrame.data
        
        Log.i(TAG, "🔄 Preprocessing YUYV ${width}x${height} with thermal enhancement")
        
        // First pass: Extract Y values and find thermal range
        val yValues = IntArray(width * height)
        var minY = 255
        var maxY = 0
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val yIndex = if (x % 2 == 0) {
                    (y * width + x) * 2
                } else {
                    (y * width + (x - 1)) * 2 + 2
                }
                
                if (yIndex < yuvData.size) {
                    val yValue = yuvData[yIndex].toInt() and 0xFF
                    yValues[y * width + x] = yValue
                    minY = minOf(minY, yValue)
                    maxY = maxOf(maxY, yValue)
                }
            }
        }
        
        Log.i(TAG, "🌡️ Thermal Y range: [$minY, $maxY]")
        
        // Second pass: Apply SAME contrast enhancement as original image
        val range = maxY - minY
        for (y in 0 until height) {
            for (x in 0 until width) {
                val yValue = yValues[y * width + x]
                
                // Apply identical enhancement as original thermal image
                val enhanced = if (range > 0) {
                    ((yValue - minY).toFloat() / range).coerceIn(0f, 1f)
                } else {
                    yValue / 255f
                }
                
                inputArray[0][y][x][0] = enhanced
            }
        }
        
        Log.i(TAG, "✅ Applied thermal contrast enhancement to FSRCNN input")
        return inputArray
    }
    
    private fun enhanceWithFSRCNN(inputBitmap: Bitmap): Bitmap? {
        val interpreter = fsrcnnInterpreter
        if (interpreter == null) {
            Log.e(TAG, "FSRCNN interpreter not initialized")
            return null
        }
        
        return try {
            // Resize input to thermal dimensions if needed
            val thermalBitmap = Bitmap.createScaledBitmap(inputBitmap, 256, 192, true)
            
            // Convert to grayscale and normalize to [0,1]
            val inputArray = preprocessBitmap(thermalBitmap)
            
            // Prepare output array [1, 384, 512, 1]
            val outputArray = Array(1) { Array(384) { Array(512) { FloatArray(1) } } }
            
            // Run inference
            val inferenceStart = System.currentTimeMillis()
            interpreter.run(inputArray, outputArray)
            val inferenceTime = System.currentTimeMillis() - inferenceStart
            
            Log.i(TAG, "FSRCNN inference time: ${inferenceTime}ms")
            
            // Convert output back to bitmap
            postprocessToBitmap(outputArray)
            
        } catch (e: Exception) {
            Log.e(TAG, "FSRCNN enhancement failed: ${e.message}", e)
            null
        }
    }
    
    private fun preprocessBitmap(bitmap: Bitmap): Array<Array<Array<FloatArray>>> {
        val width = bitmap.width
        val height = bitmap.height
        val inputArray = Array(1) { Array(height) { Array(width) { FloatArray(1) } } }
        
        // Convert to grayscale and normalize
        for (y in 0 until height) {
            for (x in 0 until width) {
                val pixel = bitmap.getPixel(x, y)
                val r = (pixel shr 16) and 0xFF
                val g = (pixel shr 8) and 0xFF
                val b = pixel and 0xFF
                
                // Convert to grayscale and normalize to [0,1]
                val gray = (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                inputArray[0][y][x][0] = gray.toFloat()
            }
        }
        
        return inputArray
    }
    
    private fun postprocessToBitmap(outputArray: Array<Array<Array<FloatArray>>>): Bitmap {
        val height = outputArray[0].size
        val width = outputArray[0][0].size
        
        // Log output range for debugging
        var minVal = Float.MAX_VALUE
        var maxVal = Float.MIN_VALUE
        var zeroCount = 0
        var nonZeroCount = 0
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = outputArray[0][y][x][0]
                minVal = minOf(minVal, value)
                maxVal = maxOf(maxVal, value)
                if (value == 0f) zeroCount++ else nonZeroCount++
            }
        }
        
        Log.i(TAG, "🔍 FSRCNN output analysis:")
        Log.i(TAG, "   Range: [$minVal, $maxVal]")
        Log.i(TAG, "   Zero values: $zeroCount, Non-zero: $nonZeroCount")
        
        // Check if output is all zeros or invalid
        if (maxVal == minVal || (zeroCount > (width * height * 0.9))) {
            Log.e(TAG, "❌ FSRCNN output appears invalid - mostly zeros or constant values")
            return createTestPatternBitmap(width, height)
        }
        
        // Use faster pixel array method instead of setPixel()
        val pixels = IntArray(width * height)
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                val value = outputArray[0][y][x][0]
                
                // Apply smart normalization
                val normalizedValue = if (maxVal > minVal) {
                    ((value - minVal) / (maxVal - minVal)).coerceIn(0f, 1f)
                } else {
                    0.5f
                }
                
                val gray = (normalizedValue * 255f).toInt().coerceIn(0, 255)
                val color = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                pixels[y * width + x] = color
            }
        }
        
        // Create bitmap efficiently
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        
        Log.i(TAG, "✅ Postprocessed FSRCNN output to ${width}x${height} bitmap efficiently")
        return bitmap
    }
    
    private fun createTestPatternBitmap(width: Int, height: Int): Bitmap {
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            for (x in 0 until width) {
                val testValue = ((x + y) % 100) / 100f
                val gray = (testValue * 255f).toInt()
                val color = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
                pixels[y * width + x] = color
            }
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        Log.w(TAG, "🔧 Created test pattern bitmap")
        return bitmap
    }
    
    private fun showEnhancementResults(original: Bitmap, enhanced: Bitmap, processingTime: Long) {
        // Create a simple dialog showing the results
        val dialog = AlertDialog.Builder(this)
            .setTitle("🔥 Super Resolution Results")
            .setMessage("Processing Time: ${processingTime}ms\n" +
                       "Original: ${original.width}x${original.height}\n" +
                       "Enhanced: ${enhanced.width}x${enhanced.height}\n" +
                       "Scale: ${enhanced.width / original.width.toFloat()}x")
            .setPositiveButton("Save Both Images") { _, _ ->
                checkStoragePermissionAndSave(original, enhanced, processingTime)
            }
            .setNegativeButton("Close") { _, _ ->
                binding.lastCommandText.text = "Last Command: Super resolution test completed (${processingTime}ms)"
            }
            .create()
        
        dialog.show()
    }
    
    private fun checkStoragePermissionAndSave(original: Bitmap, enhanced: Bitmap, processingTime: Long) {
        // For Android 10+ (API 29+), we use scoped storage and don't need WRITE_EXTERNAL_STORAGE
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            saveImagesToGallery(original, enhanced, processingTime)
            return
        }
        
        // For older versions, check for storage permission
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED -> {
                saveImagesToGallery(original, enhanced, processingTime)
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) -> {
                AlertDialog.Builder(this)
                    .setTitle("Storage Permission Required")
                    .setMessage("This app needs storage permission to save enhanced images to your gallery.")
                    .setPositiveButton("Grant Permission") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                            STORAGE_PERMISSION_REQUEST_CODE
                        )
                    }
                    .setNegativeButton("Cancel") { dialog, _ ->
                        dialog.dismiss()
                        showError("Storage permission is required to save images")
                    }
                    .show()
            }
            else -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    STORAGE_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
    
    private fun saveImagesToGallery(original: Bitmap, enhanced: Bitmap, processingTime: Long) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                
                // Save original thermal image
                val originalUri = saveBitmapToGallery(
                    original, 
                    "ThermalCamera_Original_${timestamp}",
                    "Original 256x192 thermal image captured from camera"
                )
                
                // Save enhanced image
                val enhancedUri = saveBitmapToGallery(
                    enhanced, 
                    "ThermalCamera_Enhanced_${timestamp}",
                    "Enhanced 512x384 thermal image processed with FSRCNN super resolution"
                )
                
                withContext(Dispatchers.Main) {
                    if (originalUri != null && enhancedUri != null) {
                        binding.lastCommandText.text = "Last Command: Images saved to gallery (${processingTime}ms)"
                        showSuccess("✅ Both images saved to gallery successfully!")
                        Log.i(TAG, "📸 Images saved - Original: $originalUri, Enhanced: $enhancedUri")
                    } else {
                        showError("Failed to save one or both images to gallery")
                    }
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "Error saving images to gallery", e)
                withContext(Dispatchers.Main) {
                    showError("Error saving images: ${e.message}")
                }
            }
        }
    }
    
    private suspend fun saveBitmapToGallery(bitmap: Bitmap, filename: String, description: String): Uri? = withContext(Dispatchers.IO) {
        try {
            val resolver = contentResolver
            val imageCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            
            val imageDetails = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "$filename.jpg")
                put(MediaStore.Images.Media.DESCRIPTION, description)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ThermalCamera")
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            
            val imageUri = resolver.insert(imageCollection, imageDetails)
            if (imageUri != null) {
                resolver.openOutputStream(imageUri)?.use { outputStream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)) {
                        throw Exception("Failed to compress bitmap")
                    }
                }
                
                // Mark as not pending (for Android Q+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    imageDetails.clear()
                    imageDetails.put(MediaStore.Images.Media.IS_PENDING, 0)
                    resolver.update(imageUri, imageDetails, null, null)
                }
                
                Log.i(TAG, "✅ Saved image: $filename to $imageUri")
                imageUri
            } else {
                Log.e(TAG, "Failed to create MediaStore entry for $filename")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving bitmap $filename to gallery", e)
            null
        }
    }
    
    // ===== VIDEO RECORDING FUNCTIONALITY =====
    
    private fun initializeVideoRecorder() {
        videoRecorder = VideoRecorder(this)
        Log.i(TAG, "🎥 Video recorder initialized")
    }
    
    private fun startVideoRecording() {
        // TODO: Re-enable audio permission check when implementing microphone input
        // Currently recording video-only to fix muxer start issues
        // if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
        //     requestAudioPermission()
        //     return
        // }
        
        // Get current device configuration
        val deviceConfig = currentDevice?.let { DeviceConfigs.configs[it.productId] }
        if (deviceConfig == null) {
            showError("Cannot start recording: No device configuration found")
            return
        }
        
        // Configure recorder with current camera settings
        val deviceTypeString = when (deviceConfig.deviceType) {
            3 -> "384"
            7 -> "256"
            8 -> "640"
            else -> "256"
        }
        
        // Match camera fps for proper timing synchronization
        val recordingFps = deviceConfig.fps // Use actual camera fps
        
        videoRecorder.configure(
            width = deviceConfig.width,
            height = deviceConfig.height,
            fps = recordingFps,
            deviceType = deviceTypeString,
            bitrateMbps = 15, // TODO: Get from settings
            includeAudio = false // Temporarily disable until microphone input is implemented
        )
        
        // Start recording (surface is null for direct recording mode)
        val recordingSurface = videoRecorder.startRecording(binding.cameraView)
        
        // Check if recording actually started (for both modes)
        if (videoRecorder.isRecording()) {
            // Update UI
            binding.recordButton.text = "⏹️ Stop"
            binding.recordButton.backgroundTintList = getColorStateList(android.R.color.holo_red_dark)
            binding.pauseResumeButton.isEnabled = true
            binding.recordingDurationText.visibility = View.VISIBLE
            
            // Start duration updates
            startRecordingDurationUpdates()
            
            val mode = if (recordingSurface == null) "direct" else "textureview"
            Log.i(TAG, "📹 Video recording started ($mode mode)")
            showSuccess("Recording started!")
        } else {
            showError("Failed to start video recording")
        }
    }
    
    private fun stopVideoRecording() {
        videoRecorder.stopRecording()
        
        // Update UI
        binding.recordButton.text = "🔴 Record"
        binding.recordButton.backgroundTintList = getColorStateList(android.R.color.holo_blue_bright)
        binding.pauseResumeButton.isEnabled = false
        binding.pauseResumeButton.text = "⏸️ Pause"
        binding.recordingDurationText.visibility = View.GONE
        
        // Stop duration updates
        recordingDurationUpdateJob?.cancel()
        
        Log.i(TAG, "🛑 Video recording stopped")
        showSuccess("Recording saved to gallery!")
    }
    
    private fun pauseVideoRecording() {
        videoRecorder.pauseRecording()
        binding.pauseResumeButton.text = "▶️ Resume"
        Log.i(TAG, "⏸️ Video recording paused")
    }
    
    private fun resumeVideoRecording() {
        videoRecorder.resumeRecording()
        binding.pauseResumeButton.text = "⏸️ Pause"
        Log.i(TAG, "▶️ Video recording resumed")
    }
    
    private fun startRecordingDurationUpdates() {
        recordingDurationUpdateJob?.cancel()
        recordingDurationUpdateJob = lifecycleScope.launch {
            while (videoRecorder.isRecording()) {
                val duration = videoRecorder.getRecordingDuration()
                val minutes = (duration / 1000) / 60
                val seconds = (duration / 1000) % 60
                
                withContext(Dispatchers.Main) {
                    binding.recordingDurationText.text = "Duration: %02d:%02d".format(minutes, seconds)
                    
                    // Change color when paused
                    val textColor = if (videoRecorder.isPaused()) {
                        android.graphics.Color.YELLOW
                    } else {
                        android.graphics.Color.RED
                    }
                    binding.recordingDurationText.setTextColor(textColor)
                }
                
                delay(1000) // Update every second
            }
        }
    }
    
    private fun requestAudioPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> {
                startVideoRecording()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.RECORD_AUDIO) -> {
                AlertDialog.Builder(this)
                    .setTitle("Microphone Permission Required")
                    .setMessage("This app needs microphone permission to record audio with videos. You can still record silent videos if you deny this permission.")
                    .setPositiveButton("Grant Permission") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.RECORD_AUDIO),
                            AUDIO_PERMISSION_REQUEST_CODE
                        )
                    }
                    .setNegativeButton("Record Silent Video") { _, _ ->
                        // Start recording without audio
                        startVideoRecording()
                    }
                    .show()
            }
            else -> {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.RECORD_AUDIO),
                    AUDIO_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
}
