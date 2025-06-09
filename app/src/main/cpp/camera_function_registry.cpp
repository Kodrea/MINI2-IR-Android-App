#include "camera_function_registry.h"
#include "libircmd.h"
#include <sstream>

void CameraFunctionRegistry::registerSetFunction(CameraFunctionId id, SetFunction func) {
    setFunctions_[id] = func;
    REGISTRY_LOGI("Registered SET function for ID: %d", static_cast<int>(id));
}

void CameraFunctionRegistry::registerSetFunction2(CameraFunctionId id, SetFunction2 func) {
    setFunctions2_[id] = func;
    REGISTRY_LOGI("Registered SET2 function for ID: %d", static_cast<int>(id));
}

void CameraFunctionRegistry::registerGetFunction(CameraFunctionId id, GetFunction func) {
    getFunctions_[id] = func;
    REGISTRY_LOGI("Registered GET function for ID: %d", static_cast<int>(id));
}

void CameraFunctionRegistry::registerActionFunction(CameraFunctionId id, ActionFunction func) {
    actionFunctions_[id] = func;
    REGISTRY_LOGI("Registered ACTION function for ID: %d", static_cast<int>(id));
}

int CameraFunctionRegistry::executeSetFunction(CameraFunctionId id, IrcmdHandle_t* handle, int value) {
    if (!handle) {
        REGISTRY_LOGE("Invalid handle for SET function ID: %d", static_cast<int>(id));
        return static_cast<int>(RegistryError::INVALID_HANDLE);
    }

    auto it = setFunctions_.find(id);
    if (it == setFunctions_.end()) {
        REGISTRY_LOGE("SET function not found for ID: %d", static_cast<int>(id));
        return static_cast<int>(RegistryError::FUNCTION_NOT_FOUND);
    }

    REGISTRY_LOGI("Executing SET function ID: %d with value: %d", static_cast<int>(id), value);
    int result = it->second(handle, value);
    
    if (result != 0) {
        REGISTRY_LOGW("SET function ID: %d failed with SDK error: %d", static_cast<int>(id), result);
    }
    
    return result;
}

int CameraFunctionRegistry::executeSetFunction2(CameraFunctionId id, IrcmdHandle_t* handle, int value1, int value2) {
    if (!handle) {
        REGISTRY_LOGE("Invalid handle for SET2 function ID: %d", static_cast<int>(id));
        return static_cast<int>(RegistryError::INVALID_HANDLE);
    }

    auto it = setFunctions2_.find(id);
    if (it == setFunctions2_.end()) {
        REGISTRY_LOGE("SET2 function not found for ID: %d", static_cast<int>(id));
        return static_cast<int>(RegistryError::FUNCTION_NOT_FOUND);
    }

    REGISTRY_LOGI("Executing SET2 function ID: %d with values: %d, %d", static_cast<int>(id), value1, value2);
    int result = it->second(handle, value1, value2);
    
    if (result != 0) {
        REGISTRY_LOGW("SET2 function ID: %d failed with SDK error: %d", static_cast<int>(id), result);
    }
    
    return result;
}

int CameraFunctionRegistry::executeGetFunction(CameraFunctionId id, IrcmdHandle_t* handle, int* value) {
    if (!handle || !value) {
        REGISTRY_LOGE("Invalid parameters for GET function ID: %d", static_cast<int>(id));
        return static_cast<int>(RegistryError::INVALID_PARAMETER);
    }

    auto it = getFunctions_.find(id);
    if (it == getFunctions_.end()) {
        REGISTRY_LOGE("GET function not found for ID: %d", static_cast<int>(id));
        return static_cast<int>(RegistryError::FUNCTION_NOT_FOUND);
    }

    REGISTRY_LOGI("Executing GET function ID: %d", static_cast<int>(id));
    int result = it->second(handle, value);
    
    if (result == 0) {
        REGISTRY_LOGI("GET function ID: %d returned value: %d", static_cast<int>(id), *value);
    } else {
        REGISTRY_LOGW("GET function ID: %d failed with SDK error: %d", static_cast<int>(id), result);
    }
    
    return result;
}

