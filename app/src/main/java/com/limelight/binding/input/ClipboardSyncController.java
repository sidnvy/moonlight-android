package com.limelight.binding.input;

import java.nio.charset.StandardCharsets;

/**
 * Event-driven bidirectional clipboard coordinator over Control Channel v1 with CRLF-normalized
 * 64-bit FNV-1a deduplication and echo-loop suppression.
 */
public class ClipboardSyncController {

    public static final int MAX_TEXT_BYTES = 65536;
    public static final int FLAG_SENSITIVE = 0x02;

    public interface LocalClipboardAdapter {
        void setLocalText(String text, boolean sensitive);
    }

    public interface ClipboardTransport {
        boolean hostSupportsClipboardEvent();
        int sendClipboardText(byte[] utf8Bytes, int flags);
    }

    private final Object lock = new Object();
    private final LocalClipboardAdapter localAdapter;
    private final ClipboardTransport transport;

    private boolean hasLastLocalHash;
    private boolean hasLastRemoteHash;
    private long lastLocalHash;
    private long lastRemoteHash;

    public ClipboardSyncController(LocalClipboardAdapter localAdapter, ClipboardTransport transport) {
        this.localAdapter = localAdapter;
        this.transport = transport;
    }

    /**
     * Computes 64-bit FNV-1a over UTF-8 bytes with CRLF normalized to LF, matching
     * {@code LiComputeClipboardHash} in moonlight-common-c.
     */
    public static long computeNormalizedHash(String text) {
        long hash = -3750763034362895579L; // 14695981039346656037ULL
        final long prime = 1099511628211L;

        if (text == null || text.isEmpty()) {
            return hash;
        }

        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        for (int i = 0; i < bytes.length; i++) {
            int b = bytes[i] & 0xFF;
            if (b == '\r' && (i + 1) < bytes.length && (bytes[i + 1] & 0xFF) == '\n') {
                continue;
            }
            hash ^= b;
            hash *= prime;
        }
        return hash;
    }

    public boolean pushLocalClipboard(String text, boolean force) {
        return pushLocalClipboard(text, force, false);
    }

    public boolean pushLocalClipboard(String text, boolean force, boolean sensitive) {
        if (text == null || text.isEmpty() || !transport.hostSupportsClipboardEvent()) {
            return false;
        }

        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        if (utf8.length == 0 || utf8.length > MAX_TEXT_BYTES) {
            return false;
        }

        long hash = computeNormalizedHash(text);
        synchronized (lock) {
            if (!force) {
                if ((hasLastRemoteHash && hash == lastRemoteHash) ||
                    (hasLastLocalHash && hash == lastLocalHash)) {
                    return false;
                }
            }
            hasLastLocalHash = true;
            lastLocalHash = hash;
        }

        int flags = sensitive ? FLAG_SENSITIVE : 0;
        return transport.sendClipboardText(utf8, flags) == 0;
    }

    public boolean onHostClipboardReceived(String text, long contentHash, int flags) {
        if (text == null || text.isEmpty()) {
            return false;
        }

        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        if (utf8.length == 0 || utf8.length > MAX_TEXT_BYTES) {
            return false;
        }

        synchronized (lock) {
            if ((hasLastLocalHash && contentHash == lastLocalHash) ||
                (hasLastRemoteHash && contentHash == lastRemoteHash)) {
                return false;
            }
            hasLastRemoteHash = true;
            lastRemoteHash = contentHash;
            hasLastLocalHash = false;
        }

        boolean sensitive = (flags & FLAG_SENSITIVE) != 0;
        localAdapter.setLocalText(text, sensitive);
        return true;
    }

    public void reset() {
        synchronized (lock) {
            hasLastLocalHash = false;
            hasLastRemoteHash = false;
            lastLocalHash = 0;
            lastRemoteHash = 0;
        }
    }
}
