#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "CodeMarket"

#define LOGV(...) \
    __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jstring JNICALL
Java_ir_codemarket_app_MainActivity_message(
        JNIEnv *env,
        jobject /* thiz */
) {
    std::string message = "Hey Code Market, sent message from C++";

    LOGV("Native message: %s", message.c_str());

    return env->NewStringUTF(message.c_str());
}
