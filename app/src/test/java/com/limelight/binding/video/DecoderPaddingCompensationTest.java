package com.limelight.binding.video;

import static org.junit.Assert.*;

import org.junit.Test;

public class DecoderPaddingCompensationTest {
    @Test
    public void nonAv1Unchanged() {
        DecoderPaddingCompensation comp = DecoderPaddingCompensation.forFormat(
                "video/hevc", 2448, 1848, 2496, 1850, 0, 0);
        assertFalse(comp.enabled);
        assertEquals(1f, comp.scaleX, 0f);
        assertEquals(1f, comp.scaleY, 0f);
    }

    @Test
    public void unpaddedAv1Unchanged() {
        DecoderPaddingCompensation comp = DecoderPaddingCompensation.forFormat(
                "video/av01", 2880, 1800, 2880, 1800, 0, 0);
        assertFalse(comp.enabled);
    }

    @Test
    public void fold8Av1Padded64pxCompensated() {
        DecoderPaddingCompensation comp = DecoderPaddingCompensation.forFormat(
                "video/av01", 2448, 1848, 2496, 1850, 0, 0);
        assertTrue(comp.enabled);
        assertEquals(2496f / 2448f, comp.scaleX, 1e-5f);
        assertEquals(1850f / 1848f, comp.scaleY, 1e-5f);
    }

    @Test
    public void unalignedOutputWidthIgnored() {
        DecoderPaddingCompensation comp = DecoderPaddingCompensation.forFormat(
                "video/av01", 2448, 1848, 2460, 1848, 0, 0);
        assertFalse(comp.enabled);
    }

    @Test
    public void genuineResolutionChangeIgnored() {
        DecoderPaddingCompensation comp = DecoderPaddingCompensation.forFormat(
                "video/av01", 1920, 1080, 2560, 1440, 0, 0);
        assertFalse(comp.enabled);
    }

    @Test
    public void nonZeroCropOriginIgnored() {
        DecoderPaddingCompensation comp = DecoderPaddingCompensation.forFormat(
                "video/av01", 2448, 1848, 2496, 1850, 8, 0);
        assertFalse(comp.enabled);
    }
}
