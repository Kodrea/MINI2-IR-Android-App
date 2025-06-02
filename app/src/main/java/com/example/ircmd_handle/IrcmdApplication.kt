package com.example.ircmd_handle

import android.app.Application
import android.util.Log

class IrcmdApplication : Application() {
    companion object {
        private const val TAG = "IrcmdApplication"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Initializing application")
        
        // Initialize DeviceConfigs
        DeviceConfigs.initialize(this)
    }
} 