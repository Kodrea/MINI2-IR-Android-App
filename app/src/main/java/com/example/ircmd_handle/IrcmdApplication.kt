package com.example.ircmd_handle

import android.app.Application
import android.util.Log

class Mini2IRApplication : Application() {
    companion object {
        private const val TAG = "Mini2IRApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing application")
        
        // Initialize DeviceConfigs
        DeviceConfigs.initialize(this)
    }
} 