package com.example.ircmd_handle

import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.content.Context
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import android.app.AlertDialog
import android.content.Intent

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.hardware.usb.UsbDeviceConnection
import androidx.activity.result.ActivityResultLauncher

class MainActivity : AppCompatActivity() {
    // Tag for logging
    private val TAG = "MyUsbApp"
    
    // Button to launch camera activity
    private lateinit var connectButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Log.i(TAG, "onCreate")

        // Initialize the connect button
        connectButton = findViewById(R.id.connectButton)
        
        // Set up button click listener to launch CameraActivity
        connectButton.setOnClickListener {
            val intent = Intent(this, CameraActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * A native method that is implemented by the 'ircmd_handle' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String

    companion object {
        // Used to load the 'ircmd_handle' library on application startup.
        init {
            System.loadLibrary("ircmd_handle")
        }

        // Action string for USB permission
        private const val ACTION_USB_PERMISSION = "com.example.ircmd_handle.USB_PERMISSION"
    }
}