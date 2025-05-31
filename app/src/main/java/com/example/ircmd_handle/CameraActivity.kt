package com.example.ircmd_handle

import android.app.PendingIntent
import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class CameraActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "CameraActivity"
        private const val ACTION_USB_PERMISSION = "com.example.ircmd_handle.USB_PERMISSION"
    }

    private lateinit var usbManager: UsbManager
    private var deviceConnection: UsbDeviceConnection? = null

    // 1) A PendingIntent that we'll use when calling requestPermission(...)
    private lateinit var permissionIntent: PendingIntent

    // 2) BroadcastReceiver to catch the user's response (Allow / Deny) to the permission dialog
    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_USB_PERMISSION) {
                // 2a) Extract the UsbDevice from the extras
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }

                // 2b) Check whether the user granted permission
                val granted: Boolean = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

                Log.i(TAG, "USB permission broadcast: device=$device, granted=$granted")

                if (device != null) {
                    if (granted) {
                        // 2c) Permission was granted—open the camera now
                        openUsbCamera(device)
                    } else {
                        // 2d) Permission denied—show a message and finish
                        Toast.makeText(context, "USB permission denied", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }

                // 2e) We can unregister this receiver if we only needed it once
                unregisterReceiver(this)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // 1) Set up the UI first
            setContentView(R.layout.activity_camera)

            // 2) Get the system UsbManager
            usbManager = getSystemService(UsbManager::class.java)

            // 3) Prepare the PendingIntent for USB permission requests
            permissionIntent = PendingIntent.getBroadcast(
                this,
                0,
                Intent(ACTION_USB_PERMISSION),
                PendingIntent.FLAG_IMMUTABLE
            )

            // 4) Register our BroadcastReceiver for ACTION_USB_PERMISSION
            val filter = IntentFilter(ACTION_USB_PERMISSION)
            registerReceiver(usbPermissionReceiver, filter)

            // 5) Handle the Intent that launched this Activity
            Log.i(TAG, "onCreate: received intent: $intent")
            if (intent != null) {
                handleIncomingUsbIntent(intent)
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

    private fun handleIncomingUsbIntent(intent: Intent) {
        // Only care about ACTION_USB_DEVICE_ATTACHED
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            // 1) Extract the UsbDevice that was attached
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }

            // 2) Check if, somehow, permission was already granted
            val alreadyGranted: Boolean = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)

            Log.i(TAG, "handleIncomingUsbIntent: device=$device, alreadyGranted=$alreadyGranted")

            if (device != null) {
                if (alreadyGranted) {
                    // 3a) We already have permission—open the camera immediately
                    openUsbCamera(device)
                } else {
                    // 3b) We do NOT have permission yet—ask for it now
                    Log.i(TAG, "Requesting permission for device: $device")
                    usbManager.requestPermission(device, permissionIntent)
                    // **Important**: do NOT finish() here. Wait for the broadcast callback.
                }
            } else {
                Log.w(TAG, "handleIncomingUsbIntent: ACTION_USB_DEVICE_ATTACHED but device == null")
            }
        }
    }

    private fun openUsbCamera(device: UsbDevice) {
        // 1) Open a connection to the USB device
        deviceConnection = usbManager.openDevice(device)
        if (deviceConnection == null) {
            Toast.makeText(this, "Failed to open USB device: $device", Toast.LENGTH_SHORT).show()
            return
        }

        // 2) Grab the raw file descriptor. Your native/libuvc code needs this.
        val fd: Int = deviceConnection!!.fileDescriptor
        Log.i(TAG, "openUsbCamera: fd: $fd")
        
        // 3) Call your native (JNI) entry point (or UVCCamera, etc.)
        val success = nativeOpenUvcCamera(fd)
        if (success) {
            Toast.makeText(this, "UVC streaming started!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Failed to initialize UVC stream", Toast.LENGTH_SHORT).show()
            // If initialization failed, close the connection right away
            deviceConnection?.close()
        }

        // IMPORTANT: Do NOT close deviceConnection here if streaming is running.
        // Only close it when you're completely done (e.g. in onDestroy()).
    }

    override fun onDestroy() {
        super.onDestroy()
        // Clean up: if we have an open UsbDeviceConnection, close it
        deviceConnection?.close()

        // Also unregister the permissionReceiver if it's still registered
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
}
