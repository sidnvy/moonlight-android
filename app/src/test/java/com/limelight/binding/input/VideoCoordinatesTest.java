package com.limelight.binding.input;

import static org.junit.Assert.*;

import org.junit.Test;

public class VideoCoordinatesTest {
    @Test
    public void unprojectsZoomAndPan() {
        float[] pt = new float[] { 250f, 150f };
        VideoCoordinates.unproject(pt, 50f, 30f, 2.0f);
        assertEquals(100f, pt[0], 1e-5f);
        assertEquals(60f, pt[1], 1e-5f);
    }

    @Test
    public void ignoresNonPositiveZoom() {
        float[] pt = new float[] { 120f, 80f };
        VideoCoordinates.unproject(pt, 10f, 10f, 0f);
        assertEquals(120f, pt[0], 0f);
        assertEquals(80f, pt[1], 0f);
    }
}
