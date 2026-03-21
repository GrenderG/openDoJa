package opendoja.audio;

import com.nttdocomo.ui.MediaManager;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

public final class SampledPcmPlayer implements AutoCloseable {
    private static final int CHUNK_BYTES = Integer.getInteger("opendoja.sampledChunkBytes", 4096);

    public interface Listener {
        void onLoop();

        void onComplete();

        void onFailure(Exception exception);
    }

    private final Object stateLock = new Object();
    private final Listener listener;
    private Thread worker;
    private SourceDataLine line;
    private AudioFormat lineFormat;
    private MediaManager.PreparedSound sound;
    private int loopCount;
    private int bytePosition;
    private boolean active;
    private boolean paused;
    private boolean closed;
    private boolean pendingReset;

    public SampledPcmPlayer(Listener listener) {
        this.listener = listener;
    }

    public void start(MediaManager.PreparedSound sound, int loopCount) throws Exception {
        synchronized (stateLock) {
            ensureWorker();
            ensureLine(sound.sampledFormat());
            this.sound = sound;
            this.loopCount = loopCount;
            this.bytePosition = 0;
            this.active = true;
            this.paused = false;
            this.pendingReset = true;
            stateLock.notifyAll();
        }
    }

    public void pause() {
        synchronized (stateLock) {
            if (line != null && active) {
                paused = true;
                line.stop();
            }
        }
    }

    public void restart() {
        synchronized (stateLock) {
            if (line != null && active) {
                paused = false;
                line.start();
                stateLock.notifyAll();
            }
        }
    }

    public int getCurrentTimeMillis() {
        synchronized (stateLock) {
            if (sound == null || sound.sampledFormat() == null) {
                return 0;
            }
            int frameSize = Math.max(1, sound.sampledFormat().getFrameSize());
            float frameRate = Math.max(1.0f, sound.sampledFormat().getFrameRate());
            int playedFrames = bytePosition / frameSize;
            return (int) Math.round((playedFrames * 1000.0) / frameRate);
        }
    }

    public int getTotalTimeMillis() {
        synchronized (stateLock) {
            if (sound == null || sound.sampledFormat() == null) {
                return 0;
            }
            int frameSize = Math.max(1, sound.sampledFormat().getFrameSize());
            float frameRate = Math.max(1.0f, sound.sampledFormat().getFrameRate());
            int totalFrames = sound.bytes().length / frameSize;
            int repeats = loopCount <= 0 ? 1 : loopCount;
            return (int) Math.round((totalFrames * repeats * 1000.0) / frameRate);
        }
    }

    public void stop() {
        synchronized (stateLock) {
            active = false;
            paused = false;
            bytePosition = 0;
            pendingReset = true;
            stateLock.notifyAll();
        }
    }

    @Override
    public void close() {
        Thread joinThread;
        synchronized (stateLock) {
            closed = true;
            active = false;
            paused = false;
            joinThread = worker;
            worker = null;
            stateLock.notifyAll();
        }
        if (joinThread != null && joinThread != Thread.currentThread()) {
            try {
                joinThread.join(1000L);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        synchronized (stateLock) {
            if (line != null) {
                line.stop();
                line.flush();
                line.close();
                line = null;
                lineFormat = null;
            }
        }
    }

    private void ensureWorker() {
        if (worker != null) {
            return;
        }
        worker = new Thread(this::runLoop, "opendoja-sampled-pcm");
        worker.setDaemon(true);
        worker.start();
    }

    private void ensureLine(AudioFormat format) throws Exception {
        if (line != null && lineFormat != null && lineFormat.matches(format)) {
            return;
        }
        if (line != null) {
            line.stop();
            line.flush();
            line.close();
        }
        line = AudioSystem.getSourceDataLine(format);
        line.open(format, Math.max(CHUNK_BYTES * 4, format.getFrameSize() * 1024));
        lineFormat = format;
    }

    private void runLoop() {
        try {
            while (true) {
                MediaManager.PreparedSound currentSound;
                int localLoopCount;
                SourceDataLine currentLine;
                boolean needsReset = false;
                synchronized (stateLock) {
                    while ((!active || paused || sound == null) && !closed) {
                        stateLock.wait();
                    }
                    if (closed) {
                        break;
                    }
                    currentSound = sound;
                    localLoopCount = loopCount;
                    currentLine = line;
                    if (pendingReset && currentLine != null) {
                        pendingReset = false;
                        needsReset = true;
                    }
                }
                if (needsReset) {
                    currentLine.stop();
                    currentLine.flush();
                    currentLine.start();
                }

                byte[] pcm = currentSound.bytes();
                while (true) {
                    int offset;
                    int length;
                    synchronized (stateLock) {
                        if (closed) {
                            return;
                        }
                        if (!active || paused || sound != currentSound) {
                            break;
                        }
                        if (bytePosition >= pcm.length) {
                            if (localLoopCount == 1) {
                                active = false;
                                bytePosition = 0;
                                if (line != null) {
                                    line.drain();
                                }
                                if (listener != null) {
                                    listener.onComplete();
                                }
                                break;
                            }
                            if (localLoopCount > 1) {
                                localLoopCount--;
                                loopCount = localLoopCount;
                            }
                            bytePosition = 0;
                            if (listener != null) {
                                listener.onLoop();
                            }
                        }
                        offset = bytePosition;
                        length = Math.min(CHUNK_BYTES, pcm.length - bytePosition);
                        bytePosition += length;
                    }
                    if (length > 0) {
                        line.write(pcm, offset, length);
                    }
                }
            }
        } catch (Exception exception) {
            if (listener != null) {
                listener.onFailure(exception);
            }
        }
    }
}
