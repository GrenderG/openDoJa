package com.nttdocomo.opt.device;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.util.Arrays;

/**
 * Minimal host shim for applications that expect the optional sound recorder.
 */
public class SoundRecorder {
    private static final int INPUT_LEVEL = 0;
    private static final int MIN_LEVEL = 1;
    private static final int MAX_LEVEL = 10;
    private static final int DEFAULT_LEVEL = 5;
    private static final int CHUNK_SIZE = 4096;
    private static final long CHUNK_DELAY_MS = 64L;
    private static final SoundRecorder INSTANCE = new SoundRecorder();

    private final Object lock = new Object();
    private int inputLevel = DEFAULT_LEVEL;
    private SilentInputStream activeStream;

    protected SoundRecorder() {
    }

    public static SoundRecorder getSoundRecorder() {
        return INSTANCE;
    }

    public void record() {
        synchronized (lock) {
            if (activeStream != null) {
                activeStream.finish(false);
            }
            activeStream = new SilentInputStream();
        }
    }

    public void stop() {
        SilentInputStream stream;
        synchronized (lock) {
            stream = activeStream;
            activeStream = null;
        }
        if (stream != null) {
            stream.finish(true);
        }
    }

    public InputStream getInputStream() {
        synchronized (lock) {
            if (activeStream == null) {
                activeStream = new SilentInputStream();
            }
            return activeStream;
        }
    }

    public void setAttribute(int attr, int value) {
        if (attr != INPUT_LEVEL) {
            return;
        }
        if (value < MIN_LEVEL || value > MAX_LEVEL) {
            throw new IllegalArgumentException("value");
        }
        synchronized (lock) {
            inputLevel = value;
        }
    }

    public int getAttribute(int attr) {
        if (attr != INPUT_LEVEL) {
            return -1;
        }
        synchronized (lock) {
            return inputLevel;
        }
    }

    private static final class SilentInputStream extends InputStream {
        private final Object lock = new Object();
        private boolean closed;
        private boolean interrupted;

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int count = read(one, 0, 1);
            return count < 0 ? -1 : one[0] & 0xFF;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            if (buffer == null) {
                throw new NullPointerException("buffer");
            }
            if (off < 0 || len < 0 || off + len > buffer.length) {
                throw new IndexOutOfBoundsException();
            }
            if (len == 0) {
                return 0;
            }

            synchronized (lock) {
                if (interrupted) {
                    throw interruptedIOException();
                }
                if (closed) {
                    return -1;
                }
                try {
                    lock.wait(CHUNK_DELAY_MS);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    throw interruptedIOException();
                }
                if (interrupted) {
                    throw interruptedIOException();
                }
                if (closed) {
                    return -1;
                }
            }

            int count = java.lang.Math.min(len, CHUNK_SIZE);
            Arrays.fill(buffer, off, off + count, (byte) 0);
            return count;
        }

        @Override
        public void close() {
            finish(false);
        }

        private void finish(boolean interrupted) {
            synchronized (lock) {
                this.interrupted = interrupted;
                this.closed = true;
                lock.notifyAll();
            }
        }

        private InterruptedIOException interruptedIOException() {
            return new InterruptedIOException("Sound recording was interrupted");
        }
    }
}
