package com.example.MINI2_IR

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

/**
 * Data class representing a device configuration
 */
data class DeviceConfig(
    val productId: Int,
    val width: Int,
    val height: Int,
    val fps: Int,
    val deviceType: Int  // Corresponds to device_type_e in C++
) {
    companion object {
        private const val TAG = "DeviceConfig"
        
        // Convert config to string for storage
        fun toString(config: DeviceConfig): String {
            return "${config.productId},${config.width},${config.height},${config.fps},${config.deviceType}"
        }
        
        // Parse config from string
        fun fromString(str: String): DeviceConfig? {
            return try {
                val parts = str.split(",")
                if (parts.size != 5) return null
                DeviceConfig(
                    productId = parts[0].toInt(),
                    width = parts[1].toInt(),
                    height = parts[2].toInt(),
                    fps = parts[3].toInt(),
                    deviceType = parts[4].toInt()
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing device config from string: $str", e)
                null
            }
        }
    }
}

/**
 * Companion object to hold device configurations and device type information
 */
object DeviceConfigs {
    private const val TAG = "DeviceConfigs"
    private const val PREFS_NAME = "device_configs"
    private const val KEY_CONFIGS = "configs"
    
    // Device type information
    data class DeviceTypeInfo(
        val name: String,
        val width: Int,
        val height: Int,
        val fpsOptions: List<Int>
    )

    // Valid device types and their configurations
    val deviceTypes = mapOf(
        3 to DeviceTypeInfo("MINI2-384", 384, 288, listOf(30, 60)),
        7 to DeviceTypeInfo("MINI2-256", 256, 192, listOf(25, 50)),
        8 to DeviceTypeInfo("MINI2-640", 640, 512, listOf(30))
    )

    // Hardcoded MINI2-384 configuration
    private val DEFAULT_CONFIG = DeviceConfig(
        productId = 0x43D1,  // Hardcoded PID for MINI2-384
        width = 384,
        height = 288,
        fps = 60,
        deviceType = 3  // DEV_MINI2_384
    )

    // Start with the default config - additional configurations will be loaded from SharedPreferences
    private var _configs = mutableMapOf(DEFAULT_CONFIG.productId to DEFAULT_CONFIG)
    var configs: Map<Int, DeviceConfig>
        get() = _configs
        set(value) {
            // Ensure the default config is always present
            _configs = (value + (DEFAULT_CONFIG.productId to DEFAULT_CONFIG)).toMutableMap()
            // Save to SharedPreferences whenever configs are updated
            saveConfigs()
        }
    
    private var prefs: SharedPreferences? = null
    
    // Initialize SharedPreferences
    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadConfigs()
    }
    
    // Save configurations to SharedPreferences
    private fun saveConfigs() {
        prefs?.let { prefs ->
            // Don't save the default config to SharedPreferences
            val configStrings = _configs.values
                .filter { it.productId != DEFAULT_CONFIG.productId }
                .map { DeviceConfig.toString(it) }
            prefs.edit().putStringSet(KEY_CONFIGS, configStrings.toSet()).apply()
            Log.d(TAG, "Saved ${configStrings.size} additional device configurations")
        }
    }
    
    // Load configurations from SharedPreferences
    private fun loadConfigs() {
        prefs?.let { prefs ->
            val configStrings = prefs.getStringSet(KEY_CONFIGS, emptySet()) ?: emptySet()
            // Load additional configs while preserving the default config
            _configs = (configStrings.mapNotNull { DeviceConfig.fromString(it) }
                .associateBy { it.productId } + (DEFAULT_CONFIG.productId to DEFAULT_CONFIG)).toMutableMap()
            Log.d(TAG, "Loaded ${_configs.size - 1} additional device configurations")
        }
    }
} 