int CameraFunctionRegistry::executeActionFunction(CameraFunctionId id, IrcmdHandle_t* handle) {
    if (!handle) {
        REGISTRY_LOGE("Invalid handle for ACTION function ID: %d", static_cast<int>(id));
        return static_cast<int>(RegistryError::INVALID_HANDLE);
    }

    auto it = actionFunctions_.find(id);
    if (it == actionFunctions_.end()) {
        REGISTRY_LOGE("ACTION function not found for ID: %d", static_cast<int>(id));
        return static_cast<int>(RegistryError::FUNCTION_NOT_FOUND);
    }

    REGISTRY_LOGI("Executing ACTION function ID: %d", static_cast<int>(id));
    int result = it->second(handle);
    
    if (result != 0) {
        REGISTRY_LOGW("ACTION function ID: %d failed with SDK error: %d", static_cast<int>(id), result);
    }
    
    return result;
}

bool CameraFunctionRegistry::isSetFunctionRegistered(CameraFunctionId id) const {
    return setFunctions_.find(id) != setFunctions_.end();
}

bool CameraFunctionRegistry::isSetFunction2Registered(CameraFunctionId id) const {
    return setFunctions2_.find(id) != setFunctions2_.end();
}

bool CameraFunctionRegistry::isGetFunctionRegistered(CameraFunctionId id) const {
    return getFunctions_.find(id) != getFunctions_.end();
}

bool CameraFunctionRegistry::isActionFunctionRegistered(CameraFunctionId id) const {
    return actionFunctions_.find(id) != actionFunctions_.end();
}

void CameraFunctionRegistry::initializeAllFunctions() {
    REGISTRY_LOGI("Initializing all camera functions...");
    
    initializeImageProcessingFunctions();
    initializeSceneAndPaletteFunctions();
    initializeActionFunctions();
    initializeAdvancedFunctions();
    initializeDeviceControlFunctions();
    
    REGISTRY_LOGI("Function registry initialization complete. Total functions: %zu", getRegisteredFunctionCount());
    logRegisteredFunctions();
}

void CameraFunctionRegistry::initializeImageProcessingFunctions() {
    REGISTRY_LOGI("Initializing image processing functions...");
    
    // Brightness functions
    registerSetFunction(CameraFunctionId::BRIGHTNESS, [](IrcmdHandle_t* handle, int value) -> int {
        return static_cast<int>(basic_image_brightness_level_set(handle, value));
    });
    
    registerGetFunction(CameraFunctionId::BRIGHTNESS, [](IrcmdHandle_t* handle, int* value) -> int {
        return static_cast<int>(basic_current_brightness_level_get(handle, value));
    });

    // Contrast functions
    registerSetFunction(CameraFunctionId::CONTRAST, [](IrcmdHandle_t* handle, int value) -> int {
        return static_cast<int>(basic_image_contrast_level_set(handle, value));
    });
    
    registerGetFunction(CameraFunctionId::CONTRAST, [](IrcmdHandle_t* handle, int* value) -> int {
        return static_cast<int>(basic_current_contrast_level_get(handle, value));
    });

    // Global contrast functions
    registerSetFunction(CameraFunctionId::GLOBAL_CONTRAST, [](IrcmdHandle_t* handle, int value) -> int {
        return static_cast<int>(basic_global_contrast_level_set(handle, value));
    });
    
    registerGetFunction(CameraFunctionId::GLOBAL_CONTRAST, [](IrcmdHandle_t* handle, int* value) -> int {
        return static_cast<int>(basic_global_contrast_level_get(handle, value));
    });

    // Detail enhancement functions
    registerSetFunction(CameraFunctionId::DETAIL_ENHANCEMENT, [](IrcmdHandle_t* handle, int value) -> int {
        return static_cast<int>(basic_image_detail_enhance_level_set(handle, value));
    });
    
    registerGetFunction(CameraFunctionId::DETAIL_ENHANCEMENT, [](IrcmdHandle_t* handle, int* value) -> int {
        return static_cast<int>(basic_current_detail_enhance_level_get(handle, value));
    });

    // Noise reduction functions
    registerSetFunction(CameraFunctionId::NOISE_REDUCTION, [](IrcmdHandle_t* handle, int value) -> int {
        return static_cast<int>(basic_image_noise_reduction_level_set(handle, value));
    });
    
    registerGetFunction(CameraFunctionId::NOISE_REDUCTION, [](IrcmdHandle_t* handle, int* value) -> int {
        return static_cast<int>(basic_current_image_noise_reduction_level_get(handle, value));
    });

    // ROI level functions
    registerSetFunction(CameraFunctionId::ROI_LEVEL, [](IrcmdHandle_t* handle, int value) -> int {
        return static_cast<int>(basic_image_roi_level_set(handle, value));
    });
    
    registerGetFunction(CameraFunctionId::ROI_LEVEL, [](IrcmdHandle_t* handle, int* value) -> int {
        return static_cast<int>(basic_current_image_roi_level_get(handle, value));
    });

    // AGC level functions
    registerSetFunction(CameraFunctionId::AGC_LEVEL, [](IrcmdHandle_t* handle, int value) -> int {
        return static_cast<int>(basic_image_agc_level_set(handle, value));
    });
    
    registerGetFunction(CameraFunctionId::AGC_LEVEL, [](IrcmdHandle_t* handle, int* value) -> int {
        return static_cast<int>(basic_current_agc_level_get(handle, value));
    });
}

