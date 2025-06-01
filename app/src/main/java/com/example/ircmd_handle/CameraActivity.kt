package com.example.ircmd_handle

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintLayout.LayoutParams

class CameraActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraActivity"
        private const val ACTION_USB_PERMISSION = "android.hardware.usb.action.USB_PERMISSION"
        private const val PERMISSION_REQUEST_TIMEOUT = 5000L // 5 seconds
        
        // Thermal Camera Co.,Ltd device IDs
        private const val VENDOR_ID = 13428
        private const val PRODUCT_ID = 17361
        private const val DEVICE_CLASS = 239
        private const val DEVICE_SUBCLASS = 2
        
        // Intent extra to find device manually
        const val EXTRA_FIND_DEVICE = "com.example.ircmd_handle.FIND_DEVICE"
        
        // Load the native library
        init {
            System.loadLibrary("ircmd_handle")
        }
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // Initialize IrcmdManager
            ircmdManager = IrcmdManager.getInstance()
            
            // 1) Set up the UI first
            setContentView(R.layout.activity_camera)

            // Initialize FFC button
            ffcButton = findViewById(R.id.ffcButton)
            ffcButton.setOnClickListener {
                performFFC()
            }

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
                    findAndConnectCamera()
                } else {
                    // Normal USB intent handling
                    handleIncomingUsbIntent(intent)
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

    private fun isOurDevice(device: UsbDevice): Boolean {
        return device.vendorId == VENDOR_ID &&
               device.productId == PRODUCT_ID &&
               device.deviceClass == DEVICE_CLASS &&
               device.deviceSubclass == DEVICE_SUBCLASS
    }

    private fun handleIncomingUsbIntent(intent: Intent) {
        Log.i(TAG, "handleIncomingUsbIntent: Received intent: $intent")
        Log.i(TAG, "handleIncomingUsbIntent: Intent action: ${intent.action}")
        Log.i(TAG, "handleIncomingUsbIntent: Intent extras: ${intent.extras}")

        // Only care about ACTION_USB_DEVICE_ATTACHED
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            Log.i(TAG, "handleIncomingUsbIntent: Received USB_DEVICE_ATTACHED intent")
            
            // 1) Extract the UsbDevice that was attached
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            Log.i(TAG, "handleIncomingUsbIntent: device=$device")

            if (device != null) {
                // Verify this is our device
                if (!isOurDevice(device)) {
                    Log.w(TAG, "handleIncomingUsbIntent: Device is not our thermal camera")
                    Toast.makeText(this, "Unsupported USB device", Toast.LENGTH_SHORT).show()
                    finish()
                    return
                }

                // Store the current device
                currentDevice = device

                // Check if we already have permission for this device
                val hasPermission = usbManager.hasPermission(device)
                Log.i(TAG, "handleIncomingUsbIntent: hasPermission=$hasPermission")

                if (hasPermission) {
                    // We already have permission—open the camera immediately
                    Log.i(TAG, "handleIncomingUsbIntent: Already have permission, opening camera")
                    openUsbCamera(device)
                } else {
                    // We do NOT have permission yet—ask for it now
                    Log.i(TAG, "Requesting permission for device: $device")
                    
                    // Create a new intent for this specific device
                    permissionIntent = PendingIntent.getBroadcast(
                        this,
                        0,
                        Intent(ACTION_USB_PERMISSION).apply {
                            putExtra(UsbManager.EXTRA_DEVICE, device)
                            // Add flags to ensure the intent is delivered
                            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        },
                        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                    )
                    
                    // Record the time we sent the request
                    permissionRequestTime = System.currentTimeMillis()
                    
                    // Start the permission check loop
                    permissionCheckHandler.post(permissionCheckRunnable)
                    
                    try {
                        // Request the permission
                        usbManager.requestPermission(device, permissionIntent)
                        Log.i(TAG, "Permission request sent to system for device: ${device.deviceName}")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception when requesting permission", e)
                        Toast.makeText(this, "Security error requesting USB permission", Toast.LENGTH_SHORT).show()
                        finish()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error requesting permission", e)
                        Toast.makeText(this, "Error requesting USB permission: ${e.message}", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            } else {
                Log.w(TAG, "handleIncomingUsbIntent: ACTION_USB_DEVICE_ATTACHED but device == null")
                Toast.makeText(this, "No USB device found", Toast.LENGTH_SHORT).show()
                finish()
            }
        } else {
            Log.w(TAG, "handleIncomingUsbIntent: Unexpected intent action: ${intent.action}")
        }
    }

    private fun openUsbCamera(device: UsbDevice) {
        // 1) Open a connection to the USB device
        deviceConnection = usbManager.openDevice(device)
        if (deviceConnection == null) {
            Toast.makeText(this, "Failed to open USB device: $device", Toast.LENGTH_SHORT).show()
            return
        }

        // 2) Grab the raw file descriptor
        val fd: Int = deviceConnection!!.fileDescriptor
        Log.i(TAG, "openUsbCamera: fd: $fd")
        
        // 3) Initialize IrcmdManager with the file descriptor
        if (!ircmdManager.init(fd)) {
            val errorMsg = ircmdManager.getLastErrorMessage()
            Log.e(TAG, "Failed to initialize IrcmdManager: $errorMsg")
            Toast.makeText(this, "Failed to initialize camera SDK: $errorMsg", Toast.LENGTH_SHORT).show()
            deviceConnection?.close()
            return
        }
        
        // 4) Call native method to initialize the UVC camera
        val success = nativeOpenUvcCamera(fd)
        
        if (success) {
            // 5) Create a Surface for video rendering
            setupVideoSurface()
        } else {
            Toast.makeText(this, "Failed to initialize UVC stream", Toast.LENGTH_SHORT).show()
            deviceConnection?.close()
            ircmdManager.cleanup()
        }
    }

    private fun setupVideoSurface() {
        // Get the SurfaceView and container from your layout
        val surfaceView = findViewById<SurfaceView>(R.id.surfaceView)
        val container = findViewById<FrameLayout>(R.id.cameraContainer)
        
        // Set up a surface holder callback
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                // Start streaming when surface is created
                val surface = holder.surface
                if (nativeStartStreaming(surface)) {
                    Log.i(TAG, "UVC streaming started successfully")
                    
                    // Get the camera dimensions from native code
                    val dimensions = nativeGetCameraDimensions()
                    if (dimensions != null) {
                        // Update the container's aspect ratio
                        val params = container.layoutParams as ConstraintLayout.LayoutParams
                        params.dimensionRatio = "${dimensions.first}:${dimensions.second}"
                        container.layoutParams = params
                        Log.i(TAG, "Updated container aspect ratio to ${dimensions.first}:${dimensions.second}")
                    }
                } else {
                    Log.e(TAG, "Failed to start UVC streaming")
                }
            }
            
            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                // Handle surface changes if needed
                Log.i(TAG, "Surface changed: width=$width, height=$height")
            }
            
            override fun surfaceDestroyed(holder: SurfaceHolder) {
                // Stop streaming when surface is destroyed
                nativeStopStreaming()
            }
        })
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
    private external fun nativeOpenUvcCamera(fd: Int): Boolean
    private external fun nativeStartStreaming(surface: Surface): Boolean
    private external fun nativeStopStreaming()
    private external fun nativeCloseUvcCamera()
    private external fun nativeGetCameraDimensions(): Pair<Int, Int>?

    /**
     * Find a compatible camera device that's already connected and attempt to connect to it.
     * This can be used when launching the activity from a button rather than a USB intent.
     */
    private fun findAndConnectCamera() {
        Log.i(TAG, "findAndConnectCamera: Looking for compatible USB camera")
        
        // Get the list of connected USB devices
        val deviceList = usbManager.deviceList
        
        if (deviceList.isEmpty()) {
            Log.w(TAG, "findAndConnectCamera: No USB devices found")
            Toast.makeText(this, "No USB devices found. Please connect your camera.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        Log.i(TAG, "findAndConnectCamera: Found ${deviceList.size} USB devices")
        
        // Find our camera in the device list
        var cameraDevice: UsbDevice? = null
        
        for ((_, device) in deviceList) {
            Log.i(TAG, "Found device: ${device.deviceName}, vendor: ${device.vendorId}, product: ${device.productId}, class: ${device.deviceClass}")
            
            if (isOurDevice(device)) {
                Log.i(TAG, "findAndConnectCamera: Found our camera device")
                cameraDevice = device
                break
            }
        }
        
        if (cameraDevice == null) {
            Log.w(TAG, "findAndConnectCamera: Camera not found among connected devices")
            Toast.makeText(this, "USB camera not found. Please connect your camera.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        
        // Store the current device
        currentDevice = cameraDevice
        
        // Check if we already have permission
        if (usbManager.hasPermission(cameraDevice)) {
            Log.i(TAG, "findAndConnectCamera: Already have permission, opening camera")
            openUsbCamera(cameraDevice)
        } else {
            Log.i(TAG, "findAndConnectCamera: Need to request permission")
            
            // Create a permission intent
            permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_USB_PERMISSION).apply {
                    putExtra(UsbManager.EXTRA_DEVICE, cameraDevice)
                    addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            
            // Record the time we sent the request
            permissionRequestTime = System.currentTimeMillis()
            
            // Start the permission check loop
            permissionCheckHandler.post(permissionCheckRunnable)
            
            try {
                // Request the permission
                usbManager.requestPermission(cameraDevice, permissionIntent)
                Log.i(TAG, "Permission request sent to system for device: ${cameraDevice.deviceName}")
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting permission", e)
                Toast.makeText(this, "Error requesting USB permission: ${e.message}", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }

    private fun performFFC() {
        if (!ircmdManager.isInitialized()) {
            Toast.makeText(this, "Camera not initialized", Toast.LENGTH_SHORT).show()
            return
        }

        // Disable button during FFC
        ffcButton.isEnabled = false
        ffcButton.text = "FFC in progress..."

        // Run FFC in background thread
        Thread {
            val result = ircmdManager.performFFC()
            
            // Update UI on main thread
            runOnUiThread {
                ffcButton.isEnabled = true
                ffcButton.text = "FFC"
                
                when (result) {
                    IrcmdManager.ERROR_SUCCESS -> {
                        Toast.makeText(this, "FFC completed successfully", Toast.LENGTH_SHORT).show()
                    }
                    IrcmdManager.ERROR_NOT_INITIALIZED -> {
                        Toast.makeText(this, "Camera not initialized", Toast.LENGTH_SHORT).show()
                    }
                    IrcmdManager.ERROR_USB_WRITE -> {
                        Toast.makeText(this, "Failed to send FFC command", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        Toast.makeText(this, "FFC failed: ${ircmdManager.getLastErrorMessage()}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }.start()
    }
}
