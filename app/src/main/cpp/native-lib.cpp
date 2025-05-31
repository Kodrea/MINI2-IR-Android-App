#include <jni.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include "uvc_manager.h"

// Global camera instance
static std::unique_ptr<UVCCamera> g_camera;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_example_ircmd_1handle_CameraActivity_nativeOpenUvcCamera(JNIEnv* env, jobject thiz, jint fd) {
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

    // Default to 640x480@30fps, but you may want to adjust based on your camera
    bool result = g_camera->startStream(384, 288, 60, window);
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

} // extern "C"