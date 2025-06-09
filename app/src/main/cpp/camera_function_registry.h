#pragma once

#include <unordered_map>
#include <functional>
#include <memory>
#include <android/log.h>
#include "libircmd.h"

// Logging macros
#define REGISTRY_TAG "CameraFunctionRegistry"
#define REGISTRY_LOGI(...) __android_log_print(ANDROID_LOG_INFO, REGISTRY_TAG, __VA_ARGS__)
#define REGISTRY_LOGW(...) __android_log_print(ANDROID_LOG_WARN, REGISTRY_TAG, __VA_ARGS__)
#define REGISTRY_LOGE(...) __android_log_print(ANDROID_LOG_ERROR, REGISTRY_TAG, __VA_ARGS__)

// Function types for camera operations
enum class FunctionType {
    SET = 0,    // Functions that set a parameter value
    GET = 1,    // Functions that get a parameter value  
    ACTION = 2  // Functions that perform an action (no parameters)
};

// Camera function IDs - these will replace the manual enum synchronization
enum class CameraFunctionId {
    // Image processing functions
    BRIGHTNESS = 1000,
    CONTRAST = 1001,
    GLOBAL_CONTRAST = 1002,
    DETAIL_ENHANCEMENT = 1003,
    NOISE_REDUCTION = 1004,
    ROI_LEVEL = 1005,
    AGC_LEVEL = 1006,
    
    // Scene and palette functions
    SCENE_MODE = 2000,
    PALETTE_INDEX = 2001,
    
    // Action functions
    FFC_UPDATE = 3000,
    
    // Advanced functions for future expansion
    GAMMA_LEVEL = 4000,
    EDGE_ENHANCE = 4001,
    TIME_NOISE_REDUCTION = 4002,
    SPACE_NOISE_REDUCTION = 4003,
    
    // Device control functions (MINI2-compatible SET only)
    DEVICE_SLEEP = 5000,
    ANALOG_VIDEO_OUTPUT = 5001,
    OUTPUT_FRAME_RATE = 5002,
    YUV_FORMAT = 5003,
    SHUTTER_STATUS = 5004,
    PICTURE_FREEZE = 5005,
    MIRROR_AND_FLIP = 5006,
    AUTO_FFC_STATUS = 5007,
    ALL_FFC_FUNCTION_STATUS = 5008,
    
    // Add more as needed...
};

// Type aliases for function signatures
using SetFunction = std::function<int(IrcmdHandle_t*, int)>;
using SetFunction2 = std::function<int(IrcmdHandle_t*, int, int)>; // For functions with 2 int parameters
using GetFunction = std::function<int(IrcmdHandle_t*, int*)>;
using ActionFunction = std::function<int(IrcmdHandle_t*)>;

/**
 * Registry class that maps function IDs to actual libircmd.h function calls
 * This eliminates the need for manual enum synchronization across layers
 */
class CameraFunctionRegistry {
public:
    static CameraFunctionRegistry& getInstance() {
        static CameraFunctionRegistry instance;
        return instance;
    }

    // Register functions for different types
    void registerSetFunction(CameraFunctionId id, SetFunction func);
    void registerSetFunction2(CameraFunctionId id, SetFunction2 func);
    void registerGetFunction(CameraFunctionId id, GetFunction func);
    void registerActionFunction(CameraFunctionId id, ActionFunction func);

    // Execute functions by ID
    int executeSetFunction(CameraFunctionId id, IrcmdHandle_t* handle, int value);
    int executeSetFunction2(CameraFunctionId id, IrcmdHandle_t* handle, int value1, int value2);
    int executeGetFunction(CameraFunctionId id, IrcmdHandle_t* handle, int* value);
    int executeActionFunction(CameraFunctionId id, IrcmdHandle_t* handle);

    // Check if function is registered
    bool isSetFunctionRegistered(CameraFunctionId id) const;
    bool isSetFunction2Registered(CameraFunctionId id) const;
    bool isGetFunctionRegistered(CameraFunctionId id) const;
    bool isActionFunctionRegistered(CameraFunctionId id) const;

    // Initialize all function mappings
    void initializeAllFunctions();

    // Get function information
    size_t getRegisteredFunctionCount() const;
    void logRegisteredFunctions() const;

private:
    CameraFunctionRegistry() = default;
    ~CameraFunctionRegistry() = default;
    
    // Delete copy constructor and assignment operator
    CameraFunctionRegistry(const CameraFunctionRegistry&) = delete;
    CameraFunctionRegistry& operator=(const CameraFunctionRegistry&) = delete;

    // Function storage maps
    std::unordered_map<CameraFunctionId, SetFunction> setFunctions_;
    std::unordered_map<CameraFunctionId, SetFunction2> setFunctions2_;
    std::unordered_map<CameraFunctionId, GetFunction> getFunctions_;
    std::unordered_map<CameraFunctionId, ActionFunction> actionFunctions_;

    // Helper methods for initialization
    void initializeImageProcessingFunctions();
    void initializeSceneAndPaletteFunctions();
    void initializeActionFunctions();
    void initializeAdvancedFunctions();
    void initializeDeviceControlFunctions();
};

// Error codes for registry operations
enum class RegistryError {
    SUCCESS = 0,
    FUNCTION_NOT_FOUND = -1001,
    INVALID_HANDLE = -1002,
    INVALID_PARAMETER = -1003,
    SDK_ERROR = -1004
};

// Convert libircmd errors to registry errors
RegistryError convertSdkError(IrlibError_e sdkError);

// Utility function to get error message
const char* getRegistryErrorMessage(RegistryError error);