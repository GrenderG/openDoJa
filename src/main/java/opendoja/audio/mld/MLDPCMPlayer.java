package opendoja.audio.mld;

import com.nttdocomo.ui.MediaManager;
import opendoja.audio.mld.fuetrek.FueTrekSamplerProvider;
import opendoja.audio.mld.ma3.MA3SamplerProvider;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public final class MLDPCMPlayer implements AutoCloseable {
    private static final MA3SamplerProvider MA3_SAMPLER_PROVIDER = new MA3SamplerProvider(
            MA3SamplerProvider.FM_MA3_4OP,
            MA3SamplerProvider.FM_MA3_4OP,
            MA3SamplerProvider.WAVE_DRUM_MA3);
    private static final FueTrekSamplerProvider FUETREK_SAMPLER_PROVIDER = new FueTrekSamplerProvider();
    private static final SynthProfile SYNTH_PROFILE = resolveSynthProfile();
    private static final float DEFAULT_SAMPLE_RATE = Float.parseFloat(
            System.getProperty("opendoja.mldSampleRate",
                    Float.toString(SYNTH_PROFILE.defaultSampleRate)));
    private static final int BUFFER_FRAMES = normalizeBufferFrames(
            Integer.getInteger("opendoja.mldBufferFrames", SYNTH_PROFILE.defaultBufferFrames),
            SYNTH_PROFILE.defaultBufferFrames);
    private static final int LINE_BUFFER_FRAMES = Integer.getInteger(
            "opendoja.mldLineBufferFrames", BUFFER_FRAMES * 4);
    private static final AudioFormat OUTPUT_FORMAT = new AudioFormat(
            DEFAULT_SAMPLE_RATE, 16, 2, true, false);
    private static final SamplerProvider SAMPLER_PROVIDER = SYNTH_PROFILE.samplerProvider;
    private static final SharedEngine ENGINE = new SharedEngine();

    public interface Listener {
        void onLoop();

        void onComplete();

        void onFailure(Exception exception);
    }

    private final PlaybackHandle handle;

    public MLDPCMPlayer(Listener listener) {
        this.handle = ENGINE.open(listener);
    }

    public void start(MediaManager.PreparedSound sound, int loopCount) {
        handle.start(sound, loopCount);
    }

    public void pause() {
        handle.pause();
    }

    public void restart() {
        handle.restart();
    }

    public int getCurrentTimeMillis() {
        return handle.getCurrentTimeMillis();
    }

    public int getTotalTimeMillis() {
        return handle.getTotalTimeMillis();
    }

    public void setVolumeLevel(int volumeLevel) {
        handle.setVolumeLevel(volumeLevel);
    }

    public void stop() {
        handle.stop();
    }

    @Override
    public void close() {
        handle.close();
    }

    private static SynthProfile resolveSynthProfile() {
        return switch (MldSynth.resolveConfigured()) {
            case FUETREK -> new SynthProfile(
                    MldSynth.FUETREK,
                    FUETREK_SAMPLER_PROVIDER,
                    FueTrekSamplerProvider.SAMPLE_RATE,
                    1024);
            case MA3 -> new SynthProfile(
                    MldSynth.MA3,
                    MA3_SAMPLER_PROVIDER,
                    MA3SamplerProvider.SAMPLE_RATE,
                    1024);
        };
    }

    private static int normalizeBufferFrames(int candidate, int fallback) {
        int frames = candidate <= 0 ? fallback : candidate;
        if (SYNTH_PROFILE.synth != MldSynth.FUETREK) {
            return frames;
        }
        int clamped = Math.max(FueTrekSamplerProvider.MIN_FRAME_SIZE,
                Math.min(FueTrekSamplerProvider.MAX_FRAME_SIZE, frames));
        int mask = FueTrekSamplerProvider.FRAME_GRANULARITY - 1;
        return (clamped + mask) & ~mask;
    }

    private static int totalTimeFor(MLD mld, int loopCount) {
        double baseSeconds = mld.getDuration(true);
        if (!Double.isFinite(baseSeconds)) {
            return 0;
        }
        if (loopCount <= 0) {
            return (int) Math.round(baseSeconds * 1000.0);
        }
        return (int) Math.round(baseSeconds * loopCount * 1000.0);
    }

    private static final class SharedEngine {
        private final Object engineLock = new Object();
        private final List<PlaybackHandle> handles = new ArrayList<>();
        private final float[] mixBuffer = new float[BUFFER_FRAMES * 2];
        private final float[] sessionBuffer = new float[BUFFER_FRAMES * 2];
        private final byte[] pcmBuffer = new byte[BUFFER_FRAMES * 4];

        private Thread worker;
        private SourceDataLine line;

        PlaybackHandle open(Listener listener) {
            PlaybackHandle handle = new PlaybackHandle(this, listener);
            synchronized (engineLock) {
                handles.add(handle);
                ensureWorker();
                engineLock.notifyAll();
            }
            return handle;
        }

        void wake() {
            synchronized (engineLock) {
                ensureWorker();
                engineLock.notifyAll();
            }
        }

        private void ensureWorker() {
            if (worker != null) {
                return;
            }
            worker = new Thread(this::runLoop, "opendoja-mld-" + SYNTH_PROFILE.id);
            worker.setDaemon(true);
            worker.start();
        }

        private void runLoop() {
            while (true) {
                List<PlaybackHandle> snapshot;
                synchronized (engineLock) {
                    while (handles.isEmpty() || !hasRunnableHandleLocked()) {
                        pruneClosedHandlesLocked();
                        if (handles.isEmpty()) {
                            closeLineLocked();
                        }
                        try {
                            engineLock.wait();
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                    snapshot = new ArrayList<>(handles);
                }

                List<Runnable> notifications = new ArrayList<>();
                int mixedFrames = 0;
                for (PlaybackHandle handle : snapshot) {
                    try {
                        int frames = handle.renderInto(sessionBuffer, BUFFER_FRAMES, notifications);
                        if (frames <= 0) {
                            continue;
                        }
                        mixedFrames = Math.max(mixedFrames, frames);
                        for (int i = 0; i < frames * 2; i++) {
                            mixBuffer[i] += sessionBuffer[i];
                        }
                    } catch (Exception exception) {
                        handle.fail(exception, notifications);
                    }
                }

                if (mixedFrames > 0) {
                    try {
                        ensureLine();
                        int length = encodePcm(mixedFrames);
                        line.write(pcmBuffer, 0, length);
                    } catch (Exception exception) {
                        synchronized (engineLock) {
                            closeLineLocked();
                        }
                        for (PlaybackHandle handle : snapshot) {
                            handle.fail(exception, notifications);
                        }
                    }
                }

                notifications.forEach(Runnable::run);

                synchronized (engineLock) {
                    pruneClosedHandlesLocked();
                    if (handles.isEmpty()) {
                        closeLineLocked();
                    }
                }
            }
        }

        private boolean hasRunnableHandleLocked() {
            for (PlaybackHandle handle : handles) {
                if (handle.hasWork()) {
                    return true;
                }
            }
            return false;
        }

        private void pruneClosedHandlesLocked() {
            for (int i = 0; i < handles.size(); i++) {
                if (handles.get(i).isClosed()) {
                    handles.remove(i--);
                }
            }
        }

        private void ensureLine() throws Exception {
            synchronized (engineLock) {
                if (line != null) {
                    return;
                }
                line = AudioSystem.getSourceDataLine(OUTPUT_FORMAT);
                line.open(OUTPUT_FORMAT, LINE_BUFFER_FRAMES * OUTPUT_FORMAT.getFrameSize());
                line.start();
            }
        }

        private int encodePcm(int frames) {
            int output = 0;
            for (int i = 0; i < frames * 2; i++) {
                float sample = Math.max(-1.0f, Math.min(1.0f, mixBuffer[i]));
                int value = Math.round(sample * 32767.0f);
                pcmBuffer[output++] = (byte) (value & 0xFF);
                pcmBuffer[output++] = (byte) ((value >>> 8) & 0xFF);
                mixBuffer[i] = 0.0f;
            }
            return output;
        }

        private void closeLineLocked() {
            if (line == null) {
                return;
            }
            line.stop();
            line.flush();
            line.close();
            line = null;
        }
    }

    private static final class PlaybackHandle {
        private final Object stateLock = new Object();
        private final SharedEngine engine;
        private final Listener listener;
        private final Map<MediaManager.PreparedSound, PlaybackSession> sessions = new IdentityHashMap<>();

        private PlaybackSession activeSession;
        private MediaManager.PreparedSound pendingSound;
        private int pendingLoopCount;
        private boolean pendingStop;
        private boolean paused;
        private boolean closed;
        private int volumeLevel = 100;
        private volatile int currentTimeMillis;
        private volatile int totalTimeMillis;

        private PlaybackHandle(SharedEngine engine, Listener listener) {
            this.engine = engine;
            this.listener = listener;
        }

        void start(MediaManager.PreparedSound sound, int loopCount) {
            synchronized (stateLock) {
                pendingSound = sound;
                pendingLoopCount = loopCount;
                pendingStop = false;
                paused = false;
                currentTimeMillis = 0;
                totalTimeMillis = totalTimeFor(sound.mld(), loopCount);
            }
            engine.wake();
        }

        void pause() {
            synchronized (stateLock) {
                paused = true;
            }
        }

        void restart() {
            synchronized (stateLock) {
                paused = false;
            }
            engine.wake();
        }

        int getCurrentTimeMillis() {
            return currentTimeMillis;
        }

        int getTotalTimeMillis() {
            return totalTimeMillis;
        }

        void setVolumeLevel(int volumeLevel) {
            synchronized (stateLock) {
                this.volumeLevel = Math.max(0, Math.min(100, volumeLevel));
            }
        }

        void stop() {
            synchronized (stateLock) {
                pendingSound = null;
                pendingLoopCount = 0;
                pendingStop = true;
                paused = false;
                currentTimeMillis = 0;
                totalTimeMillis = 0;
            }
        }

        void close() {
            synchronized (stateLock) {
                closed = true;
                pendingSound = null;
                pendingLoopCount = 0;
                pendingStop = true;
                paused = false;
                activeSession = null;
                currentTimeMillis = 0;
                totalTimeMillis = 0;
            }
            engine.wake();
        }

        boolean hasWork() {
            synchronized (stateLock) {
                return !closed && (pendingSound != null || (!pendingStop && activeSession != null && !paused));
            }
        }

        boolean isClosed() {
            synchronized (stateLock) {
                return closed;
            }
        }

        int renderInto(float[] buffer, int frames, List<Runnable> notifications) {
            PlaybackSession session;
            float gain;
            synchronized (stateLock) {
                if (closed) {
                    return 0;
                }
                if (pendingStop) {
                    activeSession = null;
                    pendingStop = false;
                }
                if (pendingSound != null) {
                    activeSession = sessions.computeIfAbsent(pendingSound, PlaybackSession::new);
                    activeSession.reset(pendingLoopCount);
                    pendingSound = null;
                }
                if (paused || activeSession == null) {
                    return 0;
                }
                session = activeSession;
                gain = volumeLevel / 100.0f;
            }

            while (true) {
                int rendered = session.player.render(buffer, 0, frames, gain, gain, true, false);
                currentTimeMillis = (int) Math.round(session.player.getTime() * 1000.0);

                final boolean[] restarted = {false};
                final boolean[] finished = {rendered < 0};
                final int renderedFrames = Math.max(rendered, 0);

                session.player.drainEvents(event -> {
                    if (event.type == MLDPlayer.EVENT_LOOP) {
                        if (listener != null) {
                            notifications.add(listener::onLoop);
                        }
                        if (session.remainingRepeats != Integer.MAX_VALUE && session.remainingRepeats > 0) {
                            session.remainingRepeats--;
                            if (session.remainingRepeats == 0) {
                                session.player.setLoopEnabled(false);
                            }
                        }
                    } else if (event.type == MLDPlayer.EVENT_END) {
                        if (session.remainingRepeats == Integer.MAX_VALUE) {
                            session.player.reset();
                            restarted[0] = true;
                        } else if (!session.cuepointLooping && session.remainingRepeats > 0) {
                            session.remainingRepeats--;
                            session.player.reset();
                            restarted[0] = true;
                        } else {
                            finished[0] = true;
                        }
                    }
                });

                if (restarted[0]) {
                    continue;
                }
                if (finished[0]) {
                    synchronized (stateLock) {
                        if (activeSession == session) {
                            activeSession = null;
                            currentTimeMillis = 0;
                        }
                    }
                    if (listener != null) {
                        notifications.add(listener::onComplete);
                    }
                    return renderedFrames;
                }
                return renderedFrames;
            }
        }

        void fail(Exception exception, List<Runnable> notifications) {
            synchronized (stateLock) {
                activeSession = null;
                pendingSound = null;
                pendingLoopCount = 0;
                pendingStop = false;
                currentTimeMillis = 0;
                totalTimeMillis = 0;
            }
            if (listener != null) {
                notifications.add(() -> listener.onFailure(exception));
            }
        }
    }

    private static final class PlaybackSession {
        private final MLDPlayer player;
        private final boolean cuepointLooping;
        private int remainingRepeats;

        private PlaybackSession(MediaManager.PreparedSound sound) {
            this.player = new MLDPlayer(sound.mld(), SAMPLER_PROVIDER, DEFAULT_SAMPLE_RATE);
            this.cuepointLooping = Double.isInfinite(sound.mld().getDuration(false));
        }

        private void reset(int loopCount) {
            player.setPlaybackEventsEnabled(true);
            // The native Yamaha phrase engine loops in-place and lets note
            // releases run through the boundary; killing every voice here is
            // what made cuepoint loops sound clipped.
            player.setLoopStopAll(false);
            if (loopCount <= 0) {
                remainingRepeats = Integer.MAX_VALUE;
                player.setLoopEnabled(cuepointLooping);
            } else {
                remainingRepeats = Math.max(0, loopCount - 1);
                player.setLoopEnabled(cuepointLooping && remainingRepeats > 0);
            }
            player.reset();
        }
    }

    private static final class SynthProfile {
        private final MldSynth synth;
        private final String id;
        private final SamplerProvider samplerProvider;
        private final float defaultSampleRate;
        private final int defaultBufferFrames;

        private SynthProfile(MldSynth synth,
                             SamplerProvider samplerProvider,
                             float defaultSampleRate,
                             int defaultBufferFrames) {
            this.synth = synth;
            this.id = synth.id;
            this.samplerProvider = samplerProvider;
            this.defaultSampleRate = defaultSampleRate;
            this.defaultBufferFrames = defaultBufferFrames;
        }
    }
}
