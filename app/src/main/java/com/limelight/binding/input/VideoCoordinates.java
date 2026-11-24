package com.limelight.binding.input;

/** Inverse user zoom/pan in the video's unscaled reference plane. */
public final class VideoCoordinates {
    private VideoCoordinates() {
    }

    public static void unproject(float[] point, float panX, float panY, float zoom) {
        if (point == null || point.length < 2 || !Float.isFinite(zoom) || zoom <= 0f) {
            return;
        }
        point[0] = (point[0] - panX) / zoom;
        point[1] = (point[1] - panY) / zoom;
    }
}