void CameraFunctionRegistry::initializeSceneAndPaletteFunctions() {
    REGISTRY_LOGI("Initializing scene and palette functions...");
    
    // Scene mode functions
    registerSetFunction(CameraFunctionId::SCENE_MODE, [](IrcmdHandle_t* handle, int value) -> int {
        return static_cast<int>(basic_image_scene_mode_set(handle, value));
    });
    
    registerGetFunction(CameraFunctionId::SCENE_MODE, [](IrcmdHandle_t* handle, int* value) -> int {
        return static_cast<int>(basic_current_image_scene_mode_get(handle, value));
    });

    // Palette functions
    registerSetFunction(CameraFunctionId::PALETTE_INDEX, [](IrcmdHandle_t* handle, int value) -> int {
        return static_cast<int>(basic_palette_idx_set(handle, value));
    });
    
    registerGetFunction(CameraFunctionId::PALETTE_INDEX, [](IrcmdHandle_t* handle, int* value) -> int {
        return static_cast<int>(basic_palette_idx_get(handle, value));
    });
}

void CameraFunctionRegistry::initializeActionFunctions() {
    REGISTRY_LOGI("Initializing action functions...");
    
    // FFC (Flat Field Correction) function
    registerActionFunction(CameraFunctionId::FFC_UPDATE, [](IrcmdHandle_t* handle) -> int {
        return static_cast<int>(basic_ffc_update(handle));
    });
}

void CameraFunctionRegistry::initializeAdvancedFunctions() {
    REGISTRY_LOGI("Initializing advanced functions...");
    
    // These functions may not be available on all devices
    // We register them but they may return errors if not supported
    
    // Gamma level functions (if available)
    registerSetFunction(CameraFunctionId::GAMMA_LEVEL, [](IrcmdHandle_t* handle, int value) -> int {
        // Note: This function may not exist in all SDK versions
        // Return not implemented error for now
        REGISTRY_LOGW("Gamma level function not implemented in current SDK");
        return static_cast<int>(RegistryError::FUNCTION_NOT_FOUND);
    });
    
    // Edge enhance functions (if available)
    registerSetFunction(CameraFunctionId::EDGE_ENHANCE, [](IrcmdHandle_t* handle, int value) -> int {
        return static_cast<int>(adv_edge_enhance_set(handle, value));
    });
    
    registerGetFunction(CameraFunctionId::EDGE_ENHANCE, [](IrcmdHandle_t* handle, int* value) -> int {
        return static_cast<int>(adv_edge_enhance_get(handle, value));
    });
}

