package com.limelight.binding.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ClipboardSyncControllerTest {

    private static final class RecordingLocalAdapter implements ClipboardSyncController.LocalClipboardAdapter {
        final List<String> texts = new ArrayList<>();
        final List<Boolean> sensitiveFlags = new ArrayList<>();

        @Override
        public void setLocalText(String text, boolean sensitive) {
            texts.add(text);
            sensitiveFlags.add(sensitive);
        }
    }

    private static final class RecordingTransport implements ClipboardSyncController.ClipboardTransport {
        boolean supported = true;
        int nextReturnCode = 0;
        final List<String> sentTexts = new ArrayList<>();
        final List<Integer> sentFlags = new ArrayList<>();

        @Override
        public boolean hostSupportsClipboardEvent() {
            return supported;
        }

        @Override
        public int sendClipboardText(byte[] utf8Bytes, int flags) {
            sentTexts.add(new String(utf8Bytes, StandardCharsets.UTF_8));
            sentFlags.add(flags);
            return nextReturnCode;
        }
    }

    @Test
    public void normalizedHashMatchesFnv1aAcrossCrLfAndLf() {
        long lfHash = ClipboardSyncController.computeNormalizedHash("line1\nline2\n你好");
        long crlfHash = ClipboardSyncController.computeNormalizedHash("line1\r\nline2\r\n你好");
        assertEquals(lfHash, crlfHash);

        // Exact FNV-1a 64-bit vector for "hello" = 0xa430d84680aabd0bL
        assertEquals(0xa430d84680aabd0bL, ClipboardSyncController.computeNormalizedHash("hello"));
    }

    @Test
    public void hostEchoAfterLocalPushIsSuppressed() {
        RecordingLocalAdapter local = new RecordingLocalAdapter();
        RecordingTransport transport = new RecordingTransport();
        ClipboardSyncController controller = new ClipboardSyncController(local, transport);

        String sample = "hello stream-desk";
        long hash = ClipboardSyncController.computeNormalizedHash(sample);

        assertTrue(controller.pushLocalClipboard(sample, false));
        assertEquals(1, transport.sentTexts.size());

        // Host echoes same content hash back -> must NOT re-apply to local clipboard
        assertFalse(controller.onHostClipboardReceived(sample, hash, 0));
        assertTrue(local.texts.isEmpty());

        // Redundant non-forced push is suppressed until changed or forced
        assertFalse(controller.pushLocalClipboard(sample, false));
        assertTrue(controller.pushLocalClipboard(sample, true));
        assertEquals(2, transport.sentTexts.size());
    }

    @Test
    public void localEchoAfterHostPushIsSuppressedAndSensitivePropagates() {
        RecordingLocalAdapter local = new RecordingLocalAdapter();
        RecordingTransport transport = new RecordingTransport();
        ClipboardSyncController controller = new ClipboardSyncController(local, transport);

        String secret = "secret-token-123";
        long hash = ClipboardSyncController.computeNormalizedHash(secret);

        assertTrue(controller.onHostClipboardReceived(secret, hash, ClipboardSyncController.FLAG_SENSITIVE));
        assertEquals(1, local.texts.size());
        assertEquals(secret, local.texts.get(0));
        assertTrue(local.sensitiveFlags.get(0));

        // Focus-gain push of the exact same text without force must NOT echo back to host
        assertFalse(controller.pushLocalClipboard(secret, false));
        assertTrue(transport.sentTexts.isEmpty());

        // Once host updates to a newer value, pushing an older local text is allowed again
        String newer = "newer-value";
        assertTrue(controller.onHostClipboardReceived(newer, ClipboardSyncController.computeNormalizedHash(newer), 0));
        assertTrue(controller.pushLocalClipboard(secret, false));
        assertEquals(1, transport.sentTexts.size());
        assertEquals(secret, transport.sentTexts.get(0));
    }

    @Test
    public void emptyOversizeAndUnsupportedAreRejectedCleanly() {
        RecordingLocalAdapter local = new RecordingLocalAdapter();
        RecordingTransport transport = new RecordingTransport();
        ClipboardSyncController controller = new ClipboardSyncController(local, transport);

        assertFalse(controller.pushLocalClipboard("", true));
        assertFalse(controller.pushLocalClipboard(null, true));
        assertFalse(controller.onHostClipboardReceived("", 1L, 0));

        StringBuilder oversize = new StringBuilder();
        for (int i = 0; i < ClipboardSyncController.MAX_TEXT_BYTES + 1; i++) {
            oversize.append('a');
        }
        String big = oversize.toString();
        assertFalse(controller.pushLocalClipboard(big, true));
        assertFalse(controller.onHostClipboardReceived(big, 2L, 0));

        transport.supported = false;
        assertFalse(controller.pushLocalClipboard("valid", true));
        assertTrue(transport.sentTexts.isEmpty());
        assertTrue(local.texts.isEmpty());
    }
}
