#include <jni.h>
#include <string>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <cstring>
#include <cstdint>
#include <chrono>
#include "uvc_manager.h"
#include "libircmd.h"
#include "ircmd_manager.h"
#include "camera_function_registry.h"

// Global camera instance
static std::unique_ptr<UVCCamera> g_camera;

// Global IrcmdManager instance
static std::unique_ptr<IrcmdManager> g_ircmd_manager;

// Global variables for direct video recording
static JavaVM* g_jvm = nullptr;
static jobject g_video_recorder_obj = nullptr;
static jmethodID g_encoder_callback_method = nullptr;

// Add new global variables to store the current device configuration
static int g_current_width = 384;
static int g_current_height = 288;
static int g_current_fps = 60;

// Native callback function for direct video encoding
void nativeVideoEncoderCallback(uint8_t* yuvData, int width, int height, int64_t timestampUs, void* userPtr) {
    if (g_jvm == nullptr || g_video_recorder_obj == nullptr || g_encoder_callback_method == nullptr) {
        return;
    }
    
    JNIEnv* env;
    if (g_jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return;
    }
    
    // Create a Java byte array for the YUV data
    int dataSize = width * height * 3 / 2; // YUV420 size
    jbyteArray yuvArray = env->NewByteArray(dataSize);
    if (yuvArray == nullptr) {
        return;
    }
    
    env->SetByteArrayRegion(yuvArray, 0, dataSize, reinterpret_cast<jbyte*>(yuvData));
    
    // Call the Java callback method
    env->CallVoidMethod(g_video_recorder_obj, g_encoder_callback_method, 
                       yuvArray, width, height, timestampUs);
    
    // Clean up local reference
    env->DeleteLocalRef(yuvArray);
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_ircmd_1handle_CameraActivity_nativeOpenUvcCamera(JNIEnv* env, jobject thiz, jint fd, jint width, jint height, jint fps) {
    // Store the device configuration
    g_current_width = width;
    g_current_height = height;
    g_current_fps = fps;
    
    if (!g_camera) {
        g_camera = std::make_unique<UVCCamera>();
    }
    return g_camera->init(fd) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_example_ircmd_1handle_CameraActivity_nativeStartStreaming(JNIEnv* env, jobject thiz, jobject surface) {
    if (!g_camera) {
        __android_log_print(ANDROID_LOG_ERROR, "CameraActivity", "Camera not initialized");
        return JNI_FALSE;
    }

    // Get the native window from the surface
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) {
        __android_log_print(ANDROID_LOG_ERROR, "CameraActivity", "Failed to get native window from surface");
        return JNI_FALSE;
    }

    // Use the stored device configuration
    bool result = g_camera->startStream(g_current_width, g_current_height, g_current_fps, window);
    if (!result) {
        ANativeWindow_release(window);
    }
    
    return result ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_example_ircmd_1handle_CameraActivity_nativeStopStreaming(JNIEnv* env, jobject thiz) {
    if (g_camera) {
        g_camera->stopStream();
    }
}

JNIEXPORT void JNICALL
Java_com_example_ircmd_1handle_CameraActivity_nativeCloseUvcCamera(JNIEnv* env, jobject thiz) {
    if (g_camera) {
        g_camera->cleanup();
        g_camera.reset();
    }
}

JNIEXPORT jobject JNICALL
Java_com_example_ircmd_1handle_CameraActivity_nativeGetCameraDimensions(JNIEnv* env, jobject /* this */) {
    if (!g_camera) {
        __android_log_print(ANDROID_LOG_ERROR, "CameraActivity", "Camera not initialized");
        return nullptr;
    }

    // Get the current format from the device handle
    const uvc_format_desc_t* format_desc = uvc_get_format_descs(g_camera->getDeviceHandle());
    if (!format_desc) {
        __android_log_print(ANDROID_LOG_ERROR, "CameraActivity", "Failed to get format descriptors");
        return nullptr;
    }

    // Find the first supported format (usually the highest resolution)
    const uvc_format_desc_t* current_format = format_desc;
    while (current_format) {
        if (current_format->bDescriptorSubtype == UVC_VS_FORMAT_UNCOMPRESSED) {
            // Get the frame descriptor
            const uvc_frame_desc_t* frame_desc = current_format->frame_descs;
            if (frame_desc) {
                // Create a Pair object to return the dimensions
                jclass pairClass = env->FindClass("kotlin/Pair");
                jmethodID pairConstructor = env->GetMethodID(pairClass, "<init>", "(Ljava/lang/Object;Ljava/lang/Object;)V");
                
                // Create Integer objects for width and height
                jclass integerClass = env->FindClass("java/lang/Integer");
                jmethodID integerConstructor = env->GetMethodID(integerClass, "<init>", "(I)V");
                
                jobject widthObj = env->NewObject(integerClass, integerConstructor, frame_desc->wWidth);
                jobject heightObj = env->NewObject(integerClass, integerConstructor, frame_desc->wHeight);
                
                // Create and return the Pair
                jobject pair = env->NewObject(pairClass, pairConstructor, widthObj, heightObj);
                
                // Clean up local references
                env->DeleteLocalRef(widthObj);
                env->DeleteLocalRef(heightObj);
                env->DeleteLocalRef(integerClass);
                env->DeleteLocalRef(pairClass);
                
                return pair;
            }
        }
        current_format = current_format->next;
    }
    
    __android_log_print(ANDROID_LOG_ERROR, "CameraActivity", "No supported format found");
    return nullptr;
}

JNIEXPORT jboolean JNICALL
Java_com_example_ircmd_1handle_IrcmdManager_nativeInit(JNIEnv* env, jobject thiz, jint fileDescriptor, jint deviceType) {
    if (!g_ircmd_manager) {
        g_ircmd_manager = std::make_unique<IrcmdManager>();
    }
    return g_ircmd_manager->init(fileDescriptor, deviceType);
}

JNIEXPORT void JNICALL
Java_com_example_ircmd_1handle_IrcmdManager_nativeCleanup(JNIEnv* env, jobject thiz) {
    if (g_ircmd_manager) {
        g_ircmd_manager->cleanup();
    }
}

JNIEXPORT jint JNICALL
Java_com_example_ircmd_1handle_IrcmdManager_nativeGetLastError(JNIEnv* env, jobject thiz) {
    if (!g_ircmd_manager) {
        return 0;  // No error if manager doesn't exist
    }
    return g_ircmd_manager->getLastError();
}

JNIEXPORT jstring JNICALL
Java_com_example_ircmd_1handle_IrcmdManager_nativeGetLastErrorMessage(JNIEnv* env, jobject thiz) {
    if (!g_ircmd_manager) {
        return env->NewStringUTF("IrcmdManager not initialized");
    }
    return env->NewStringUTF(g_ircmd_manager->getLastErrorMessage());
}

JNIEXPORT jint JNICALL
Java_com_example_ircmd_1handle_IrcmdManager_nativePerformFFC(JNIEnv* env, jobject thiz) {
    if (!g_ircmd_manager) {
        return -2;  // Error code for not initialized
    }
    return g_ircmd_manager->executeActionFunction(PERFORM_FFC);
}

JNIEXPORT jint JNICALL
Java_com_example_ircmd_1handle_IrcmdManager_nativeExecuteGetFunction(JNIEnv* env, jobject thiz, jint functionId, jobject resultObj) {
    if (!g_ircmd_manager) {
        return -2;  // Error code for not initialized
    }
    
    int value = 0;
    int result = g_ircmd_manager->executeGetFunction(static_cast<CameraFunction>(functionId), value);
    
    // Set the output value using JNI
    jclass integerClass = env->GetObjectClass(resultObj);
    jfieldID valueField = env->GetFieldID(integerClass, "value", "I");
    env->SetIntField(resultObj, valueField, value);
    
    return result;
}

JNIEXPORT jint JNICALL
Java_com_example_ircmd_1handle_IrcmdManager_nativeExecuteSetFunction(JNIEnv* env, jobject thiz, jint functionId, jint value) {
    if (!g_ircmd_manager) {
        return -2;  // Error code for not initialized
    }
    return g_ircmd_manager->executeSetFunction(static_cast<CameraFunction>(functionId), value);
}

JNIEXPORT jint JNICALL
Java_com_example_ircmd_1handle_IrcmdManager_nativeExecuteActionFunction(JNIEnv* env, jobject thiz, jint functionId) {
    if (!g_ircmd_manager) {
        return -2;  // Error code for not initialized
    }
    return g_ircmd_manager->executeActionFunction(static_cast<CameraFunction>(functionId));
}

// ===== NEW UNIFIED REGISTRY-BASED JNI FUNCTIONS =====

JNIEXPORT jint JNICALL
Java_com_example_ircmd_1handle_IrcmdManager_nativeExecuteRegistrySetFunction(JNIEnv* env, jobject thiz, jint functionId, jint value) {
    if (!g_ircmd_manager) {
        return -2;  // Error code for not initialized
    }
    return g_ircmd_manager->executeSetFunction(static_cast<CameraFunctionId>(functionId), value);
}

JNIEXPORT jint JNICALL
Java_com_example_ircmd_1handle_IrcmdManager_nativeExecuteRegistrySetFunction2(JNIEnv* env, jobject thiz, jint functionId, jint value1, jint value2) {
    if (!g_ircmd_manager) {
        return -2;  // Error code for not initialized
    }
    return g_ircmd_manager->executeSetFunction2(static_cast<CameraFunctionId>(functionId), value1, value2);
}

JNIEXPORT jint JNICALL
Java_com_example_ircmd_1handle_IrcmdManager_nativeExecuteRegistryGetFunction(JNIEnv* env, jobject thiz, jint functionId, jobject resultObj) {
    if (!g_ircmd_manager) {
        return -2;  // Error code for not initialized
    }
    
    int value = 0;
    int result = g_ircmd_manager->executeGetFunction(static_cast<CameraFunctionId>(functionId), value);
    
    // Set the output value using JNI
    jclass integerClass = env->GetObjectClass(resultObj);
    jfieldID valueField = env->GetFieldID(integerClass, "value", "I");
    env->SetIntField(resultObj, valueField, value);
    
    return result;
}

JNIEXPORT jint JNICALL
Java_com_example_ircmd_1handle_IrcmdManager_nativeExecuteRegistryActionFunction(JNIEnv* env, jobject thiz, jint functionId) {
    if (!g_ircmd_manager) {
        return -2;  // Error code for not initialized
    }
    return g_ircmd_manager->executeActionFunction(static_cast<CameraFunctionId>(functionId));
}

// Function to check if a function is supported
JNIEXPORT jboolean JNICALL
Java_com_example_ircmd_1handle_IrcmdManager_nativeIsFunctionSupported(JNIEnv* env, jobject thiz, jint functionType, jint functionId) {
    auto& registry = CameraFunctionRegistry::getInstance();
    CameraFunctionId id = static_cast<CameraFunctionId>(functionId);
    
    switch (functionType) {
        case 0: // SET function
            return registry.isSetFunctionRegistered(id) ? JNI_TRUE : JNI_FALSE;
        case 1: // GET function
            return registry.isGetFunctionRegistered(id) ? JNI_TRUE : JNI_FALSE;
        case 2: // ACTION function
            return registry.isActionFunctionRegistered(id) ? JNI_TRUE : JNI_FALSE;
        default:
            return JNI_FALSE;
    }
}

// Function to get total number of registered functions
JNIEXPORT jint JNICALL
Java_com_example_ircmd_1handle_IrcmdManager_nativeGetRegisteredFunctionCount(JNIEnv* env, jobject thiz) {
    auto& registry = CameraFunctionRegistry::getInstance();
    return static_cast<jint>(registry.getRegisteredFunctionCount());
}

// ===== RAW FRAME CAPTURE FOR SUPER RESOLUTION =====

// Set capture flag to capture next raw frame
JNIEXPORT void JNICALL
Java_com_example_ircmd_1handle_CameraActivity_nativeSetCaptureFlag(JNIEnv* env, jobject thiz, jboolean capture) {
    if (g_camera) {
        g_camera->setCaptureNextFrame(capture);
        __android_log_print(ANDROID_LOG_INFO, "NativeLib", "🎯 Set capture flag: %s", capture ? "true" : "false");
    } else {
        __android_log_print(ANDROID_LOG_ERROR, "NativeLib", "Camera not initialized for capture");
    }
}

// Check if a new frame has been captured
JNIEXPORT jboolean JNICALL
Java_com_example_ircmd_1handle_CameraActivity_nativeHasCapturedFrame(JNIEnv* env, jobject thiz) {
    if (g_camera) {
        return g_camera->hasNewCapturedFrame() ? JNI_TRUE : JNI_FALSE;
    }
    return JNI_FALSE;
}

// Get the captured raw frame data
JNIEXPORT jbyteArray JNICALL
Java_com_example_ircmd_1handle_CameraActivity_nativeGetCapturedFrame(JNIEnv* env, jobject thiz) {
    if (!g_camera) {
        __android_log_print(ANDROID_LOG_ERROR, "NativeLib", "Camera not initialized");
        return nullptr;
    }
    
    // Allocate buffer for 256x192 YUYV data (max size)
    const int max_data_size = 256 * 192 * 2; // YUYV = 2 bytes per pixel
    uint8_t* buffer = new uint8_t[max_data_size];
    int width, height;
    
    bool success = g_camera->getCapturedFrameData(buffer, &width, &height);
    if (!success) {
        delete[] buffer;
        __android_log_print(ANDROID_LOG_WARN, "NativeLib", "No captured frame available");
        return nullptr;
    }
    
    // Calculate actual data size
    int actual_data_size = width * height * 2; // YUYV format
    
    // Create Java byte array and copy data
    jbyteArray result = env->NewByteArray(actual_data_size + 8); // +8 for width/height header
    if (result) {
        jbyte* java_buffer = env->GetByteArrayElements(result, nullptr);
        
        // Pack width and height as first 8 bytes (4 bytes each, little endian)
        *reinterpret_cast<int32_t*>(java_buffer) = width;
        *reinterpret_cast<int32_t*>(java_buffer + 4) = height;
        
        // Copy frame data
        std::memcpy(java_buffer + 8, buffer, actual_data_size);
        
        env->ReleaseByteArrayElements(result, java_buffer, 0);
        
        __android_log_print(ANDROID_LOG_INFO, "NativeLib", 
                           "📸 Returned captured frame: %dx%d, %d bytes", 
                           width, height, actual_data_size);
    }
    
    delete[] buffer;
    return result;
}

// ===== UVC FRAMERATE CONTROL JNI FUNCTIONS =====

JNIEXPORT jintArray JNICALL
Java_com_example_ircmd_1handle_CameraActivity_nativeGetSupportedFrameRates(JNIEnv *env, jobject /* this */, jint width, jint height) {
    if (!g_camera) {
        LOGE("No camera instance");
        return nullptr;
    }

    std::vector<int> frameRates = g_camera->getSupportedFrameRates(width, height);
    
    if (frameRates.empty()) {
        LOGE("No supported frame rates found for %dx%d", width, height);
        return nullptr;
    }

    // Create Java int array
    jintArray result = env->NewIntArray(frameRates.size());
    if (result == nullptr) {
        LOGE("Failed to create int array");
        return nullptr;
    }

    // Copy frame rates to Java array
    env->SetIntArrayRegion(result, 0, frameRates.size(), frameRates.data());
    
    LOGI("Returning %zu supported frame rates for %dx%d", frameRates.size(), width, height);
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_example_ircmd_1handle_CameraActivity_nativeSetFrameRate(JNIEnv *env, jobject /* this */, jint width, jint height, jint fps) {
    if (!g_camera) {
        LOGE("No camera instance");
        return JNI_FALSE;
    }

    bool success = g_camera->setFrameRate(width, height, fps);
    LOGI("Set frame rate to %d fps for %dx%d: %s", fps, width, height, success ? "SUCCESS" : "FAILED");
    return success ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_example_ircmd_1handle_CameraActivity_nativeGetCurrentFrameRate(JNIEnv *env, jobject /* this */) {
    if (!g_camera) {
        LOGE("No camera instance");
        return 0;
    }

    int fps = g_camera->getCurrentFrameRate();
    LOGI("Current frame rate: %d fps", fps);
    return fps;
}

JNIEXPORT void JNICALL
Java_com_example_ircmd_1handle_CameraActivity_nativeEnumerateAllFrameRates(JNIEnv *env, jobject /* this */) {
    if (!g_camera) {
        LOGE("No camera instance");
        return;
    }

    g_camera->enumerateAllFrameRates();
}

// ===== DIRECT VIDEO RECORDING JNI METHODS =====

JNIEXPORT void JNICALL
Java_com_example_ircmd_1handle_VideoRecorder_nativeSetupDirectRecording(JNIEnv *env, jobject videoRecorderObj) {
    // Store Java VM reference for callbacks
    env->GetJavaVM(&g_jvm);
    
    // Store global reference to VideoRecorder object
    g_video_recorder_obj = env->NewGlobalRef(videoRecorderObj);
    
    // Get the callback method ID
    jclass videoRecorderClass = env->GetObjectClass(videoRecorderObj);
    g_encoder_callback_method = env->GetMethodID(videoRecorderClass, "onNativeYUVFrame", "([BIIJ)V");
    
    if (g_encoder_callback_method == nullptr) {
        LOGE("Failed to find onNativeYUVFrame method");
        return;
    }
    
    // Set up the native callback in UVC camera
    if (g_camera) {
        g_camera->setVideoEncoderCallback(nativeVideoEncoderCallback, nullptr);
        LOGI("✅ Direct video recording setup complete");
    } else {
        LOGE("No camera instance for direct recording setup");
    }
}

JNIEXPORT void JNICALL
Java_com_example_ircmd_1handle_VideoRecorder_nativeStartDirectRecording(JNIEnv *env, jobject /* this */) {
    if (!g_camera) {
        LOGE("No camera instance for direct recording");
        return;
    }
    
    // Set recording start time
    auto now = std::chrono::duration_cast<std::chrono::microseconds>(
        std::chrono::steady_clock::now().time_since_epoch()
    ).count();
    
    // Enable video recording in UVC camera
    g_camera->setVideoRecordingEnabled(true);
    
    LOGI("🎥 Direct video recording started");
}

JNIEXPORT void JNICALL
Java_com_example_ircmd_1handle_VideoRecorder_nativeStopDirectRecording(JNIEnv *env, jobject /* this */) {
    if (!g_camera) {
        LOGE("No camera instance for direct recording");
        return;
    }
    
    // Disable video recording in UVC camera
    g_camera->setVideoRecordingEnabled(false);
    
    LOGI("🛑 Direct video recording stopped");
}

JNIEXPORT void JNICALL
Java_com_example_ircmd_1handle_VideoRecorder_nativeCleanupDirectRecording(JNIEnv *env, jobject /* this */) {
    if (g_video_recorder_obj != nullptr) {
        env->DeleteGlobalRef(g_video_recorder_obj);
        g_video_recorder_obj = nullptr;
    }
    
    g_encoder_callback_method = nullptr;
    g_jvm = nullptr;
    
    LOGI("🧹 Direct video recording cleanup complete");
}

} // extern "C"