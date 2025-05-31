#include <jni.h>
#include <string>

extern "C" JNIEXPORT jstring JNICALL
Java_com_example_ircmd_1handle_MainActivity_stringFromJNI(
        JNIEnv* env,
        jobject /* this */) {
    std::string hello = "Hello from C++";
    return env->NewStringUTF(hello.c_str());
}
extern "C"
JNIEXPORT jboolean JNICALL
Java_com_example_ircmd_1handle_CameraActivity_nativeOpenUvcCamera(JNIEnv *env, jobject thiz,
                                                                  jint fd) {
    // For now, just return false to indicate camera initialization failed
    // This prevents the crash while we implement proper UVC camera handling
    return JNI_FALSE;
}