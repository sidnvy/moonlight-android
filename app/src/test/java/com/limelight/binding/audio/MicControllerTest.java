package com.limelight.binding.audio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MicControllerTest {

    private static final class SentFrame {
        final int sequence;
        final long timestampSamples;

        SentFrame(int sequence, long timestampSamples) {
            this.sequence = sequence;
            this.timestampSamples = timestampSamples;
        }
    }

    private static final class FakeCaptureSource implements MicController.CaptureSource {
        final AtomicInteger startCount = new AtomicInteger();
        final AtomicInteger stopCount = new AtomicInteger();
        final AtomicInteger releaseCount = new AtomicInteger();
        volatile boolean active;
        volatile int failAfterFrames = Integer.MAX_VALUE;
        int framesProduced = 0;

        @Override
        public boolean start() {
            startCount.incrementAndGet();
            active = true;
            return true;
        }

        @Override
        public int read(short[] pcm, int offset, int count) {
            if (!active) {
                return 0;
            }
            if (framesProduced >= failAfterFrames) {
                return -1;
            }
            for (int i = 0; i < count; i++) {
                pcm[offset + i] = (short) ((framesProduced + i) & 0xFF);
            }
            framesProduced++;
            return count;
        }

        @Override
        public void stop() {
            stopCount.incrementAndGet();
            active = false;
        }

        @Override
        public void release() {
            releaseCount.incrementAndGet();
        }
    }

    private static final class FakeTransport implements MicController.EncoderTransport {
        boolean hostSupported = true;
        final AtomicInteger createCount = new AtomicInteger();
        final AtomicInteger destroyCount = new AtomicInteger();
        final List<SentFrame> sentFrames = new ArrayList<>();
        volatile int failAfterSends = Integer.MAX_VALUE;
        CountDownLatch targetSendLatch;

        @Override
        public boolean hostSupportsMicrophone() {
            return hostSupported;
        }

        @Override
        public long createEncoder() {
            return createCount.incrementAndGet();
        }

        @Override
        public synchronized int encodeAndSend(long encoderPtr, short[] pcm, int sequence, long timestampSamples) {
            if (sentFrames.size() >= failAfterSends) {
                return -1;
            }
            sentFrames.add(new SentFrame(sequence, timestampSamples));
            if (targetSendLatch != null) {
                targetSendLatch.countDown();
            }
            return 0;
        }

        @Override
        public void destroyEncoder(long encoderPtr) {
            destroyCount.incrementAndGet();
        }
    }

    @Test
    public void timestampSamplesRemainMonotonicAcross16BitSequenceWraparound() throws Exception {
        final int totalFrames = 65540; // Crosses 65535 -> 0
        FakeCaptureSource capture = new FakeCaptureSource();
        FakeTransport transport = new FakeTransport();
        CountDownLatch latch = new CountDownLatch(totalFrames);
        transport.targetSendLatch = latch;

        MicController controller = new MicController(() -> capture, transport, null);
        assertTrue(controller.start());
        assertTrue("Timed out waiting for 65540 frames", latch.await(10, TimeUnit.SECONDS));
        controller.stop();

        assertFalse(controller.isActive());
        synchronized (transport) {
            assertTrue(transport.sentFrames.size() >= totalFrames);
            SentFrame atWrapBefore = transport.sentFrames.get(65535);
            SentFrame atWrapAfter = transport.sentFrames.get(65536);
            assertEquals(65535, atWrapBefore.sequence);
            assertEquals(0, atWrapAfter.sequence);
            assertEquals(65535L * MicController.FRAME_SAMPLES, atWrapBefore.timestampSamples);
            assertEquals(65536L * MicController.FRAME_SAMPLES, atWrapAfter.timestampSamples);
        }
        assertEquals(1, capture.releaseCount.get());
        assertEquals(1, transport.destroyCount.get());
    }

    @Test
    public void captureReadErrorStopsCleanlyAndNotifiesListener() throws Exception {
        FakeCaptureSource capture = new FakeCaptureSource();
        capture.failAfterFrames = 3;
        FakeTransport transport = new FakeTransport();

        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicBoolean reportedActive = new AtomicBoolean(true);
        final String[] reportedReason = new String[1];

        MicController controller = new MicController(() -> capture, transport, (active, reason) -> {
            reportedActive.set(active);
            reportedReason[0] = reason;
            errorLatch.countDown();
        });

        assertTrue(controller.start());
        assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
        controller.stop();

        assertFalse(controller.isActive());
        assertFalse(reportedActive.get());
        assertEquals("capture_error", reportedReason[0]);
        assertEquals(1, capture.stopCount.get());
        assertEquals(1, capture.releaseCount.get());
        assertEquals(1, transport.destroyCount.get());
    }

    @Test
    public void transportSendErrorStopsCleanlyAndNotifiesListener() throws Exception {
        FakeCaptureSource capture = new FakeCaptureSource();
        FakeTransport transport = new FakeTransport();
        transport.failAfterSends = 2;

        CountDownLatch errorLatch = new CountDownLatch(1);
        AtomicBoolean reportedActive = new AtomicBoolean(true);
        final String[] reportedReason = new String[1];

        MicController controller = new MicController(() -> capture, transport, (active, reason) -> {
            reportedActive.set(active);
            reportedReason[0] = reason;
            errorLatch.countDown();
        });

        assertTrue(controller.start());
        assertTrue(errorLatch.await(5, TimeUnit.SECONDS));
        controller.stop();

        assertFalse(controller.isActive());
        assertFalse(reportedActive.get());
        assertEquals("transport_error", reportedReason[0]);
        assertEquals(1, capture.stopCount.get());
        assertEquals(1, capture.releaseCount.get());
        assertEquals(1, transport.destroyCount.get());
    }

    @Test
    public void repeatedStartStopCyclesNeverLeakOrDoubleDestroy() throws Exception {
        List<FakeCaptureSource> sources = new ArrayList<>();
        FakeTransport transport = new FakeTransport();

        MicController controller = new MicController(() -> {
            FakeCaptureSource cs = new FakeCaptureSource();
            sources.add(cs);
            return cs;
        }, transport, null);

        for (int i = 0; i < 5; i++) {
            CountDownLatch latch = new CountDownLatch(2);
            transport.targetSendLatch = latch;
            assertTrue(controller.start());
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            controller.stop();
            controller.stop(); // idempotent second stop
            assertFalse(controller.isActive());
        }

        assertEquals(5, sources.size());
        for (FakeCaptureSource cs : sources) {
            assertEquals(1, cs.startCount.get());
            assertEquals(1, cs.stopCount.get());
            assertEquals(1, cs.releaseCount.get());
        }
        assertEquals(5, transport.createCount.get());
        assertEquals(5, transport.destroyCount.get());
    }
}
