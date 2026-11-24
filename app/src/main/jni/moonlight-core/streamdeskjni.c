#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#include <Limelight.h>

JNIEXPORT jboolean JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_hostSupportsCommitText(JNIEnv *env, jclass clazz)
{
    (void)env;
    (void)clazz;
    return (LiGetHostFeatureFlags() & LI_FF_COMMIT_TEXT) != 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendCommitText(JNIEnv *env, jclass clazz, jbyteArray utf8Bytes)
{
    (void)clazz;

    if (utf8Bytes == NULL) {
        return -1;
    }

    jsize len = (*env)->GetArrayLength(env, utf8Bytes);
    if (len <= 0 || len > LI_STREAM_DESK_MAX_TEXT_BYTES) {
        return -1;
    }

    jbyte *bytes = (*env)->GetByteArrayElements(env, utf8Bytes, NULL);
    if (bytes == NULL) {
        return -1;
    }

    int rc = LiSendCommitText((const char *)bytes, (uint32_t)len);
    (*env)->ReleaseByteArrayElements(env, utf8Bytes, bytes, JNI_ABORT);
    return (jint)rc;
}

JNIEXPORT jboolean JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_hostSupportsClipboardEvent(JNIEnv *env, jclass clazz)
{
    (void)env;
    (void)clazz;
    return (LiGetHostFeatureFlags() & LI_FF_CLIPBOARD_EVENT) != 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_sendClipboardText(JNIEnv *env, jclass clazz, jbyteArray utf8Bytes, jint flags)
{
    (void)clazz;

    if (utf8Bytes == NULL) {
        return -1;
    }

    jsize len = (*env)->GetArrayLength(env, utf8Bytes);
    if (len <= 0 || len > LI_STREAM_DESK_MAX_TEXT_BYTES) {
        return -1;
    }

    jbyte *bytes = (*env)->GetByteArrayElements(env, utf8Bytes, NULL);
    if (bytes == NULL) {
        return -1;
    }

    int rc = LiSendClipboardText((const char *)bytes, (uint32_t)len, (uint8_t)(flags & 0xFF));
    (*env)->ReleaseByteArrayElements(env, utf8Bytes, bytes, JNI_ABORT);
    return (jint)rc;
}

JNIEXPORT jlong JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_computeClipboardHash(JNIEnv *env, jclass clazz, jbyteArray utf8Bytes)
{
    (void)clazz;

    if (utf8Bytes == NULL) {
        return (jlong)LiComputeClipboardHash("", 0);
    }

    jsize len = (*env)->GetArrayLength(env, utf8Bytes);
    if (len <= 0) {
        return (jlong)LiComputeClipboardHash("", 0);
    }

    jbyte *bytes = (*env)->GetByteArrayElements(env, utf8Bytes, NULL);
    if (bytes == NULL) {
        return (jlong)LiComputeClipboardHash("", 0);
    }

    uint64_t hash = LiComputeClipboardHash((const char *)bytes, (uint32_t)len);
    (*env)->ReleaseByteArrayElements(env, utf8Bytes, bytes, JNI_ABORT);
    return (jlong)hash;
}
