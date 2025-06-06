package com.example.MINI2_IR

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

class CameraActivity : AppCompatActivity() {

    init {
        Log.i(TAG, "CameraActivity class being initialized")
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val ACTION_USB_PERMISSION = "android.hardware.usb.action.USB_PERMISSION"
        private const val PERMISSION_REQUEST_TIMEOUT = 5000L // 5 seconds
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        
        // Thermal Camera Co.,Ltd device IDs
        private const val VENDOR_ID = 13428  // 0x3474
        private const val DEVICE_CLASS = 239
        private const val DEVICE_SUBCLASS = 2

        // Intent extra to find device manually
        const val EXTRA_FIND_DEVICE = "com.example.MINI2_IR.FIND_DEVICE"
        
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
            System.loadLibrary("MINI2_IR")
        }
        
        // Add flag to prevent activity recreation
        private var isHandlingOrientationChange = false
        
        // Add flag to track if we're handling a USB device attachment
        private var isHandlingUsbAttachment = false
    }

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
                            Toast.makeText(context, "USB permission denied", Toast.LENGTH_SHORT).show()
                            finish()
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

    private val permissionCheckHandler = Handler(Looper.getMainLooper())
    private val permissionCheckRunnable = object : Runnable {
        override fun run() {
            val currentTime = System.currentTimeMillis()
            if (currentTime - permissionRequestTime > PERMISSION_REQUEST_TIMEOUT) {
                Log.w(TAG, "Permission request timed out after ${PERMISSION_REQUEST_TIMEOUT}ms")
                Toast.makeText(this@CameraActivity, "USB permission request timed out", Toast.LENGTH_SHORT).show()
                finish()
                return
            }

            // Check if we got permission while waiting
            currentDevice?.let { device ->
                if (usbManager.hasPermission(device)) {
                    Log.i(TAG, "Permission granted while waiting")
                    openUsbCamera(device)
                    return
                }
            }

            // Keep checking
            permissionCheckHandler.postDelayed(this, 1000)
        }
    }

    // Add IrcmdManager instance
    private lateinit var ircmdManager: IrcmdManager

    private lateinit var ffcButton: Button
    private lateinit var brightnessSlider: SeekBar
    private lateinit var contrastSlider: SeekBar
    private lateinit var setBrightnessButton: Button
    private lateinit var setContrastButton: Button
    private lateinit var fullscreenButton: ImageButton
    private lateinit var controlsContainer: NestedScrollView
    private lateinit var cameraContainer: FrameLayout
    private lateinit var cameraView: TextureView
    private var isFullscreen = false
    private lateinit var windowInsetsController: WindowInsetsControllerCompat
    private var originalCameraParams: ConstraintLayout.LayoutParams? = null

    // Add new UI element properties
    private lateinit var noiseReductionSlider: SeekBar
    private lateinit var setNoiseReductionButton: Button
    private lateinit var timeNoiseReductionSlider: SeekBar
    private lateinit var setTimeNoiseReductionButton: Button
    private lateinit var spaceNoiseReductionSlider: SeekBar
    private lateinit var setSpaceNoiseReductionButton: Button
    private lateinit var detailEnhancementSlider: SeekBar
    private lateinit var setDetailEnhancementButton: Button
    private lateinit var globalContrastSlider: SeekBar
    private lateinit var setGlobalContrastButton: Button

    private lateinit var nextPaletteButton: Button
    private lateinit var prevPaletteButton: Button
    private var currentPaletteIndex = 0
    
    private lateinit var nextSceneModeButton: Button
    private lateinit var prevSceneModeButton: Button
    private var currentSceneModeIndex = 0
    
    private lateinit var lastCommandText: TextView

    // Add properties for expandable groups
    private lateinit var imageSettingsHeader: TextView
    private lateinit var imageSettingsContent: LinearLayout
    private lateinit var noiseReductionHeader: TextView
    private lateinit var noiseReductionContent: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i(TAG, "onCreate: Starting CameraActivity initialization")
        setContentView(R.layout.activity_camera)
        
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
                    fullscreenButton.setImageResource(R.drawable.ic_fullscreen)
                } else {
                    fullscreenButton.setImageResource(R.drawable.ic_fullscreen_exit)
                }
                ViewCompat.onApplyWindowInsets(view, windowInsets)
            }
            
            // Initialize IrcmdManager
            ircmdManager = IrcmdManager.getInstance()
            
            // Initialize views with explicit type parameters
            try {
                fullscreenButton = findViewById<ImageButton>(R.id.fullscreenButton)
                controlsContainer = findViewById<NestedScrollView>(R.id.controlsContainer)
                cameraContainer = findViewById<FrameLayout>(R.id.cameraContainer)
                cameraView = findViewById<TextureView>(R.id.cameraView)
                lastCommandText = findViewById<TextView>(R.id.lastCommandText)
                
                Log.i(TAG, "onCreate: Found all views - fullscreenButton: ${fullscreenButton != null}, " +
                          "controlsContainer: ${controlsContainer != null}, " +
                          "cameraContainer: ${cameraContainer != null}, " +
                          "cameraView: ${cameraView != null}, " +
                          "lastCommandText: ${lastCommandText != null}")
            } catch (e: Exception) {
                Log.e(TAG, "onCreate: Error finding views", e)
                throw e
            }
            
            // Store original camera container parameters
            originalCameraParams = cameraContainer.layoutParams as ConstraintLayout.LayoutParams
            
            fullscreenButton.setOnClickListener {
                toggleFullscreen()
            }

            // Initialize FFC button
            ffcButton = findViewById(R.id.ffcButton)
            ffcButton.setOnClickListener {
                performFFC()
            }

            // Initialize brightness controls
            brightnessSlider = findViewById(R.id.brightnessSlider)
            setBrightnessButton = findViewById(R.id.setBrightnessButton)
            setBrightnessButton.setOnClickListener {
                setBrightness(brightnessSlider.progress)
            }

            // Initialize contrast controls
            contrastSlider = findViewById(R.id.contrastSlider)
            setContrastButton = findViewById(R.id.setContrastButton)
            setContrastButton.setOnClickListener {
                setContrast(contrastSlider.progress)
            }

            // Initialize noise reduction controls
            noiseReductionSlider = findViewById(R.id.noiseReductionSlider)
            setNoiseReductionButton = findViewById(R.id.setNoiseReductionButton)
            setNoiseReductionButton.setOnClickListener {
                setNoiseReduction(noiseReductionSlider.progress)
            }

            // Initialize time noise reduction controls
            timeNoiseReductionSlider = findViewById(R.id.timeNoiseReductionSlider)
            setTimeNoiseReductionButton = findViewById(R.id.setTimeNoiseReductionButton)
            setTimeNoiseReductionButton.setOnClickListener {
                setTimeNoiseReduction(timeNoiseReductionSlider.progress)
            }

            // Initialize space noise reduction controls
            spaceNoiseReductionSlider = findViewById(R.id.spaceNoiseReductionSlider)
            setSpaceNoiseReductionButton = findViewById(R.id.setSpaceNoiseReductionButton)
            setSpaceNoiseReductionButton.setOnClickListener {
                setSpaceNoiseReduction(spaceNoiseReductionSlider.progress)
            }

            // Initialize detail enhancement controls
            detailEnhancementSlider = findViewById(R.id.detailEnhancementSlider)
            setDetailEnhancementButton = findViewById(R.id.setDetailEnhancementButton)
            setDetailEnhancementButton.setOnClickListener {
                setDetailEnhancement(detailEnhancementSlider.progress)
            }

            // Initialize global contrast controls
            globalContrastSlider = findViewById(R.id.globalContrastSlider)
            setGlobalContrastButton = findViewById(R.id.setGlobalContrastButton)
            setGlobalContrastButton.setOnClickListener {
                setGlobalContrast(globalContrastSlider.progress)
            }

            // Initialize palette control buttons
            nextPaletteButton = findViewById(R.id.nextPaletteButton)
            prevPaletteButton = findViewById(R.id.prevPaletteButton)
            
            nextPaletteButton.setOnClickListener {
                setPalette((currentPaletteIndex + 1).coerceIn(MIN_PALETTE_INDEX, MAX_PALETTE_INDEX))
            }
            
            prevPaletteButton.setOnClickListener {
                setPalette((currentPaletteIndex - 1).coerceIn(MIN_PALETTE_INDEX, MAX_PALETTE_INDEX))
            }

            // Initialize scene mode control buttons
            nextSceneModeButton = findViewById(R.id.nextSceneModeButton)
            prevSceneModeButton = findViewById(R.id.prevSceneModeButton)
            
            nextSceneModeButton.setOnClickListener {
                setSceneMode((currentSceneModeIndex + 1).coerceIn(MIN_SCENE_MODE_INDEX, MAX_SCENE_MODE_INDEX))
            }
            
            prevSceneModeButton.setOnClickListener {
                setSceneMode((currentSceneModeIndex - 1).coerceIn(MIN_SCENE_MODE_INDEX, MAX_SCENE_MODE_INDEX))
            }

            // Initialize expandable groups
            imageSettingsHeader = findViewById(R.id.imageSettingsHeader)
            imageSettingsContent = findViewById(R.id.imageSettingsContent)
            noiseReductionHeader = findViewById(R.id.noiseReductionHeader)
            noiseReductionContent = findViewById(R.id.noiseReductionContent)

            // Set up expandable groups
            setupExpandableGroup(imageSettingsHeader, imageSettingsContent)
            setupExpandableGroup(noiseReductionHeader, noiseReductionContent)

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
                Toast.makeText(this, "Failed to register USB receiver: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
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
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate", e)
            Toast.makeText(this, "Error initializing camera: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
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
            cameraContainer.layoutParams = params
            
            // Update TextureView to fill the container while maintaining aspect ratio
            val textureParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            cameraView.layoutParams = textureParams
            
            Log.i(TAG, "Fullscreen dimensions - Screen: ${screenWidth}x${screenHeight}, Video: ${targetWidth}x${targetHeight}")
            
        } else {
            // Portrait mode - use constraints from layout file
            val params = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                0
            ).apply {
                topToTop = ConstraintLayout.LayoutParams.PARENT_ID
                bottomToTop = controlsContainer.id
                startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                // Add top margin to ensure it's not cut off
                topMargin = 48
            }
            cameraContainer.layoutParams = params
            
            val textureParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            ).apply {
                gravity = android.view.Gravity.CENTER
            }
            cameraView.layoutParams = textureParams
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

    private fun handleIncomingUsbIntent(intent: Intent) {
        Log.i(TAG, "handleIncomingUsbIntent: Starting USB intent handling")
        
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
            // Store the device and update display immediately
            currentDevice = device
            updateDeviceInfo(device)
            
            // Check if we already have permission
            if (usbManager.hasPermission(device)) {
                if (isOurDevice(device)) {
                    // Only open camera if we're not handling a USB attachment
                    // This prevents automatic streaming when the app is launched via USB attachment
                    if (!isHandlingUsbAttachment) {
                        openUsbCamera(device)
                    } else {
                        // For USB attachment, just show a message that the device is ready
                        Toast.makeText(this, "Camera ready. Press 'Open Camera' to start streaming.", Toast.LENGTH_LONG).show()
                    }
                }
            } else {
                // Request USB permission
                requestUsbPermission(device)
            }
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
            Log.e(TAG, "No configuration found for device ${device.productId}")
            return
        }

        val usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        val permissionIntent = PendingIntent.getBroadcast(this, 0, Intent(ACTION_USB_PERMISSION), PendingIntent.FLAG_IMMUTABLE)
        usbManager.requestPermission(device, permissionIntent)

        if (!usbManager.hasPermission(device)) {
            Log.e(TAG, "No permission to access USB device")
            return
        }

        val connection = usbManager.openDevice(device)
        if (connection == null) {
            Log.e(TAG, "Could not open connection")
            return
        }

        try {
            val fd = connection.fileDescriptor
            if (fd == -1) {
                Log.e(TAG, "Invalid file descriptor")
                return
            }

            // Pass the device configuration to native code
            if (!nativeOpenUvcCamera(fd, deviceConfig.width, deviceConfig.height, deviceConfig.fps)) {
                Log.e(TAG, "Failed to open UVC camera")
                return
            }

            // Set up the video surface
            setupVideoSurface()

            // Initialize IrcmdManager with the device configuration
            val ircmdManager = IrcmdManager.getInstance()
            if (!ircmdManager.init(fd, deviceConfig.deviceType)) {
                Log.e(TAG, "Failed to initialize IrcmdManager")
                return
            }
            Log.i(TAG, "IrcmdManager initialized with device type: ${deviceConfig.deviceType}")

        } catch (e: Exception) {
            Log.e(TAG, "Error opening camera", e)
        }
    }

    private fun setupVideoSurface() {
        // Set up a surface texture listener
        cameraView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(texture: SurfaceTexture, width: Int, height: Int) {
                // Start streaming when surface is available
                val surface = Surface(texture)
                if (nativeStartStreaming(surface)) {
                    Log.i(TAG, "UVC streaming started successfully")
                    
                    // Get the camera dimensions from native code
                    val dimensions = nativeGetCameraDimensions()
                    if (dimensions != null) {
                        // Update the container's aspect ratio to match camera
                        val params = cameraContainer.layoutParams as ConstraintLayout.LayoutParams
                        if (!isFullscreen) {
                            // Only set aspect ratio in portrait mode
                            params.dimensionRatio = "${dimensions.first}:${dimensions.second}"
                            cameraContainer.layoutParams = params
                        }
                        Log.i(TAG, "Camera dimensions: ${dimensions.first}x${dimensions.second}")
                    }
                } else {
                    Log.e(TAG, "Failed to start UVC streaming")
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
        
        // Stop streaming if it's active
        nativeStopStreaming()
        
        // Close the UVC camera
        nativeCloseUvcCamera()
        
        // Clean up IrcmdManager
        ircmdManager.cleanup()
        
        // Close the USB connection
        deviceConnection?.close()
        deviceConnection = null
        
        // Clean up handler
        permissionCheckHandler.removeCallbacks(permissionCheckRunnable)
        
        // Unregister receiver
        try {
            unregisterReceiver(usbPermissionReceiver)
        } catch (ignored: IllegalArgumentException) {
            // Receiver was already unregistered
        }
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
            Toast.makeText(this, "No USB devices found", Toast.LENGTH_SHORT).show()
            finish()
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
        
        Toast.makeText(this, "No compatible camera found", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun updateLastCommand(command: String, value: Int, success: Boolean = true, errorMessage: String? = null, errorCode: Int? = null) {
        runOnUiThread {
            val status = if (success) "Success" else "Failed"
            val errorText = if (!success && errorMessage != null) {
                val codeText = if (errorCode != null) " (code: $errorCode)" else ""
                " - $errorMessage$codeText"
            } else ""
            lastCommandText.text = "Last Command: $command = $value ($status$errorText)"
        }
    }

    private fun performFFC() {
        if (!ircmdManager.isInitialized()) {
            Log.e(TAG, "FFC failed: Camera not initialized (code: ${IrcmdManager.ERROR_NOT_INITIALIZED})")
            updateLastCommand("FFC", 0, false, "Camera not initialized", IrcmdManager.ERROR_NOT_INITIALIZED)
            return
        }

        // Disable button during FFC
        ffcButton.isEnabled = false
        ffcButton.text = "FFC in progress..."

        // Run FFC in background thread
        Thread {
            try {
                val result = ircmdManager.performFFC()
                
                // Update UI on main thread
                runOnUiThread {
                    ffcButton.isEnabled = true
                    ffcButton.text = "FFC"
                    
                    when (result) {
                        IrcmdManager.ERROR_SUCCESS -> {
                            updateLastCommand("FFC", 0, true)
                        }
                        IrcmdManager.ERROR_NOT_INITIALIZED -> {
                            Log.e(TAG, "FFC failed: Camera not initialized (code: $result)")
                            updateLastCommand("FFC", 0, false, "Camera not initialized", result)
                        }
                        IrcmdManager.ERROR_USB_WRITE -> {
                            Log.e(TAG, "FFC failed: USB write error (code: $result)")
                            updateLastCommand("FFC", 0, false, "Failed to send FFC command", result)
                        }
                        else -> {
                            Log.e(TAG, "FFC failed: ${ircmdManager.getLastErrorMessage()} (code: $result)")
                            updateLastCommand("FFC", 0, false, ircmdManager.getLastErrorMessage(), result)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error performing FFC", e)
                runOnUiThread {
                    ffcButton.isEnabled = true
                    ffcButton.text = "FFC"
                    updateLastCommand("FFC", 0, false, e.message ?: "Unknown error", -99)
                }
            }
        }.start()
    }
    
    private fun setBrightness(level: Int) {
        if (!ircmdManager.isInitialized()) {
            Log.e(TAG, "Set brightness failed: Camera not initialized (code: ${IrcmdManager.ERROR_NOT_INITIALIZED})")
            updateLastCommand("Brightness", level, false, "Camera not initialized", IrcmdManager.ERROR_NOT_INITIALIZED)
            return
        }

        // Run in background thread
        Thread {
            val result = ircmdManager.setBrightness(level)
            
            // Update UI on main thread
            runOnUiThread {
                when (result) {
                    IrcmdManager.ERROR_SUCCESS -> {
                        Log.i(TAG, "Brightness set to $level successfully")
                        updateLastCommand("Brightness", level, true)
                    }
                    IrcmdManager.ERROR_NOT_INITIALIZED -> {
                        Log.e(TAG, "Set brightness failed: Camera not initialized (code: $result)")
                        updateLastCommand("Brightness", level, false, "Camera not initialized", result)
                    }
                    else -> {
                        Log.e(TAG, "Set brightness failed: ${ircmdManager.getLastErrorMessage()} (code: $result)")
                        updateLastCommand("Brightness", level, false, ircmdManager.getLastErrorMessage(), result)
                    }
                }
            }
        }.start()
    }
    
    private fun setContrast(level: Int) {
        if (!ircmdManager.isInitialized()) {
            Log.e(TAG, "Set contrast failed: Camera not initialized (code: ${IrcmdManager.ERROR_NOT_INITIALIZED})")
            updateLastCommand("Contrast", level, false, "Camera not initialized", IrcmdManager.ERROR_NOT_INITIALIZED)
            return
        }

        // Run in background thread
        Thread {
            val result = ircmdManager.setContrast(level)
            
            // Update UI on main thread
            runOnUiThread {
                when (result) {
                    IrcmdManager.ERROR_SUCCESS -> {
                        Log.i(TAG, "Contrast set to $level successfully")
                        updateLastCommand("Contrast", level, true)
                    }
                    IrcmdManager.ERROR_NOT_INITIALIZED -> {
                        Log.e(TAG, "Set contrast failed: Camera not initialized (code: $result)")
                        updateLastCommand("Contrast", level, false, "Camera not initialized", result)
                    }
                    else -> {
                        Log.e(TAG, "Set contrast failed: ${ircmdManager.getLastErrorMessage()} (code: $result)")
                        updateLastCommand("Contrast", level, false, ircmdManager.getLastErrorMessage(), result)
                    }
                }
            }
        }.start()
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
            (controlsContainer as View).visibility = View.GONE
            
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
            (controlsContainer as View).visibility = View.VISIBLE
            
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
        if (!ircmdManager.isInitialized()) {
            Log.e(TAG, "Set palette failed: Camera not initialized (code: ${IrcmdManager.ERROR_NOT_INITIALIZED})")
            updateLastCommand("Palette", newIndex, false, "Camera not initialized", IrcmdManager.ERROR_NOT_INITIALIZED)
            return
        }

        // Handle wrapping around
        currentPaletteIndex = when {
            newIndex > MAX_PALETTE_INDEX -> MIN_PALETTE_INDEX
            newIndex < MIN_PALETTE_INDEX -> MAX_PALETTE_INDEX
            else -> newIndex
        }

        // Run in background thread
        Thread {
            val result = ircmdManager.setPalette(currentPaletteIndex)
            
            // Update UI on main thread
            runOnUiThread {
                when (result) {
                    IrcmdManager.ERROR_SUCCESS -> {
                        Log.i(TAG, "Palette set to ${PALETTE_NAMES[currentPaletteIndex]} (index: $currentPaletteIndex)")
                        updateLastCommand("Palette", currentPaletteIndex, true)
                    }
                    IrcmdManager.ERROR_NOT_INITIALIZED -> {
                        Log.e(TAG, "Set palette failed: Camera not initialized (code: $result)")
                        updateLastCommand("Palette", currentPaletteIndex, false, "Camera not initialized", result)
                    }
                    else -> {
                        Log.e(TAG, "Set palette failed: ${ircmdManager.getLastErrorMessage()} (code: $result)")
                        updateLastCommand("Palette", currentPaletteIndex, false, ircmdManager.getLastErrorMessage(), result)
                    }
                }
            }
        }.start()
    }

    private fun setSceneMode(newIndex: Int) {
        if (!ircmdManager.isInitialized()) {
            Log.e(TAG, "Set scene mode failed: Camera not initialized (code: ${IrcmdManager.ERROR_NOT_INITIALIZED})")
            updateLastCommand("Scene Mode", newIndex, false, "Camera not initialized", IrcmdManager.ERROR_NOT_INITIALIZED)
            return
        }

        // Handle wrapping around
        currentSceneModeIndex = when {
            newIndex > MAX_SCENE_MODE_INDEX -> MIN_SCENE_MODE_INDEX
            newIndex < MIN_SCENE_MODE_INDEX -> MAX_SCENE_MODE_INDEX
            else -> newIndex
        }

        // Run in background thread
        Thread {
            val result = ircmdManager.setSceneMode(currentSceneModeIndex)
            
            // Update UI on main thread
            runOnUiThread {
                when (result) {
                    IrcmdManager.ERROR_SUCCESS -> {
                        Log.i(TAG, "Scene mode set to ${SCENE_MODE_NAMES[currentSceneModeIndex]} (index: $currentSceneModeIndex)")
                        updateLastCommand("Scene Mode", currentSceneModeIndex, true)
                    }
                    IrcmdManager.ERROR_NOT_INITIALIZED -> {
                        Log.e(TAG, "Set scene mode failed: Camera not initialized (code: $result)")
                        updateLastCommand("Scene Mode", currentSceneModeIndex, false, "Camera not initialized", result)
                    }
                    else -> {
                        Log.e(TAG, "Set scene mode failed: ${ircmdManager.getLastErrorMessage()} (code: $result)")
                        updateLastCommand("Scene Mode", currentSceneModeIndex, false, ircmdManager.getLastErrorMessage(), result)
                    }
                }
            }
        }.start()
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
                        Toast.makeText(this, "Camera permission is required to use the thermal camera", Toast.LENGTH_LONG).show()
                        finish()
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
                    Toast.makeText(this, "Camera permission is required to use the thermal camera", Toast.LENGTH_LONG).show()
                    finish()
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
        permissionCheckHandler.post(permissionCheckRunnable)
    }

    private fun setNoiseReduction(level: Int) {
        if (!ircmdManager.isInitialized()) {
            Log.e(TAG, "Set noise reduction failed: Camera not initialized (code: ${IrcmdManager.ERROR_NOT_INITIALIZED})")
            updateLastCommand("Noise Reduction", level, false, "Camera not initialized", IrcmdManager.ERROR_NOT_INITIALIZED)
            return
        }

        // Run in background thread
        Thread {
            val result = ircmdManager.setNoiseReduction(level)
            
            // Update UI on main thread
            runOnUiThread {
                when (result) {
                    IrcmdManager.ERROR_SUCCESS -> {
                        Log.i(TAG, "Noise reduction set to $level successfully")
                        updateLastCommand("Noise Reduction", level, true)
                    }
                    IrcmdManager.ERROR_NOT_INITIALIZED -> {
                        Log.e(TAG, "Set noise reduction failed: Camera not initialized (code: $result)")
                        updateLastCommand("Noise Reduction", level, false, "Camera not initialized", result)
                    }
                    else -> {
                        Log.e(TAG, "Set noise reduction failed: ${ircmdManager.getLastErrorMessage()} (code: $result)")
                        updateLastCommand("Noise Reduction", level, false, ircmdManager.getLastErrorMessage(), result)
                    }
                }
            }
        }.start()
    }

    private fun setTimeNoiseReduction(level: Int) {
        if (!ircmdManager.isInitialized()) {
            Log.e(TAG, "Set time noise reduction failed: Camera not initialized (code: ${IrcmdManager.ERROR_NOT_INITIALIZED})")
            updateLastCommand("Time Noise Reduction", level, false, "Camera not initialized", IrcmdManager.ERROR_NOT_INITIALIZED)
            return
        }

        // Run in background thread
        Thread {
            val result = ircmdManager.setTimeNoiseReduction(level)
            
            // Update UI on main thread
            runOnUiThread {
                when (result) {
                    IrcmdManager.ERROR_SUCCESS -> {
                        Log.i(TAG, "Time noise reduction set to $level successfully")
                        updateLastCommand("Time Noise Reduction", level, true)
                    }
                    IrcmdManager.ERROR_NOT_INITIALIZED -> {
                        Log.e(TAG, "Set time noise reduction failed: Camera not initialized (code: $result)")
                        updateLastCommand("Time Noise Reduction", level, false, "Camera not initialized", result)
                    }
                    else -> {
                        Log.e(TAG, "Set time noise reduction failed: ${ircmdManager.getLastErrorMessage()} (code: $result)")
                        updateLastCommand("Time Noise Reduction", level, false, ircmdManager.getLastErrorMessage(), result)
                    }
                }
            }
        }.start()
    }

    private fun setSpaceNoiseReduction(level: Int) {
        if (!ircmdManager.isInitialized()) {
            Log.e(TAG, "Set space noise reduction failed: Camera not initialized (code: ${IrcmdManager.ERROR_NOT_INITIALIZED})")
            updateLastCommand("Space Noise Reduction", level, false, "Camera not initialized", IrcmdManager.ERROR_NOT_INITIALIZED)
            return
        }

        // Run in background thread
        Thread {
            val result = ircmdManager.setSpaceNoiseReduction(level)
            
            // Update UI on main thread
            runOnUiThread {
                when (result) {
                    IrcmdManager.ERROR_SUCCESS -> {
                        Log.i(TAG, "Space noise reduction set to $level successfully")
                        updateLastCommand("Space Noise Reduction", level, true)
                    }
                    IrcmdManager.ERROR_NOT_INITIALIZED -> {
                        Log.e(TAG, "Set space noise reduction failed: Camera not initialized (code: $result)")
                        updateLastCommand("Space Noise Reduction", level, false, "Camera not initialized", result)
                    }
                    else -> {
                        Log.e(TAG, "Set space noise reduction failed: ${ircmdManager.getLastErrorMessage()} (code: $result)")
                        updateLastCommand("Space Noise Reduction", level, false, ircmdManager.getLastErrorMessage(), result)
                    }
                }
            }
        }.start()
    }

    private fun setDetailEnhancement(level: Int) {
        if (!ircmdManager.isInitialized()) {
            Log.e(TAG, "Set detail enhancement failed: Camera not initialized (code: ${IrcmdManager.ERROR_NOT_INITIALIZED})")
            updateLastCommand("Detail Enhancement", level, false, "Camera not initialized", IrcmdManager.ERROR_NOT_INITIALIZED)
            return
        }

        // Run in background thread
        Thread {
            val result = ircmdManager.setDetailEnhancement(level)
            
            // Update UI on main thread
            runOnUiThread {
                when (result) {
                    IrcmdManager.ERROR_SUCCESS -> {
                        Log.i(TAG, "Detail enhancement set to $level successfully")
                        updateLastCommand("Detail Enhancement", level, true)
                    }
                    IrcmdManager.ERROR_NOT_INITIALIZED -> {
                        Log.e(TAG, "Set detail enhancement failed: Camera not initialized (code: $result)")
                        updateLastCommand("Detail Enhancement", level, false, "Camera not initialized", result)
                    }
                    else -> {
                        Log.e(TAG, "Set detail enhancement failed: ${ircmdManager.getLastErrorMessage()} (code: $result)")
                        updateLastCommand("Detail Enhancement", level, false, ircmdManager.getLastErrorMessage(), result)
                    }
                }
            }
        }.start()
    }

    private fun setGlobalContrast(level: Int) {
        if (!ircmdManager.isInitialized()) {
            Log.e(TAG, "Set global contrast failed: Camera not initialized (code: ${IrcmdManager.ERROR_NOT_INITIALIZED})")
            updateLastCommand("Global Contrast", level, false, "Camera not initialized", IrcmdManager.ERROR_NOT_INITIALIZED)
            return
        }

        // Run in background thread
        Thread {
            val result = ircmdManager.setGlobalContrast(level)
            
            // Update UI on main thread
            runOnUiThread {
                when (result) {
                    IrcmdManager.ERROR_SUCCESS -> {
                        Log.i(TAG, "Global contrast set to $level successfully")
                        updateLastCommand("Global Contrast", level, true)
                    }
                    IrcmdManager.ERROR_NOT_INITIALIZED -> {
                        Log.e(TAG, "Set global contrast failed: Camera not initialized (code: $result)")
                        updateLastCommand("Global Contrast", level, false, "Camera not initialized", result)
                    }
                    else -> {
                        Log.e(TAG, "Set global contrast failed: ${ircmdManager.getLastErrorMessage()} (code: $result)")
                        updateLastCommand("Global Contrast", level, false, ircmdManager.getLastErrorMessage(), result)
                    }
                }
            }
        }.start()
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
}
