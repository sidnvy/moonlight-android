package com.limelight.binding.video;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class StreamGeometryHelperTest {

    @Test
    public void standardAndFoldableInnerResolutionsArePreservedVerbatim() {
        assertArrayEquals(new int[] { 1920, 1080 }, StreamGeometryHelper.alignResolution(1920, 1080, true));
        assertArrayEquals(new int[] { 2560, 1440 }, StreamGeometryHelper.alignResolution(2560, 1440, true));
        assertArrayEquals(new int[] { 3840, 2160 }, StreamGeometryHelper.alignResolution(3840, 2160, true));
        assertArrayEquals(new int[] { 2880, 1800 }, StreamGeometryHelper.alignResolution(2880, 1800, true));
        assertArrayEquals(new int[] { 2184, 1968 }, StreamGeometryHelper.alignResolution(2184, 1968, true));
        assertArrayEquals(new int[] { 1440, 3120 }, StreamGeometryHelper.alignResolution(1440, 3120, true));
    }

    @Test
    public void oddAndMod4CutoutDimensionsAlignDownCleanly() {
        // Fold cover panel mod-4 width aligns down to mod-8 for AV1 hardware encoders
        assertArrayEquals(new int[] { 2312, 904 }, StreamGeometryHelper.alignResolution(2316, 904, true));
        assertArrayEquals(new int[] { 2176, 1808 }, StreamGeometryHelper.alignResolution(2179, 1813, true));
        assertArrayEquals(new int[] { 320, 240 }, StreamGeometryHelper.alignResolution(100, 120, true));
    }

    @Test
    public void computeDesktopScaleAndAspectRatioCheck() {
        assertEquals(0.0f, StreamGeometryHelper.computeDesktopScale(0), 0.001f);
        assertEquals(0.0f, StreamGeometryHelper.computeDesktopScale(100), 0.001f);
        assertEquals(1.75f, StreamGeometryHelper.computeDesktopScale(175), 0.001f);
        assertEquals(0.5f, StreamGeometryHelper.computeDesktopScale(10), 0.001f);
        assertEquals(4.0f, StreamGeometryHelper.computeDesktopScale(900), 0.001f);

        assertTrue(StreamGeometryHelper.isFoldableSquareAspect(2184, 1968));
        assertFalse(StreamGeometryHelper.isFoldableSquareAspect(2316, 904));
        assertFalse(StreamGeometryHelper.isFoldableSquareAspect(2880, 1800));
    }

    @Test
    public void computeProductivityDesktopScaleFromViewportAndDensity() {
        assertEquals(1.80f, StreamGeometryHelper.computeDesktopScale(100, 2448, 1848, 2.8125f), 0.001f);
        assertEquals(2.00f, StreamGeometryHelper.computeDesktopScale(100, 1848, 2448, 2.8125f), 0.001f);
        assertEquals(1.00f, StreamGeometryHelper.computeDesktopScale(100, 1920, 1080, 1.0f), 0.001f);
        assertEquals(1.50f, StreamGeometryHelper.computeDesktopScale(150, 2448, 1848, 2.8125f), 0.001f);
        assertEquals(0.0f, StreamGeometryHelper.computeDesktopScale(100, 2448, 1848, 0.0f), 0.001f);
        assertEquals(0.0f, StreamGeometryHelper.computeDesktopScale(100, 2448, 1848, Float.NaN), 0.001f);
    }
}
