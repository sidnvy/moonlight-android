package com.limelight.binding.audio;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;

import com.limelight.nvstream.jni.MoonBridge;

/**
 * Captures 48 kHz mono 16-bit PCM frames and streams Opus frames over the encrypted ENet
 * control channel while guaranteeing decoupled transport/capture interfaces and race-free
 * worker ownership.
 */
public class MicController {

    public static final int SAMPLE_RATE = 48000;
    public static final int FRAME_SAMPLES = 960;

    public interface CaptureSource {
        boolean start();
        int read(short[] pcm, int offset, int count);
        void stop();
        void release();
    }

    public interface CaptureSourceFactory {
        CaptureSource create();
    }

    public interface EncoderTransport {
        boolean hostSupportsMicrophone();
        long createEncoder();
        int encodeAndSend(long encoderPtr, short[] pcm, int sequence, long timestampSamples);
        void destroyEncoder(long encoderPtr);
    }

    public interface Listener {
        void onMicStateChanged(boolean active, String reason);
    }

    public static final class MoonBridgeEncoderTransport implements EncoderTransport {
        @Override
        public boolean hostSupportsMicrophone() {
            return MoonBridge.hostSupportsMicrophone();
        }

        @Override
        public long createEncoder() {
            return MoonBridge.createMicEncoder();
        }

        @Override
        public int encodeAndSend(long encoderPtr, short[] pcm, int sequence, long timestampSamples) {
            return MoonBridge.encodeAndSendMic(encoderPtr, pcm, sequence, timestampSamples);
        }

        @Override
        public void destroyEncoder(long encoderPtr) {
            MoonBridge.destroyMicEncoder(encoderPtr);
        }
    }

    public static final class AudioRecordCaptureSource implements CaptureSource {
        private AudioRecord audioRecord;
        private volatile boolean stopped;

        @Override
        public boolean start() {
            int minBuf = AudioRecord.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minBuf <= 0) {
                return false;
            }
            int bufferBytes = Math.max(minBuf, FRAME_SAMPLES * 2 * 4);
            AudioRecord record;
            try {
                record = new AudioRecord(
                        MediaRecorder.AudioSource.VOICE_RECOGNITION,
                        SAMPLE_RATE,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        bufferBytes);
            } catch (IllegalArgumentException | SecurityException e) {
                return false;
            }
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                record.release();
                return false;
            }
            try {
                record.startRecording();
            } catch (IllegalStateException e) {
                record.release();
                return false;
            }
            if (record.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                record.release();
                return false;
            }
            stopped = false;
            audioRecord = record;
            return true;
        }

        @Override
        public int read(short[] pcm, int offset, int count) {
            AudioRecord record = audioRecord;
            if (record == null || stopped) {
                return -1;
            }
            int readSoFar = 0;
            while (readSoFar < count && !stopped) {
                int r = record.read(pcm, offset + readSoFar, count - readSoFar);
                if (r < 0) {
                    return r;
                }
                if (r == 0) {
                    if (stopped) {
                        return 0;
                    }
                    Thread.yield();
                    continue;
                }
                readSoFar += r;
            }
            return readSoFar;
        }

        @Override
        public void stop() {
            stopped = true;
            AudioRecord record = audioRecord;
            if (record != null) {
                try {
                    if (record.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) {
                        record.stop();
                    }
                } catch (IllegalStateException ignored) {
                }
            }
        }

        @Override
        public void release() {
            stopped = true;
            AudioRecord record = audioRecord;
            audioRecord = null;
            if (record != null) {
                try {
                    record.release();
                } catch (IllegalStateException ignored) {
                }
            }
        }
    }

    private final Object lock = new Object();
    private final CaptureSourceFactory captureFactory;
    private final EncoderTransport transport;
    private final Listener listener;

    private volatile boolean running;
    private long sessionGen;
    private Thread worker;
    private CaptureSource captureSource;
    private boolean captureStopped;

    public MicController(Listener listener) {
        this(AudioRecordCaptureSource::new, new MoonBridgeEncoderTransport(), listener);
    }

    public MicController(CaptureSourceFactory captureFactory,
                         EncoderTransport transport,
                         Listener listener) {
        this.captureFactory = captureFactory;
        this.transport = transport;
        this.listener = listener;
    }

    public boolean isActive() {
        return running;
    }

    public boolean start() {
        Thread pendingWorkerToJoin;
        synchronized (lock) {
            if (running) {
                return true;
            }
            pendingWorkerToJoin = worker;
        }
        if (pendingWorkerToJoin != null && pendingWorkerToJoin != Thread.currentThread()) {
            try {
                pendingWorkerToJoin.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        if (!transport.hostSupportsMicrophone()) {
            return false;
        }

        CaptureSource cs = captureFactory.create();
        if (cs == null) {
            return false;
        }
        if (!cs.start()) {
            cs.release();
            return false;
        }

        long enc = transport.createEncoder();
        if (enc == 0) {
            cs.stop();
            cs.release();
            return false;
        }

        synchronized (lock) {
            if (running) {
                // Another caller raced us to start; clean up our tentative resources.
                cs.stop();
                cs.release();
                transport.destroyEncoder(enc);
                return true;
            }
            sessionGen++;
            final long myGen = sessionGen;
            running = true;
            captureSource = cs;
            captureStopped = false;
            worker = new Thread(() -> runLoop(myGen, cs, enc), "StreamDesk-MicUplink");
            worker.start();
        }
        return true;
    }

    public void stop() {
        Thread toJoin = null;
        CaptureSource csToStop = null;
        synchronized (lock) {
            running = false;
            if (captureSource != null && !captureStopped) {
                captureStopped = true;
                csToStop = captureSource;
            }
            if (worker != null && worker != Thread.currentThread()) {
                toJoin = worker;
            }
        }
        if (csToStop != null) {
            csToStop.stop();
        }
        if (toJoin != null) {
            try {
                toJoin.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void runLoop(long myGen, CaptureSource cs, long enc) {
        String failureReason = null;
        short[] pcm = new short[FRAME_SAMPLES];
        int sequence = 0;
        long timestampSamples = 0L;

        try {
            while (running) {
                synchronized (lock) {
                    if (!running || sessionGen != myGen) {
                        break;
                    }
                }

                int totalRead = 0;
                boolean readFailed = false;
                while (totalRead < FRAME_SAMPLES && running) {
                    int n = cs.read(pcm, totalRead, FRAME_SAMPLES - totalRead);
                    if (n < 0) {
                        readFailed = true;
                        break;
                    }
                    if (n == 0) {
                        break;
                    }
                    totalRead += n;
                }

                if (readFailed) {
                    if (running) {
                        failureReason = "capture_error";
                    }
                    break;
                }

                if (!running || totalRead < FRAME_SAMPLES) {
                    break;
                }

                int sendResult = transport.encodeAndSend(enc, pcm, sequence & 0xFFFF, timestampSamples);
                sequence = (sequence + 1) & 0xFFFF;
                timestampSamples += FRAME_SAMPLES;

                if (sendResult < 0) {
                    if (running) {
                        failureReason = "transport_error";
                    }
                    break;
                }
            }
        } finally {
            boolean callStop = false;
            synchronized (lock) {
                if (sessionGen == myGen) {
                    running = false;
                    worker = null;
                    captureSource = null;
                    if (!captureStopped) {
                        captureStopped = true;
                        callStop = true;
                    }
                }
            }
            if (callStop) {
                cs.stop();
            }
            cs.release();
            transport.destroyEncoder(enc);
            if (failureReason != null && listener != null) {
                listener.onMicStateChanged(false, failureReason);
            }
        }
    }
}
