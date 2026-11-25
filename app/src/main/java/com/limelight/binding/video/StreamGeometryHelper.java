package com.limelight.binding.video;

/**
 * Pure geometry and display scaling helpers for foldable/cutout Android targets (e.g. Fold8)
 * and hardware AV1 / HEVC decoder alignment constraints.
 */
public final class StreamGeometryHelper {

    public static final int MIN_WIDTH = 320;
    public static final int MIN_HEIGHT = 240;

    private StreamGeometryHelper() {
    }

    /**
     * Aligns stream dimensions downward to multiples of 8 (or 2 when alignToEight is false)
     * while preserving standard widescreen, 16:10, and foldable panel resolutions cleanly.
     */
    public static int[] alignResolution(int width, int height, boolean alignToEight) {
        int align = alignToEight ? 8 : 2;
        int w = Math.max(MIN_WIDTH, width - (width % align));
        int h = Math.max(MIN_HEIGHT, height - (height % align));
        return new int[] { w, h };
    }

    /**
     * Maps legacy integer percentage scale factor (e.g. 150 -> 1.50) to Control Channel /launch
     * {@code desktopScale} in [0.5, 4.0], returning 0.0 for host default (100 or unset).
     */
    public static float computeDesktopScale(int resolutionScaleFactor) {
        if (resolutionScaleFactor <= 0 || resolutionScaleFactor == 100) {
            return 0.0f;
        }
        float scale = resolutionScaleFactor / 100.0f;
        if (scale < 0.5f) {
            return 0.5f;
        }
        if (scale > 4.0f) {
            return 4.0f;
        }
        return scale;
    }

    /**
     * Computes productivity desktop scale from the client display pixel dimensions and density
     * when {@code resolutionScaleFactor} is left at default (100 or unset). Caps scale so
     * desktop apps receive at least ~1360 logical px in landscape or ~800 logical px in portrait.
     */
    public static float computeDesktopScale(int resolutionScaleFactor, int pixelWidth, int pixelHeight, float density) {
        float explicitScale = computeDesktopScale(resolutionScaleFactor);
        if (explicitScale > 0.0f) {
            return explicitScale;
        }
        if (pixelWidth <= 0 || pixelHeight <= 0 || !Float.isFinite(density) || density <= 0.0f) {
            return 0.0f;
        }
        float fromDensity = Math.max(0.5f, Math.min(4.0f, density));
        float targetWidth = pixelWidth >= pixelHeight ? 1360.0f : 800.0f;
        float workspaceScale = Math.max(1.0f, Math.min(2.0f, pixelWidth / targetWidth));
        return Math.min(fromDensity, workspaceScale);
    }

    /**
     * Identifies near-square unfolded foldable displays (e.g. Galaxy Z Fold series inner panel).
     */
    public static boolean isFoldableSquareAspect(int width, int height) {
        if (width <= 0 || height <= 0) {
            return false;
        }
        float ratio = (float) width / (float) height;
        return ratio >= 0.80f && ratio <= 1.25f;
    }
}
