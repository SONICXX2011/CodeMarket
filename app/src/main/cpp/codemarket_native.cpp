#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "CodeMarketNative"
#define LOGV(...) __android_log_print(ANDROID_LOG_VERBOSE, LOG_TAG, __VA_ARGS__)

extern "C"
JNIEXPORT jstring JNICALL
Java_ir_codemarket_app_NativeLib_getBaseUrl(JNIEnv *env, jobject thiz) {
    return env->NewStringUTF("https://host.teohcho.xyz");
}

extern "C"
JNIEXPORT jstring JNICALL
Java_ir_codemarket_app_NativeLib_buildLoginPayload(JNIEnv *env, jobject thiz, jstring username, jstring password) {
    const char *u = env->GetStringUTFChars(username, 0);
    const char *p = env->GetStringUTFChars(password, 0);
    std::string json = "{\"username\":\"" + std::string(u) + "\",\"password\":\"" + std::string(p) + "\"}";
    LOGV("Built Login Payload: %s", json.c_str());
    env->ReleaseStringUTFChars(username, u);
    env->ReleaseStringUTFChars(password, p);
    return env->NewStringUTF(json.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_ir_codemarket_app_NativeLib_buildRegisterPayload(JNIEnv *env, jobject thiz, jstring username, jstring email, jstring password) {
    const char *u = env->GetStringUTFChars(username, 0);
    const char *e = env->GetStringUTFChars(email, 0);
    const char *p = env->GetStringUTFChars(password, 0);
    std::string json = "{\"username\":\"" + std::string(u) + "\",\"email\":\"" + std::string(e) + "\",\"password\":\"" + std::string(p) + "\"}";
    LOGV("Built Register Payload: %s", json.c_str());
    env->ReleaseStringUTFChars(username, u);
    env->ReleaseStringUTFChars(email, e);
    env->ReleaseStringUTFChars(password, p);
    return env->NewStringUTF(json.c_str());
}