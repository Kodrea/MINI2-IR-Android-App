package com.example.ircmd_handle

import android.Manifest
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputEditText
import com.example.ircmd_handle.databinding.ActivityMainBinding
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val ACTION_USB_PERMISSION = "android.hardware.usb.action.USB_PERMISSION"
        private const val CAMERA_PERMISSION_REQUEST_CODE = 1001
        
        // Thermal Camera Co.,Ltd device IDs
        private const val VENDOR_ID = 0x3474
        private const val DEVICE_CLASS = 239
        private const val DEVICE_SUBCLASS = 2

        // Used to load the 'ircmd_handle' library on application startup.
        init {
            System.loadLibrary("ircmd_handle")
        }
    }
    
    // ViewBinding
    private lateinit var binding: ActivityMainBinding
    
    // Coroutine scope for lifecycle-aware operations
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // USB related
    private lateinit var usbManager: UsbManager
    private var currentDevice: UsbDevice? = null
    private lateinit var permissionIntent: PendingIntent

    // USB permission receiver
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.i(TAG, "USB permission receiver: received intent: $intent")
            if (intent.action == ACTION_USB_PERMISSION) {
                synchronized(this) {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }

                    val granted: Boolean = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    Log.i(TAG, "USB permission received - device: $device, granted: $granted, currentDevice: $currentDevice")

                    if (device != null && device == currentDevice) {
                        runOnUiThread {
                            if (granted) {
                                Log.i(TAG, "USB permission granted")
                                showSuccess("USB permission granted")
                                updateDeviceInfo(device)
                                updateButtonStates()
                            } else {
                                Log.i(TAG, "USB permission denied")
                                showError("USB permission denied. Please grant permission to use the thermal camera.") {
                                    findAndRequestPermission()
                                }
                                currentDevice = null
                                updateDeviceInfo(null)
                                updateButtonStates()
                            }
                        }
                    } else {
                        Log.w(TAG, "Device mismatch or null. Current: $currentDevice, Received: $device")
                    }
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        Log.i(TAG, "onCreate")

        // Initialize USB manager
        usbManager = getSystemService(Context.USB_SERVICE) as UsbManager
        
        // Set up button click listeners using ViewBinding
        binding.requestPermissionButton.setOnClickListener {
            findAndRequestPermission()
        }

        binding.openCameraButton.setOnClickListener {
            launchCameraActivity()
        }

        binding.manageDevicesButton.setOnClickListener {
            showManageDevicesDialog()
        }

        binding.addDeviceButton.setOnClickListener {
            showAddDeviceDialog()
        }

        binding.testSuperResolutionButton.setOnClickListener {
            val intent = Intent(this, TfLiteTestActivity::class.java)
            startActivity(intent)
        }

        // Register for USB permission broadcasts
        val filter = IntentFilter(ACTION_USB_PERMISSION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbPermissionReceiver, filter)
        }
        Log.i(TAG, "Registered USB permission receiver")

        // Initial state update
        updateButtonStates()
        updateDeviceInfo(findCameraDevice())
    }

    override fun onResume() {
        super.onResume()
        // Update states when activity resumes
        updateButtonStates()
        updateDeviceInfo(findCameraDevice())
    }

    override fun onDestroy() {
        super.onDestroy()
        
        // Cancel all coroutines to prevent memory leaks
        scope.cancel()
        
        try {
            unregisterReceiver(usbPermissionReceiver)
            Log.i(TAG, "Unregistered USB permission receiver")
        } catch (e: IllegalArgumentException) {
            // Receiver was not registered
            Log.w(TAG, "USB permission receiver was not registered")
        }
    }

    private fun updateDeviceInfo(device: UsbDevice?) {
        Log.i(TAG, "updateDeviceInfo called with device: ${device?.deviceName ?: "null"}")
        
        runOnUiThread {
            val info = if (device != null) {
                val vid = device.vendorId.toString(16)
                val pid = device.productId.toString(16)
                Log.i(TAG, "Setting device info - VID: 0x$vid, PID: 0x$pid")
                "VID: 0x$vid\nPID: 0x$pid"
            } else {
                Log.i(TAG, "Setting 'No device connected' text")
                "No device connected"
            }
            
            try {
                binding.deviceInfoText.text = info
                Log.i(TAG, "Successfully set device info text")
            } catch (e: Exception) {
                Log.e(TAG, "Error setting device info text", e)
            }
        }
    }

    private fun updateButtonStates() {
        // Find our camera device if it exists
        val cameraDevice = findCameraDevice()
        currentDevice = cameraDevice

        // Update button states based on device and permission status
        if (cameraDevice != null) {
            // We have a compatible device - always enable request permission button
            binding.requestPermissionButton.isEnabled = true
            
            // Only enable open camera button if we have permission
            if (usbManager.hasPermission(cameraDevice)) {
                binding.openCameraButton.isEnabled = true
                Log.i(TAG, "Camera device found and permission granted")
            } else {
                binding.openCameraButton.isEnabled = false
                Log.i(TAG, "Camera device found but needs permission")
            }
        } else {
            // No compatible device found - disable both buttons
            binding.requestPermissionButton.isEnabled = false
            binding.openCameraButton.isEnabled = false
            Log.i(TAG, "No compatible camera device found")
        }
    }

    private fun findCameraDevice(): UsbDevice? {
        val deviceList = usbManager.deviceList
        if (deviceList.isEmpty()) {
            Log.i(TAG, "No USB devices found")
            return null
        }

        for ((_, device) in deviceList) {
            Log.i(TAG, "Found device: ${device.deviceName}, vendor: ${device.vendorId}, product: ${device.productId}")
            if (isOurDevice(device)) {
                Log.i(TAG, "Found compatible camera device")
                return device
            }
        }

        Log.i(TAG, "No compatible camera device found")
        return null
    }

    private fun findAndRequestPermission() {
        // First check camera permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Camera permission not granted, requesting...")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
            return
        }

        // If we have camera permission, proceed with USB permission
        val cameraDevice = findCameraDevice()
        if (cameraDevice == null) {
            showError("No compatible camera found. Please connect your camera.")
            return
        }

        if (usbManager.hasPermission(cameraDevice)) {
            Log.i(TAG, "Already have permission")
            updateButtonStates()
            updateDeviceInfo(cameraDevice)
            return
        }

        Log.i(TAG, "Requesting USB permission")
        
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
        
        try {
            // Request the permission
            usbManager.requestPermission(cameraDevice, permissionIntent)
            Log.i(TAG, "USB permission request sent")
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting USB permission", e)
            showError("Error requesting USB permission: ${e.message}") {
                findAndRequestPermission()
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
                    // Now that we have camera permission, proceed with USB permission
                    findAndRequestPermission()
                } else {
                    Log.w(TAG, "Camera permission denied")
                    showError("Camera permission is required to use the thermal camera")
                }
            }
        }
    }

    private fun launchCameraActivity() {
        // Check camera permission first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.i(TAG, "Camera permission not granted, requesting...")
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
            return
        }

        val device = currentDevice
        if (device == null || !usbManager.hasPermission(device)) {
            Log.w(TAG, "Cannot launch camera: device not found or no permission")
            showError("Cannot open camera: device not found or no permission") {
                findAndRequestPermission()
            }
            updateButtonStates()
            return
        }

        Log.i(TAG, "Launching camera activity with device: $device")
        val intent = Intent(this, CameraActivity::class.java).apply {
            putExtra(UsbManager.EXTRA_DEVICE, device)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    private fun isOurDevice(device: UsbDevice): Boolean {
        return device.vendorId == VENDOR_ID &&
               device.deviceClass == DEVICE_CLASS &&
               device.deviceSubclass == DEVICE_SUBCLASS
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

    private fun showManageDevicesDialog() {
        val deviceList = DeviceConfigs.configs.entries.joinToString("\n") { (pid, config) ->
            "PID: 0x${pid.toString(16)}\n" +
            "Resolution: ${config.width}x${config.height}\n" +
            "FPS: ${config.fps}\n" +
            "Type: ${when(config.deviceType) {
                3 -> "MINI2-384"
                7 -> "MINI2-256"
                8 -> "MINI2-640"
                else -> "Unknown"
            }}\n"
        }

        AlertDialog.Builder(this)
            .setTitle("Device Configurations")
            .setMessage(if (deviceList.isEmpty()) "No device configurations found" else deviceList)
            .setPositiveButton("OK", null)
            .show()
    }

    private fun showAddDeviceDialog() {
        Log.i(TAG, "showAddDeviceDialog: Starting dialog creation")
        
        val dialog = AlertDialog.Builder(this)
            .setTitle("Add New Device Configuration")
            .setView(R.layout.dialog_add_device)
            .setPositiveButton("Add") { dialog, _ ->
                val dialogView = (dialog as AlertDialog).findViewById<View>(android.R.id.content)
                val pidEdit = dialogView?.findViewById<TextInputEditText>(R.id.pidEdit)
                val deviceTypeSpinner = dialogView?.findViewById<Spinner>(R.id.deviceTypeSpinner)
                val fpsSpinner = dialogView?.findViewById<Spinner>(R.id.fpsSpinner)
                
                try {
                    // Parse PID value
                    val pidText = pidEdit?.text?.toString()?.removePrefix("0x") ?: throw IllegalArgumentException("PID is required")
                    val pid = pidText.toInt(16)
                    
                    // Get device type
                    val deviceType = when(deviceTypeSpinner?.selectedItem?.toString()) {
                        "MINI2-384" -> 3
                        "MINI2-256" -> 7
                        "MINI2-640" -> 8
                        else -> throw IllegalArgumentException("Invalid device type")
                    }
                    
                    // Get device type info
                    val typeInfo = DeviceConfigs.deviceTypes[deviceType] 
                        ?: throw IllegalArgumentException("Invalid device type")
                    
                    // Get selected FPS
                    val fps = fpsSpinner?.selectedItem?.toString()?.removeSuffix(" fps")?.toInt() 
                        ?: throw IllegalArgumentException("FPS is required")
                    
                    // Validate FPS
                    if (!typeInfo.fpsOptions.contains(fps)) {
                        throw IllegalArgumentException("Invalid FPS for this device type")
                    }
                    
                    // Create new config
                    val newConfig = DeviceConfig(
                        pid,
                        typeInfo.width,
                        typeInfo.height,
                        fps,
                        deviceType
                    )
                    
                    // Add the configuration
                    DeviceConfigs.configs = DeviceConfigs.configs + (pid to newConfig)
                    
                    showSuccess("Device configuration added")
                } catch (e: Exception) {
                    Log.e(TAG, "Error adding device configuration", e)
                    showError("Invalid input: ${e.message}")
                }
            }
            .setNegativeButton("Cancel", null)
            .create()
        
        dialog.setOnShowListener {
            val dialogView = dialog.findViewById<View>(android.R.id.content)
            val pidEdit = dialogView?.findViewById<TextInputEditText>(R.id.pidEdit)
            val deviceTypeSpinner = dialogView?.findViewById<Spinner>(R.id.deviceTypeSpinner)
            val resolutionText = dialogView?.findViewById<TextView>(R.id.resolutionText)
            val fpsSpinner = dialogView?.findViewById<Spinner>(R.id.fpsSpinner)
            
            // Pre-fill PID if we have a current device
            currentDevice?.let { device ->
                pidEdit?.setText("0x${device.productId.toString(16)}")
            }
            
            // Set up device type spinner
            val deviceTypes = DeviceConfigs.deviceTypes.values.map { it.name }
            deviceTypeSpinner?.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, deviceTypes)
            deviceTypeSpinner?.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                    val selectedType = DeviceConfigs.deviceTypes.values.elementAt(position)
                    resolutionText?.text = "Resolution: ${selectedType.width}x${selectedType.height}"
                    
                    // Update FPS spinner
                    val fpsAdapter = ArrayAdapter(
                        this@MainActivity,
                        android.R.layout.simple_spinner_item,
                        selectedType.fpsOptions.map { "$it fps" }
                    )
                    fpsSpinner?.adapter = fpsAdapter
                }
                
                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }
        }
        
        dialog.show()
    }

    /**
     * A native method that is implemented by the 'ircmd_handle' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String
}