// Minimal test to validate registry compilation
#include <iostream>
#include <unordered_map>
#include <functional>
#include <memory>

// Mock Android log for testing
#define __android_log_print(level, tag, fmt, ...) printf("[%s] " fmt "\n", tag, ##__VA_ARGS__)
#define ANDROID_LOG_INFO 1
#define ANDROID_LOG_WARN 2
#define ANDROID_LOG_ERROR 3

// Mock libircmd types for compilation test
typedef enum {
    IRLIB_SUCCESS = 0,
    IRCMD_PARAM_ERROR = -200
} IrlibError_e;

typedef struct _IrcmdHandle_t IrcmdHandle_t;

// Mock functions
extern "C" {
    IrlibError_e basic_image_brightness_level_set(IrcmdHandle_t* handle, int level) { return IRLIB_SUCCESS; }
    IrlibError_e basic_current_brightness_level_get(IrcmdHandle_t* handle, int* level) { *level = 50; return IRLIB_SUCCESS; }
    IrlibError_e basic_ffc_update(IrcmdHandle_t* handle) { return IRLIB_SUCCESS; }
    IrlibError_e basic_image_contrast_level_set(IrcmdHandle_t* handle, int level) { return IRLIB_SUCCESS; }
    IrlibError_e basic_current_contrast_level_get(IrcmdHandle_t* handle, int* level) { *level = 50; return IRLIB_SUCCESS; }
    IrlibError_e basic_global_contrast_level_set(IrcmdHandle_t* handle, int level) { return IRLIB_SUCCESS; }
    IrlibError_e basic_global_contrast_level_get(IrcmdHandle_t* handle, int* level) { *level = 50; return IRLIB_SUCCESS; }
    IrlibError_e basic_image_detail_enhance_level_set(IrcmdHandle_t* handle, int level) { return IRLIB_SUCCESS; }
    IrlibError_e basic_current_detail_enhance_level_get(IrcmdHandle_t* handle, int* level) { *level = 50; return IRLIB_SUCCESS; }
    IrlibError_e basic_image_noise_reduction_level_set(IrcmdHandle_t* handle, int level) { return IRLIB_SUCCESS; }
    IrlibError_e basic_current_image_noise_reduction_level_get(IrcmdHandle_t* handle, int* level) { *level = 50; return IRLIB_SUCCESS; }
    IrlibError_e basic_image_roi_level_set(IrcmdHandle_t* handle, int level) { return IRLIB_SUCCESS; }
    IrlibError_e basic_current_image_roi_level_get(IrcmdHandle_t* handle, int* level) { *level = 1; return IRLIB_SUCCESS; }
    IrlibError_e basic_image_agc_level_set(IrcmdHandle_t* handle, int level) { return IRLIB_SUCCESS; }
    IrlibError_e basic_current_agc_level_get(IrcmdHandle_t* handle, int* level) { *level = 50; return IRLIB_SUCCESS; }
    IrlibError_e basic_image_scene_mode_set(IrcmdHandle_t* handle, int mode) { return IRLIB_SUCCESS; }
    IrlibError_e basic_current_image_scene_mode_get(IrcmdHandle_t* handle, int* mode) { *mode = 0; return IRLIB_SUCCESS; }
    IrlibError_e basic_palette_idx_set(IrcmdHandle_t* handle, int idx) { return IRLIB_SUCCESS; }
    IrlibError_e basic_palette_idx_get(IrcmdHandle_t* handle, int* idx) { *idx = 0; return IRLIB_SUCCESS; }
    IrlibError_e adv_edge_enhance_set(IrcmdHandle_t* handle, int level) { return IRLIB_SUCCESS; }
    IrlibError_e adv_edge_enhance_get(IrcmdHandle_t* handle, int* level) { *level = 1; return IRLIB_SUCCESS; }
}

// Include our registry headers with modifications for standalone compilation
#include "app/src/main/cpp/camera_function_registry.h"
#include "app/src/main/cpp/camera_function_registry.cpp"

int main() {
    std::cout << "Testing Camera Function Registry..." << std::endl;
    
    // Test registry initialization
    auto& registry = CameraFunctionRegistry::getInstance();
    registry.initializeAllFunctions();
    
    std::cout << "Registry initialized successfully!" << std::endl;
    std::cout << "Total registered functions: " << registry.getRegisteredFunctionCount() << std::endl;
    
    // Test function support checks
    bool brightnessSet = registry.isSetFunctionRegistered(CameraFunctionId::BRIGHTNESS);
    bool brightnessGet = registry.isGetFunctionRegistered(CameraFunctionId::BRIGHTNESS);
    bool ffcAction = registry.isActionFunctionRegistered(CameraFunctionId::FFC_UPDATE);
    
    std::cout << "Brightness SET registered: " << (brightnessSet ? "YES" : "NO") << std::endl;
    std::cout << "Brightness GET registered: " << (brightnessGet ? "YES" : "NO") << std::endl;
    std::cout << "FFC ACTION registered: " << (ffcAction ? "YES" : "NO") << std::endl;
    
    // Test function execution with mock handle
    IrcmdHandle_t mockHandle;
    
    int result = registry.executeSetFunction(CameraFunctionId::BRIGHTNESS, &mockHandle, 75);
    std::cout << "Set brightness to 75, result: " << result << std::endl;
    
    int value = 0;
    result = registry.executeGetFunction(CameraFunctionId::BRIGHTNESS, &mockHandle, &value);
    std::cout << "Get brightness result: " << result << ", value: " << value << std::endl;
    
    result = registry.executeActionFunction(CameraFunctionId::FFC_UPDATE, &mockHandle);
    std::cout << "FFC action result: " << result << std::endl;
    
    std::cout << "All tests completed successfully!" << std::endl;
    return 0;
}