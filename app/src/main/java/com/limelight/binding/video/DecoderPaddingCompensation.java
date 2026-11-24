package com.limelight.binding.video;

/**
 * Corrects a Surface-rendered AV1 stream whose hardware encoder/decoder reports
 * aligned padded picture pixels as visible content (e.g. 2496x1850 for 2448x1848).
 *
 * MediaCodec renders the aligned picture into the SurfaceView, including the black
 * padded edge. Scaling only the SurfaceView from its top-left pivot and clipping at
 * the untransformed StreamContainer bounds keeps touch/mouse coordinates aligned 1:1
 * with the real remote desktop.
 */
public final class DecoderPaddingCompensation {
    public final float scaleX;
    public final float scaleY;
    public final boolean enabled;

    private DecoderPaddingCompensation(float x, float y, boolean on) {
        scaleX = x;
        scaleY = y;
        enabled = on;
    }

    public static DecoderPaddingCompensation none() {
        return new DecoderPaddingCompensation(1f, 1f, false);
    }

    public static DecoderPaddingCompensation forFormat(
            String inputMime,
            int streamWidth, int streamHeight,
            int outputWidth, int outputHeight,
            int cropLeft, int cropTop) {
        if (!"video/av01".equals(inputMime) ||
                streamWidth <= 0 || streamHeight <= 0 ||
                cropLeft != 0 || cropTop != 0) {
            return none();
        }

        int extraWidth = outputWidth - streamWidth;
        int extraHeight = outputHeight - streamHeight;
        if ((extraWidth <= 0 && extraHeight <= 0) ||
                extraWidth < 0 || extraWidth >= 64 ||
                extraHeight < 0 || extraHeight > 16) {
            return none();
        }
        if (extraWidth > 0 && (outputWidth % 64) != 0) {
            return none();
        }
        if (extraHeight > 0 && (outputHeight % 2) != 0) {
            return none();
        }

        return new DecoderPaddingCompensation(
                (float) outputWidth / streamWidth,
                (float) outputHeight / streamHeight,
                true);
    }
}
