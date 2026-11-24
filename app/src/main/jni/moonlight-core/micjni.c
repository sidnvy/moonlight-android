#include <jni.h>
#include <stdint.h>
#include <stdlib.h>

#include <Limelight.h>
#include <opus.h>

JNIEXPORT jboolean JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_hostSupportsMicrophone(JNIEnv *env, jclass clazz)
{
    (void)env;
    (void)clazz;
    return (LiGetHostFeatureFlags() & LI_FF_MIC_INPUT) != 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jlong JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_createMicEncoder(JNIEnv *env, jclass clazz)
{
    (void)env;
    (void)clazz;

    int err = OPUS_OK;
    OpusEncoder *enc = opus_encoder_create(LI_MIC_SAMPLE_RATE, LI_MIC_CHANNELS, OPUS_APPLICATION_VOIP, &err);
    if (err != OPUS_OK || enc == NULL) {
        if (enc != NULL) {
            opus_encoder_destroy(enc);
        }
        return 0;
    }

    opus_encoder_ctl(enc, OPUS_SET_BITRATE(48000));
    opus_encoder_ctl(enc, OPUS_SET_INBAND_FEC(1));
    opus_encoder_ctl(enc, OPUS_SET_PACKET_LOSS_PERC(10));
    opus_encoder_ctl(enc, OPUS_SET_DTX(1));

    return (jlong)(uintptr_t)enc;
}

JNIEXPORT jint JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_encodeAndSendMic(JNIEnv *env,
                                                            jclass clazz,
                                                            jlong encoderPtr,
                                                            jshortArray pcmArray,
                                                            jint sequence,
                                                            jlong timestampSamples)
{
    (void)clazz;

    if (encoderPtr == 0 || pcmArray == NULL) {
        return -1;
    }

    jsize len = (*env)->GetArrayLength(env, pcmArray);
    if (len < LI_MIC_FRAME_SAMPLES) {
        return -1;
    }

    opus_int16 pcm[LI_MIC_FRAME_SAMPLES];
    (*env)->GetShortArrayRegion(env, pcmArray, 0, LI_MIC_FRAME_SAMPLES, pcm);
    if ((*env)->ExceptionCheck(env)) {
        return -1;
    }

    OpusEncoder *enc = (OpusEncoder *)(uintptr_t)encoderPtr;
    unsigned char opus[LI_MIC_MAX_OPUS_BYTES];
    int encoded = opus_encode(enc, pcm, LI_MIC_FRAME_SAMPLES, opus, (opus_int32)sizeof(opus));
    if (encoded < 0) {
        return -1;
    }

    // Skip Opus DTX comfort-noise/silence packets while caller advances monotonic seq & timestamp
    if (encoded <= 2) {
        return 0;
    }

    return (jint)LiSendMicAudioFrame(opus,
                                     encoded,
                                     (uint16_t)(sequence & 0xFFFF),
                                     (uint32_t)(timestampSamples & 0xFFFFFFFFu));
}

JNIEXPORT void JNICALL
Java_com_limelight_nvstream_jni_MoonBridge_destroyMicEncoder(JNIEnv *env, jclass clazz, jlong encoderPtr)
{
    (void)env;
    (void)clazz;

    OpusEncoder *enc = (OpusEncoder *)(uintptr_t)encoderPtr;
    if (enc != NULL) {
        opus_encoder_destroy(enc);
    }
}
