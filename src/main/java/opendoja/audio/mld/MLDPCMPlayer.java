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
    private static final float DEFAULT_SAMPLE_RATE =
            opendoja.host.OpenDoJaLaunchArgs.getFloat(opendoja.host.OpenDoJaLaunchArgs.MLD_SAMPLE_RATE);
    private static final int BUFFER_FRAMES = normalizeBufferFrames(
            opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.MLD_BUFFER_FRAMES),
            SYNTH_PROFILE.defaultBufferFrames);
    private static final int LINE_BUFFER_FRAMES =
            opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.MLD_LINE_BUFFER_FRAMES, BUFFER_FRAMES * 4);
    private static final AudioFormat OUTPUT_FORMAT = new AudioFormat(
            DEFAULT_SAMPLE_RATE, 16, 2, true, false);
    private static final SamplerProvider SAMPLER_PROVIDER = SYNTH_PROFILE.samplerProvider;
    private static final SharedEngine ENGINE = new SharedEngine();

    public interface Listener {
        void onLoop();

        void onSync(int timeMillis);

        void onComplete();

        void onFailure(Exception exception);
    }

    private final PlaybackHandle handle;

    public MLDPCMPlayer(Listener listener) {
        this.handle = ENGINE.open(listener);
    }

    public void start(MediaManager.PreparedSound sound, int loopCount, int startPositionMillis) {
        handle.start(sound, loopCount, startPositionMillis);
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

    public void setSyncEvent(int channel, int key) {
        handle.setSyncEvent(channel, key);
    }

    public void clearSyncEvent() {
        handle.clearSyncEvent();
    }

    public void stop() {
        handle.stop();
    }

    @Override
    public void close() {
        handle.close();
    }

    private static SynthProfile resolveSynthProfile() {
        return switch (MLDSynth.resolveConfigured()) {
            case FUETREK -> new SynthProfile(
                    MLDSynth.FUETREK,
                    FUETREK_SAMPLER_PROVIDER,
                    FueTrekSamplerProvider.SAMPLE_RATE,
                    1024);
            case MA3 -> new SynthProfile(
                    MLDSynth.MA3,
                    MA3_SAMPLER_PROVIDER,
                    MA3SamplerProvider.SAMPLE_RATE,
                    1024);
        };
    }

    private static int normalizeBufferFrames(int candidate, int fallback) {
        int frames = candidate <= 0 ? fallback : candidate;
        if (SYNTH_PROFILE.synth != MLDSynth.FUETREK) {
            return frames;
        }
        int clamped = Math.max(FueTrekSamplerProvider.MIN_FRAME_SIZE,
                Math.min(FueTrekSamplerProvider.MAX_FRAME_SIZE, frames));
        int mask = FueTrekSamplerProvider.FRAME_GRANULARITY - 1;
        return (clamped + mask) & ~mask;
    }

    private static int totalTimeFor(MLD mld) {
        double baseSeconds = mld.getDuration(true);
        if (!Double.isFinite(baseSeconds)) {
            return 0;
        }
        return (int) Math.round(baseSeconds * 1000.0);
    }

    private static final class SharedEngine {
        private final Object engineLock = new Object();
        private final List<PlaybackHandle> handles = new ArrayList<>();
        private final float[] mixBuffer = new float[BUFFER_FRAMES * 2];
        private final float[] sessionBuffer = new float[BUFFER_FRAMES * 2];
        private final byte[] pcmBuffer = new byte[BUFFER_FRAMES * 4];

        private Thread worker;
        private SourceDataLine line;
        private long writtenFrames;
        private boolean silentClock;

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
                long writtenBefore = writtenFrames;
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
                        if (silentClock) {
                            paceSilently(mixedFrames);
                        } else {
                            ensureLine();
                            int length = encodePcm(mixedFrames);
                            line.write(pcmBuffer, 0, length);
                        }
                        writtenFrames += mixedFrames;
                    } catch (IllegalArgumentException |
                             javax.sound.sampled.LineUnavailableException exception) {
                        synchronized (engineLock) {
                            closeLineLocked();
                            // Some hosts cannot open the requested line format.
                            // Keep advancing the shared clock so timed MLD events
                            // still fire instead of aborting playback outright.
                            silentClock = true;
                        }
                        paceSilently(mixedFrames);
                        writtenFrames += mixedFrames;
                    } catch (Exception exception) {
                        synchronized (engineLock) {
                            closeLineLocked();
                        }
                        for (PlaybackHandle handle : snapshot) {
                            handle.fail(exception, notifications);
                        }
                    }
                }

                long playedFrames;
                synchronized (engineLock) {
                    playedFrames = (line == null ? writtenFrames :
                            line.getLongFramePosition());
                }
                // Complete MLD playback only after the shared line has
                // consumed the final queued frames for that session.
                for (PlaybackHandle handle : snapshot) {
                    handle.bindCompletionTarget(writtenBefore, writtenFrames);
                    handle.dispatchReadyCompletion(playedFrames, notifications);
                }

                notifications.forEach(Runnable::run);
                long drainWaitMillis = mixedFrames == 0 ?
                    drainPollMillis(writtenFrames, playedFrames) : 0L;

                synchronized (engineLock) {
                    pruneClosedHandlesLocked();
                    if (handles.isEmpty()) {
                        closeLineLocked();
                    } else if (drainWaitMillis > 0L) {
                        try {
                            engineLock.wait(drainWaitMillis);
                        } catch (InterruptedException ignored) {
                            Thread.currentThread().interrupt();
                            return;
                        }
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

        private void paceSilently(int frames) {
            long millis = Math.max(1L, Math.round((frames * 1000.0d) / OUTPUT_FORMAT.getFrameRate()));
            try {
                Thread.sleep(millis);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }

        private static long drainPollMillis(long writtenFrames,
            long playedFrames) {
            long queuedFrames = Math.max(0L, writtenFrames - playedFrames);
            if (queuedFrames == 0L) {
                return 0L;
            }
            double queuedMillis = (queuedFrames * 1000.0d) /
                OUTPUT_FORMAT.getFrameRate();
            return Math.max(1L, Math.min(20L,
                (long)Math.ceil(queuedMillis / 2.0d)));
        }

        private void closeLineLocked() {
            if (line == null) {
                writtenFrames = 0L;
                return;
            }
            line.stop();
            line.flush();
            line.close();
            line = null;
            writtenFrames = 0L;
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
        private int pendingStartPositionMillis;
        private boolean pendingStop;
        private boolean paused;
        private boolean closed;
        private int volumeLevel = 100;
        private int syncEventChannel = -1;
        private int syncEventKey = -1;
        private boolean completionPending;
        private boolean completionNeedsCurrentWrite;
        private long completionTargetFrame = -1L;
        // Completion and event callbacks belong to one concrete playback
        // lifecycle. Bump the generation whenever that lifecycle changes.
        private long completionGeneration = -1L;
        private long generation;
        private volatile int currentTimeMillis;
        private volatile int totalTimeMillis;

        private PlaybackHandle(SharedEngine engine, Listener listener) {
            this.engine = engine;
            this.listener = listener;
        }

        private void clearCompletionState() {
            completionPending = false;
            completionNeedsCurrentWrite = false;
            completionTargetFrame = -1L;
            completionGeneration = -1L;
        }

        void start(MediaManager.PreparedSound sound, int loopCount, int startPositionMillis) {
            synchronized (stateLock) {
                generation++;
                pendingSound = sound;
                pendingLoopCount = loopCount;
                pendingStartPositionMillis = Math.max(0, startPositionMillis);
                pendingStop = false;
                paused = false;
                clearCompletionState();
                currentTimeMillis = 0;
                totalTimeMillis = totalTimeFor(sound.mld());
            }
            engine.wake();
        }

        void pause() {
            synchronized (stateLock) {
                generation++;
                paused = true;
            }
        }

        void restart() {
            synchronized (stateLock) {
                generation++;
                paused = false;
            }
            engine.wake();
        }

        int getCurrentTimeMillis() {
            int current = currentTimeMillis;
            if (current != 0) {
                return current;
            }
            synchronized (stateLock) {
                if (closed || pendingStop || paused) {
                    return 0;
                }
                if (pendingSound != null || activeSession != null) {
                    // Titles poll getCurrentTime() to distinguish "not playing"
                    // from "already started". Report a minimal non-zero time
                    // as soon as playback is pending or active, even before the
                    // worker has rendered the first audio chunk.
                    return 1;
                }
            }
            return 0;
        }

        int getTotalTimeMillis() {
            return totalTimeMillis;
        }

        void setVolumeLevel(int volumeLevel) {
            synchronized (stateLock) {
                this.volumeLevel = Math.max(0, Math.min(100, volumeLevel));
            }
        }

        void setSyncEvent(int channel, int key) {
            synchronized (stateLock) {
                syncEventChannel = channel;
                syncEventKey = key;
                if (activeSession != null) {
                    activeSession.configureSync(channel, key);
                }
            }
        }

        void clearSyncEvent() {
            synchronized (stateLock) {
                syncEventChannel = -1;
                syncEventKey = -1;
                if (activeSession != null) {
                    activeSession.configureSync(-1, -1);
                }
            }
        }

        void stop() {
            synchronized (stateLock) {
                generation++;
                pendingSound = null;
                pendingLoopCount = 0;
                pendingStop = true;
                paused = false;
                clearCompletionState();
                currentTimeMillis = 0;
                totalTimeMillis = 0;
            }
        }

        void close() {
            synchronized (stateLock) {
                generation++;
                closed = true;
                pendingSound = null;
                pendingLoopCount = 0;
                pendingStop = true;
                paused = false;
                activeSession = null;
                clearCompletionState();
                currentTimeMillis = 0;
                totalTimeMillis = 0;
            }
            engine.wake();
        }

        boolean hasWork() {
            synchronized (stateLock) {
                return !closed && (pendingSound != null || completionPending ||
                        (!pendingStop && activeSession != null && !paused));
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
            long callbackGeneration;
            synchronized (stateLock) {
                if (closed) {
                    return 0;
                }
                if (pendingStop) {
                    activeSession = null;
                    pendingStop = false;
                    clearCompletionState();
                }
                if (pendingSound != null) {
                    activeSession = sessions.computeIfAbsent(pendingSound, PlaybackSession::new);
                    activeSession.reset(pendingLoopCount, pendingStartPositionMillis,
                            syncEventChannel, syncEventKey);
                    pendingSound = null;
                    pendingStartPositionMillis = 0;
                }
                if (paused || activeSession == null) {
                    return 0;
                }
                session = activeSession;
                gain = volumeLevel / 100.0f;
                callbackGeneration = generation;
            }

            while (true) {
                int rendered = session.player.render(buffer, 0, frames, gain, gain, true, false);
                currentTimeMillis = (int) Math.round(session.player.getTime() * 1000.0);

                final boolean[] restarted = {false};
                final boolean[] finished = {rendered < 0};
                final int renderedFrames = Math.max(rendered, 0);

                session.player.drainEvents(event -> {
                    if (event.type == MLDPlayer.EVENT_END) {
                        if (session.remainingRepeats == Integer.MAX_VALUE) {
                            session.player.reset();
                            if (listener != null) {
                                queueNotification(notifications, callbackGeneration,
                                        listener::onLoop);
                            }
                            restarted[0] = true;
                        } else if (session.remainingRepeats > 0) {
                            session.remainingRepeats--;
                            session.player.reset();
                            if (listener != null) {
                                queueNotification(notifications, callbackGeneration,
                                        listener::onLoop);
                            }
                            restarted[0] = true;
                        } else {
                            finished[0] = true;
                        }
                    } else if (event.type == MLDPlayer.EVENT_KEY && listener != null) {
                        int timeMillis = (int) Math.round(event.time * 1000.0d);
                        queueNotification(notifications, callbackGeneration,
                                () -> listener.onSync(timeMillis));
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
                            armCompletion(renderedFrames > 0, callbackGeneration);
                        }
                    }
                    return renderedFrames;
                }
                return renderedFrames;
            }
        }

        private void armCompletion(boolean needsCurrentWrite, long expectedGeneration) {
            completionPending = true;
            completionNeedsCurrentWrite = needsCurrentWrite;
            completionTargetFrame = -1L;
            completionGeneration = expectedGeneration;
        }

        private void bindCompletionTarget(long writtenBefore, long writtenAfter) {
            synchronized (stateLock) {
                if (!completionPending || completionTargetFrame >= 0) {
                    return;
                }
                completionTargetFrame =
                        completionNeedsCurrentWrite ? writtenAfter : writtenBefore;
            }
        }

        private void dispatchReadyCompletion(long playedFrames,
            List<Runnable> notifications) {
            Listener notify = null;
            long notifyGeneration;
            synchronized (stateLock) {
                if (!completionPending || completionTargetFrame < 0 ||
                        playedFrames < completionTargetFrame) {
                    return;
                }
                notifyGeneration = completionGeneration;
                clearCompletionState();
                totalTimeMillis = 0;
                notify = listener;
            }
            if (notify != null) {
                queueNotification(notifications, notifyGeneration, notify::onComplete);
            }
        }

        void fail(Exception exception, List<Runnable> notifications) {
            long callbackGeneration;
            synchronized (stateLock) {
                callbackGeneration = generation;
                activeSession = null;
                pendingSound = null;
                pendingLoopCount = 0;
                pendingStop = false;
                clearCompletionState();
                currentTimeMillis = 0;
                totalTimeMillis = 0;
            }
            if (listener != null) {
                queueNotification(notifications, callbackGeneration,
                        () -> listener.onFailure(exception));
            }
        }

        private void queueNotification(List<Runnable> notifications,
                                       long expectedGeneration,
                                       Runnable action) {
            notifications.add(() -> {
                synchronized (stateLock) {
                    // Drop notifications that were queued before stop(),
                    // pause(), restart(), or a replacement play() advanced the
                    // handle state.
                    if (closed || generation != expectedGeneration) {
                        return;
                    }
                }
                action.run();
            });
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

        private void configureSync(int channel, int key) {
            player.clearEventNotes();
            if (channel >= 0 && key >= 0) {
                // MLDPlayer filters by the absolute note domain used by DoJa
                // sync registration rather than the raw A4-relative key.
                player.addEventNote(channel, key);
            }
        }

        private void reset(int loopCount, int startPositionMillis,
                           int syncEventChannel, int syncEventKey) {
            player.setPlaybackEventsEnabled(true);
            // The native Yamaha phrase engine loops in-place and lets note
            // releases run through the boundary; killing every voice here is
            // what made cuepoint loops sound clipped.
            player.setLoopStopAll(false);
            if (loopCount < 0) {
                remainingRepeats = Integer.MAX_VALUE;
            } else {
                remainingRepeats = Math.max(0, loopCount);
            }
            player.setLoopEnabled(cuepointLooping && loopCount != 0);
            configureSync(syncEventChannel, syncEventKey);
            player.reset();
            if (startPositionMillis > 0) {
                player.setTime(startPositionMillis / 1000.0);
            }
        }
    }

    private static final class SynthProfile {
        private final MLDSynth synth;
        private final String id;
        private final SamplerProvider samplerProvider;
        private final float defaultSampleRate;
        private final int defaultBufferFrames;

        private SynthProfile(MLDSynth synth,
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
