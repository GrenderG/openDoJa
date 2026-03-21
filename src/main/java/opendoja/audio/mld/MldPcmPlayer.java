package opendoja.audio.mld;

import com.nttdocomo.ui.MediaManager;
import opendoja.audio.mld.ma3.MA3SamplerProvider;
import opendoja.audio.mld.ma3.MLD;
import opendoja.audio.mld.ma3.MLDPlayer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.IdentityHashMap;
import java.util.Map;

public final class MldPcmPlayer implements AutoCloseable {
    private static final float DEFAULT_SAMPLE_RATE = Float.parseFloat(
            System.getProperty("opendoja.mldSampleRate",
                    Float.toString(MA3SamplerProvider.SAMPLE_RATE)));
    private static final int BUFFER_FRAMES = Integer.getInteger("opendoja.mldBufferFrames", 1024);
    private static final AudioFormat OUTPUT_FORMAT = new AudioFormat(
            DEFAULT_SAMPLE_RATE, 16, 2, true, false);
    private static final MA3SamplerProvider SAMPLER_PROVIDER = new MA3SamplerProvider(
            MA3SamplerProvider.FM_MA3_4OP,
            MA3SamplerProvider.FM_MA3_4OP,
            MA3SamplerProvider.WAVE_DRUM_MA3);

    public interface Listener {
        void onLoop();

        void onComplete();

        void onFailure(Exception exception);
    }

    private final Object stateLock = new Object();
    private final Listener listener;
    private final float[] sampleBuffer = new float[BUFFER_FRAMES * 2];
    private final byte[] pcmBuffer = new byte[BUFFER_FRAMES * 4];
    private final Map<MediaManager.PreparedSound, PlaybackSession> sessions = new IdentityHashMap<>();

    private Thread worker;
    private SourceDataLine line;
    private MediaManager.PreparedSound pendingSound;
    private int pendingLoopCount;
    private boolean paused;
    private boolean active;
    private boolean closed;
    private boolean pendingReset;
    private volatile int currentTimeMillis;
    private volatile int totalTimeMillis;

    public MldPcmPlayer(Listener listener) {
        this.listener = listener;
    }

    public void start(MediaManager.PreparedSound sound, int loopCount) {
        synchronized (stateLock) {
            ensureWorker();
            pendingSound = sound;
            pendingLoopCount = loopCount;
            paused = false;
            active = true;
            pendingReset = true;
            currentTimeMillis = 0;
            totalTimeMillis = totalTimeFor(sound.mld(), loopCount);
            stateLock.notifyAll();
        }
    }

    public void pause() {
        synchronized (stateLock) {
            paused = true;
            if (line != null) {
                line.stop();
            }
        }
    }

    public void restart() {
        synchronized (stateLock) {
            paused = false;
            if (line != null) {
                line.start();
            }
            stateLock.notifyAll();
        }
    }

    public int getCurrentTimeMillis() {
        return currentTimeMillis;
    }

    public int getTotalTimeMillis() {
        return totalTimeMillis;
    }

    public void stop() {
        synchronized (stateLock) {
            active = false;
            pendingSound = null;
            paused = false;
            pendingReset = true;
            currentTimeMillis = 0;
            stateLock.notifyAll();
        }
    }