void CameraFunctionRegistry::initializeDeviceControlFunctions() {
    REGISTRY_LOGI("Initializing device control functions (MINI2-compatible SET only)...");
    
    // Device sleep control
    registerSetFunction(CameraFunctionId::DEVICE_SLEEP, [](IrcmdHandle_t* handle, int status) -> int {
        return static_cast<int>(adv_device_sleep_set(handle, status));
    });
    
    // Analog video output (2 parameters: status, format) 
    registerSetFunction2(CameraFunctionId::ANALOG_VIDEO_OUTPUT, [](IrcmdHandle_t* handle, int status, int format) -> int {
        return static_cast<int>(adv_analog_video_output_set(handle, status, format));
    });
    
    // Output frame rate
    registerSetFunction(CameraFunctionId::OUTPUT_FRAME_RATE, [](IrcmdHandle_t* handle, int rate) -> int {
        return static_cast<int>(adv_output_frame_rate_set(handle, rate));
    });
    
    // YUV format
    registerSetFunction(CameraFunctionId::YUV_FORMAT, [](IrcmdHandle_t* handle, int format) -> int {
        return static_cast<int>(adv_yuv_format_set(handle, format));
    });
    
    // Shutter status
    registerSetFunction(CameraFunctionId::SHUTTER_STATUS, [](IrcmdHandle_t* handle, int status) -> int {
        return static_cast<int>(adv_shutter_status_set(handle, status));
    });
    
    // Picture freeze
    registerSetFunction(CameraFunctionId::PICTURE_FREEZE, [](IrcmdHandle_t* handle, int status) -> int {
        return static_cast<int>(adv_picture_freeze_status_set(handle, status));
    });
    
    // Mirror and flip
    registerSetFunction(CameraFunctionId::MIRROR_AND_FLIP, [](IrcmdHandle_t* handle, int value) -> int {
        return static_cast<int>(basic_mirror_and_flip_status_set(handle, value));
    });
    
    // Auto FFC status
    registerSetFunction(CameraFunctionId::AUTO_FFC_STATUS, [](IrcmdHandle_t* handle, int status) -> int {
        return static_cast<int>(basic_auto_ffc_status_set(handle, status));
    });
    
    // All FFC function status
    registerSetFunction(CameraFunctionId::ALL_FFC_FUNCTION_STATUS, [](IrcmdHandle_t* handle, int status) -> int {
        return static_cast<int>(basic_all_ffc_function_status_set(handle, status));
    });
    
    REGISTRY_LOGI("Device control functions initialized");
}

size_t CameraFunctionRegistry::getRegisteredFunctionCount() const {
    return setFunctions_.size() + setFunctions2_.size() + getFunctions_.size() + actionFunctions_.size();
}

void CameraFunctionRegistry::logRegisteredFunctions() const {
    REGISTRY_LOGI("=== Registered Functions Summary ===");
    REGISTRY_LOGI("SET functions: %zu", setFunctions_.size());
    REGISTRY_LOGI("SET2 functions: %zu", setFunctions2_.size());
    REGISTRY_LOGI("GET functions: %zu", getFunctions_.size());
    REGISTRY_LOGI("ACTION functions: %zu", actionFunctions_.size());
    REGISTRY_LOGI("Total functions: %zu", getRegisteredFunctionCount());
    
    // Log specific function IDs for debugging
    std::stringstream ss;
    ss << "SET function IDs: ";
    for (const auto& pair : setFunctions_) {
        ss << static_cast<int>(pair.first) << " ";
    }
    REGISTRY_LOGI("%s", ss.str().c_str());
}

RegistryError convertSdkError(IrlibError_e sdkError) {
    switch (sdkError) {
        case IRLIB_SUCCESS:
            return RegistryError::SUCCESS;
        case IRCMD_PARAM_ERROR:
            return RegistryError::INVALID_PARAMETER;
        default:
            return RegistryError::SDK_ERROR;
    }
}

const char* getRegistryErrorMessage(RegistryError error) {
    switch (error) {
        case RegistryError::SUCCESS:
            return "Success";
        case RegistryError::FUNCTION_NOT_FOUND:
            return "Function not found in registry";
        case RegistryError::INVALID_HANDLE:
            return "Invalid camera handle";
        case RegistryError::INVALID_PARAMETER:
            return "Invalid parameter";
        case RegistryError::SDK_ERROR:
            return "SDK error";
        default:
            return "Unknown error";
    }
}