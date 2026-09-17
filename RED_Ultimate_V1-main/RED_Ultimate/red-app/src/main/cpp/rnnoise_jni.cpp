#include <jni.h>
#include <rnnoise.h>
#include <string.h>

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_red_sovereign_calls_RnNoiseProcessor_nativeCreate(JNIEnv *env, jobject thiz) {
    DenoiseState *st = rnnoise_create(NULL);
    return (jlong) st;
}

JNIEXPORT jfloat JNICALL
Java_com_red_sovereign_calls_RnNoiseProcessor_nativeProcess(JNIEnv *env, jobject thiz,
                                                           jlong state, jfloatArray input, jfloatArray output) {
    DenoiseState *st = (DenoiseState *) state;
    if (!st) return 1.0f;

    jfloat *in = env->GetFloatArrayElements(input, NULL);
    jfloat *out = env->GetFloatArrayElements(output, NULL);
    if (!in || !out) {
        if (in) env->ReleaseFloatArrayElements(input, in, JNI_ABORT);
        if (out) env->ReleaseFloatArrayElements(output, out, JNI_ABORT);
        return 1.0f;
    }

    const int frame_size = 480; // 10ms at 48kHz
    float vad_prob = rnnoise_process_frame(st, out, in);

    env->ReleaseFloatArrayElements(input, in, JNI_ABORT);
    env->ReleaseFloatArrayElements(output, out, 0);

    return vad_prob;
}

JNIEXPORT void JNICALL
Java_com_red_sovereign_calls_RnNoiseProcessor_nativeDestroy(JNIEnv *env, jobject thiz, jlong state) {
    DenoiseState *st = (DenoiseState *) state;
    if (st) {
        rnnoise_destroy(st);
    }
}

}