    @Override
    public void close() {
        Thread joinThread;
        synchronized (stateLock) {
            closed = true;
            active = false;
            pendingSound = null;
            paused = false;
            currentTimeMillis = 0;
            totalTimeMillis = 0;
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
        closeLine();
    }

    private void ensureWorker() {
        if (worker != null) {
            return;
        }
        worker = new Thread(this::runLoop, "opendoja-mld");
        worker.setDaemon(true);
        worker.start();
    }

    private void ensureLine() throws Exception {
        if (line != null) {
            return;
        }
        line = AudioSystem.getSourceDataLine(OUTPUT_FORMAT);
        line.open(OUTPUT_FORMAT, pcmBuffer.length * 8);
        line.start();
    }

    private void runLoop() {
        try {
            while (true) {
                MediaManager.PreparedSound sound;
                int loopCount;
                boolean resetLine = false;
                synchronized (stateLock) {
                    while ((!pendingReset && (!active || pendingSound == null || paused)) && !closed) {
                        stateLock.wait();
                    }
                    if (closed) {
                        break;
                    }
                    if (pendingReset) {
                        pendingReset = false;
                        resetLine = true;
                    }
                    if (!active || pendingSound == null || paused) {
                        sound = null;
                        loopCount = 0;
                    } else {
                        sound = pendingSound;
                        loopCount = pendingLoopCount;
                        pendingSound = null;
                    }
                }

                if (resetLine) {
                    resetLineForWorker();
                }
                if (sound == null) {
                    continue;
                }

                ensureLine();
                renderSound(sound, loopCount);
            }
        } catch (Exception exception) {
            if (listener != null) {
                listener.onFailure(exception);
            }
        } finally {
            currentTimeMillis = 0;
            totalTimeMillis = 0;
            closeLine();
        }
    }

    private void renderSound(MediaManager.PreparedSound sound, int loopCount) throws Exception {
        PlaybackSession session = sessions.computeIfAbsent(sound, PlaybackSession::new);
        MLD mld = sound.mld();
        MLDPlayer player = session.player;
        player.setPlaybackEventsEnabled(true);
        player.setLoopStopAll(true);

        boolean cuepointLooping = Double.isInfinite(mld.getDuration(false));
        int remainingRepeats;
        if (loopCount <= 0) {
            remainingRepeats = Integer.MAX_VALUE;
            player.setLoopEnabled(cuepointLooping);
        } else {
            remainingRepeats = Math.max(0, loopCount - 1);
            player.setLoopEnabled(cuepointLooping && remainingRepeats > 0);
        }

        player.reset();
        currentTimeMillis = 0;

        while (true) {
            synchronized (stateLock) {
                while (paused && active && pendingSound == null && !closed) {
                    stateLock.wait();
                }
                if (closed || !active || pendingSound != null) {
                    return;
                }
            }

            int frames = player.render(sampleBuffer, 0, BUFFER_FRAMES, 1.0f, 1.0f, true, true);
            currentTimeMillis = (int) Math.round(player.getTime() * 1000.0);

            if (frames > 0) {
                int length = encodePcm(frames);
                line.write(pcmBuffer, 0, length);
            }

            final int[] remainingRepeatsRef = {remainingRepeats};
            final boolean[] restarted = {false};
            final boolean[] finished = {frames < 0};
            player.drainEvents(event -> {
                if (event.type == MLDPlayer.EVENT_LOOP) {
                    if (listener != null) {
                        listener.onLoop();
                    }
                    if (remainingRepeatsRef[0] != Integer.MAX_VALUE && remainingRepeatsRef[0] > 0) {
                        remainingRepeatsRef[0]--;
                        if (remainingRepeatsRef[0] == 0) {
                            player.setLoopEnabled(false);
                        }
                    }
                } else if (event.type == MLDPlayer.EVENT_END) {
                    if (remainingRepeatsRef[0] == Integer.MAX_VALUE) {
                        player.reset();
                        restarted[0] = true;
                    } else if (!cuepointLooping && remainingRepeatsRef[0] > 0) {
                        remainingRepeatsRef[0]--;
                        player.reset();
                        restarted[0] = true;
                    } else {
                        finished[0] = true;
                    }
                }
            });
            remainingRepeats = remainingRepeatsRef[0];

            if (restarted[0]) {
                continue;
            }
            if (finished[0]) {
                line.drain();
                currentTimeMillis = 0;
                synchronized (stateLock) {
                    if (pendingSound == null) {
                        active = false;
                    }
                }
                if (listener != null) {
                    listener.onComplete();
                }
                return;
            }
        }
    }

    private int encodePcm(int frames) {
        int output = 0;
        for (int i = 0; i < frames * 2; i++) {
            float sample = Math.max(-1.0f, Math.min(1.0f, sampleBuffer[i]));
            int value = Math.round(sample * 32767.0f);
            pcmBuffer[output++] = (byte) (value & 0xFF);
            pcmBuffer[output++] = (byte) ((value >>> 8) & 0xFF);
        }
        return output;
    }

    private int totalTimeFor(MLD mld, int loopCount) {
        double baseSeconds = mld.getDuration(true);
        if (!Double.isFinite(baseSeconds)) {
            return 0;
        }
        if (loopCount <= 0) {
            return (int) Math.round(baseSeconds * 1000.0);
        }
        return (int) Math.round(baseSeconds * loopCount * 1000.0);
    }

    private void closeLine() {
        synchronized (stateLock) {
            if (line != null) {
                line.stop();
                line.flush();
                line.close();
                line = null;
            }
        }
    }

    private void resetLineForWorker() {
        SourceDataLine currentLine = line;
        if (currentLine == null) {
            return;
        }
        // Games like Nose Hair retrigger short MLD effects from their frame loop. Keep all line
        // reset/control on the audio worker thread so stop/start never blocks gameplay.
        currentLine.stop();
        currentLine.flush();
        if (!closed && !paused) {
            currentLine.start();
        }
    }

    private static final class PlaybackSession {
        private final MLDPlayer player;

        private PlaybackSession(MediaManager.PreparedSound sound) {
            this.player = new MLDPlayer(sound.mld(), SAMPLER_PROVIDER, DEFAULT_SAMPLE_RATE);
        }
    }
}
