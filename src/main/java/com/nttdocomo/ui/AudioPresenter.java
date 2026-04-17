package com.nttdocomo.ui;

import opendoja.audio.MidiEventPlayer;
import opendoja.audio.SampledPCMPlayer;
import opendoja.audio.mld.MLDPCMPlayer;
import opendoja.host.DoJaProfile;
import opendoja.host.DoJaRuntime;
import opendoja.host.OpenDoJaLog;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Defines the presenter object used to play audio media data.
 */
public class AudioPresenter implements MediaPresenter, AutoCloseable {
    private static final boolean TRACE_AUDIO_FAILURES = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.TRACE_AUDIO_FAILURES);
    // Keep a fallback callback executor for presenter use outside a live
    // DoJa runtime. Runtime-backed delivery is handled separately below.
    private static final ExecutorService MEDIA_CALLBACKS = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "openDoJa-media-events");
        thread.setDaemon(true);
        return thread;
    });
    /**
     * Event type indicating that playback started.
     */
    public static final int AUDIO_PLAYING = 1;
    /**
     * Event type indicating that playback stopped.
     */
    public static final int AUDIO_STOPPED = 2;
    /**
     * Event type indicating that playback reached the end of the media.
     */
    public static final int AUDIO_COMPLETE = 3;
    /**
     * Event type indicating that an embedded synchronization event fired.
     */
    public static final int AUDIO_SYNC = 4;
    /**
     * Event type indicating that playback paused.
     */
    public static final int AUDIO_PAUSED = 5;
    /**
     * Event type indicating that playback restarted from a paused state.
     */
    public static final int AUDIO_RESTARTED = 6;
    /**
     * Event type indicating that playback looped.
     */
    public static final int AUDIO_LOOPED = 7;
    /**
     * Attribute key that specifies playback priority.
     */
    public static final int PRIORITY = 1;
    /**
     * Attribute key that enables or disables synchronization events.
     */
    public static final int SYNC_MODE = 2;
    /**
     * Attribute key that transposes playback pitch in semitone units.
     */
    public static final int TRANSPOSE_KEY = 3;
    /**
     * Attribute key that specifies relative playback volume in percent.
     */
    public static final int SET_VOLUME = 4;
    /**
     * Attribute key that specifies relative playback tempo in percent.
     */
    public static final int CHANGE_TEMPO = 5;
    /**
     * Attribute key that specifies how many times the whole medium loops.
     */
    public static final int LOOP_COUNT = 6;
    /**
     * Attribute value that disables synchronization events.
     */
    public static final int ATTR_SYNC_OFF = 0;
    /**
     * Attribute value that enables synchronization events.
     */
    public static final int ATTR_SYNC_ON = 1;
    /**
     * Lowest playback-priority value.
     */
    public static final int MIN_PRIORITY = 1;
    /**
     * Normal playback-priority value.
     */
    public static final int NORM_PRIORITY = 5;
    /**
     * Highest playback-priority value.
     */
    public static final int MAX_PRIORITY = 10;
    /**
     * Smallest option-defined attribute identifier.
     */
    public static final int MIN_OPTION_ATTR = 128;
    /**
     * Largest option-defined attribute identifier.
     */
    public static final int MAX_OPTION_ATTR = 255;
    /**
     * Smallest vendor-defined attribute identifier.
     */
    protected static final int MIN_VENDOR_ATTR = 64;
    /**
     * Largest vendor-defined attribute identifier.
     */
    protected static final int MAX_VENDOR_ATTR = 127;
    /**
     * Smallest vendor-defined audio-event identifier.
     */
    protected static final int MIN_VENDOR_AUDIO_EVENT = 64;
    /**
     * Largest vendor-defined audio-event identifier.
     */
    protected static final int MAX_VENDOR_AUDIO_EVENT = 127;
    private static final int SYNC_EVENT_PRECISION_MS = 100;

    private final Map<Integer, Integer> attributes = new HashMap<>();
    private final List<ScheduledFuture<?>> syncEventTasks = new ArrayList<>();
    private final Audio3D audio3D = new Audio3D(this);
    private final int explicitPort;
    private MediaResource resource;
    private MediaListener mediaListener;
    private SampledPCMPlayer sampledPlayer;
    private MidiEventPlayer midiEventPlayer;
    private MLDPCMPlayer mldPlayer;
    private Sequencer sequencer;
    private int pausedPosition;
    private MediaManager.PreparedSound.Kind activeSoundKind = MediaManager.PreparedSound.Kind.UNKNOWN;
    private int syncEventChannel = -1;
    private int syncEventKey = -1;
    private int lastMldSyncTimeMillis = Integer.MIN_VALUE;
    private volatile boolean playing;
    private long activePlaybackToken = Long.MIN_VALUE;
    // Async callbacks from superseded play/stop cycles must not leak into the
    // current presenter state.
    private long callbackGeneration;

    /**
     * Applications cannot create this class directly.
     */
    protected AudioPresenter() {
        this(-1);
    }

    protected AudioPresenter(int explicitPort) {
        this.explicitPort = explicitPort;
        registerWithRuntime();
        // DoJa titles often construct presenters during loading and expect the first MLD effect
        // play to be low-latency. Create the long-lived MLD backend up front so menu input does
        // not pay the handle/worker setup cost the first time a prepared effect is triggered.
        mldPlayer = new MLDPCMPlayer(new MldListener());
    }

    /**
     * Gets the 3D audio controller associated with this presenter.
     *
     * @return the 3D audio controller for this presenter
     */
    public Audio3D getAudio3D() {
        return audio3D;
    }

    /**
     * Gets a new audio presenter.
     *
     * @return the audio presenter
     */
    public static AudioPresenter getAudioPresenter() {
        return new AudioPresenter();
    }

    /**
     * Gets a new audio presenter for the specified output port.
     *
     * @param port the output port
     * @return the audio presenter
     */
    public static AudioPresenter getAudioPresenter(int port) {
        if (port < 0 || port >= configuredExplicitPortCount()) {
            throw new IllegalArgumentException("port");
        }
        return new AudioPresenter(port);
    }

    /**
     * Gets an audio-track presenter object.
     *
     * @return the audio-track presenter object
     */
    public static AudioTrackPresenter getAudioTrackPresenter() {
        return new AudioTrackPresenter();
    }

    /**
     * Sets the media sound played by this presenter.
     *
     * @param sound the media sound to play
     */
    public void setSound(MediaSound sound) {
        this.resource = sound;
    }

    /**
     * Sets the media data played by this presenter.
     *
     * @param data the media data to play
     */
    @Override
    public void setData(MediaData data) {
        this.resource = data;
    }

    /**
     * Gets the media resource currently associated with this presenter.
     *
     * @return the current media resource
     */
    @Override
    public MediaResource getMediaResource() {
        return resource;
    }

    /**
     * Starts playback from the beginning of the current media.
     */
    @Override
    public void play() {
        play(0);
    }

    /**
     * Starts playback from the specified position in milliseconds measured
     * from the beginning of the media.
     *
     * @param time the playback start position in milliseconds
     * @throws IllegalArgumentException if {@code time} is negative
     */
    public void play(int time) {
        if (time < 0) {
            throw new IllegalArgumentException("time");
        }
        long playbackToken = nextCallbackGeneration();
        registerWithRuntime();
        stopPlayback();
        if (!(resource instanceof MediaManager.BasicMediaSound sound)) {
            playing = false;
            activeSoundKind = MediaManager.PreparedSound.Kind.UNKNOWN;
            notifyListener(AUDIO_STOPPED, 0);
            return;
        }
        try {
            MediaManager.PreparedSound prepared = sound.prepared();
            int loopCount = configuredLoopCount();
            lastMldSyncTimeMillis = Integer.MIN_VALUE;
            setActivePlaybackToken(playbackToken);
            if (time >= singlePlaybackDurationMillis(prepared)) {
                playing = false;
                activeSoundKind = MediaManager.PreparedSound.Kind.UNKNOWN;
                clearActivePlaybackToken(playbackToken);
                notifyListenerAsync(AUDIO_PLAYING, 0);
                notifyListenerAsync(AUDIO_COMPLETE, 0);
                return;
            }
            activeSoundKind = prepared.kind();
            if (prepared.kind() == MediaManager.PreparedSound.Kind.MIDI) {
                if (useDirectMidiPlayback(prepared, loopCount)) {
                    if (midiEventPlayer == null) {
                        midiEventPlayer = new MidiEventPlayer(new MidiEventListener());
                    }
                    midiEventPlayer.start(prepared, time, playbackToken);
                } else {
                    sequencer = MidiSystem.getSequencer();
                    sequencer.open();
                    sequencer.addMetaEventListener(meta -> {
                        // 0x2F is the standard MIDI End-of-Track meta event.
                        if (meta.getType() == 0x2F) {
                            handlePlaybackComplete(playbackToken);
                        }
                    });
                    sequencer.setSequence(prepared.midiSequence());
                    if (loopCount < 0) {
                        sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
                    } else {
                        sequencer.setLoopCount(loopCount);
                    }
                    if (time > 0) {
                        sequencer.setMicrosecondPosition(Math.min((long) time * 1_000L, sequencer.getMicrosecondLength()));
                    }
                    sequencer.start();
                }
            } else if (prepared.kind() == MediaManager.PreparedSound.Kind.MLD) {
                if (mldPlayer == null) {
                    mldPlayer = new MLDPCMPlayer(new MldListener());
                }
                mldPlayer.setVolumeLevel(currentVolumeLevel());
                updateMldSyncConfiguration();
                mldPlayer.start(prepared, loopCount, time, playbackToken);
            } else {
                if (sampledPlayer == null) {
                    sampledPlayer = new SampledPCMPlayer(new SampledListener());
                }
                sampledPlayer.setVolumeLevel(currentVolumeLevel());
                sampledPlayer.start(prepared, loopCount, time, playbackToken);
            }
            playing = true;
            notifyListenerAsync(AUDIO_PLAYING, 0);
            scheduleSyncEvents(prepared, time);
        } catch (UIException e) {
            playing = false;
            activeSoundKind = MediaManager.PreparedSound.Kind.UNKNOWN;
            stopPlayback();
            throw e;
        } catch (Exception e) {
            playing = false;
            activeSoundKind = MediaManager.PreparedSound.Kind.UNKNOWN;
            clearActivePlaybackToken(playbackToken);
            if (TRACE_AUDIO_FAILURES) {
                OpenDoJaLog.error(AudioPresenter.class, "Audio playback failed", e);
            }
            notifyListener(AUDIO_STOPPED, 0);
        }
    }

    /**
     * Pauses playback.
     */
    public void pause() {
        nextCallbackGeneration();
        if (activeSoundKind == MediaManager.PreparedSound.Kind.SAMPLED && sampledPlayer != null) {
            sampledPlayer.pause();
            playing = false;
            cancelSyncEvents();
            notifyListener(AUDIO_PAUSED, 0);
        } else if (activeSoundKind == MediaManager.PreparedSound.Kind.MLD && mldPlayer != null) {
            mldPlayer.pause();
            playing = false;
            cancelSyncEvents();
            notifyListener(AUDIO_PAUSED, 0);
        } else if (activeSoundKind == MediaManager.PreparedSound.Kind.MIDI && sequencer != null) {
            pausedPosition = (int) sequencer.getTickPosition();
            sequencer.stop();
            playing = false;
            cancelSyncEvents();
            notifyListener(AUDIO_PAUSED, 0);
        }
    }

    /**
     * Restarts playback after a pause.
     */
    public void restart() {
        nextCallbackGeneration();
        if (activeSoundKind == MediaManager.PreparedSound.Kind.SAMPLED && sampledPlayer != null) {
            sampledPlayer.restart();
            playing = true;
            notifyListener(AUDIO_RESTARTED, 0);
        } else if (activeSoundKind == MediaManager.PreparedSound.Kind.MLD && mldPlayer != null) {
            mldPlayer.restart();
            playing = true;
            notifyListener(AUDIO_RESTARTED, 0);
        } else if (activeSoundKind == MediaManager.PreparedSound.Kind.MIDI && sequencer != null) {
            sequencer.setTickPosition(pausedPosition);
            sequencer.start();
            playing = true;
            scheduleSyncEventsForSequence(sequencer.getSequence(), getCurrentTime());
            notifyListener(AUDIO_RESTARTED, 0);
        }
    }

    /**
     * Gets the current playback position in milliseconds.
     *
     * @return the current playback time in milliseconds
     */
    public int getCurrentTime() {
        if (activeSoundKind == MediaManager.PreparedSound.Kind.SAMPLED && sampledPlayer != null) {
            return sampledPlayer.getCurrentTimeMillis();
        }
        if (activeSoundKind == MediaManager.PreparedSound.Kind.MLD && mldPlayer != null) {
            return mldPlayer.getCurrentTimeMillis();
        }
        if (activeSoundKind == MediaManager.PreparedSound.Kind.MIDI
                && midiEventPlayer != null && midiEventPlayer.isActive()) {
            return midiEventPlayer.getCurrentTimeMillis();
        }
        if (activeSoundKind == MediaManager.PreparedSound.Kind.MIDI && sequencer != null) {
            return (int) (sequencer.getMicrosecondPosition() / 1_000L);
        }
        return 0;
    }

    /**
     * Gets the total playback time in milliseconds.
     *
     * @return the total playback time in milliseconds
     */
    public int getTotalTime() {
        if (activeSoundKind == MediaManager.PreparedSound.Kind.SAMPLED && sampledPlayer != null) {
            return sampledPlayer.getTotalTimeMillis();
        }
        if (activeSoundKind == MediaManager.PreparedSound.Kind.MLD && mldPlayer != null) {
            return mldPlayer.getTotalTimeMillis();
        }
        if (activeSoundKind == MediaManager.PreparedSound.Kind.MIDI
                && midiEventPlayer != null && midiEventPlayer.isActive()) {
            return midiEventPlayer.getTotalTimeMillis();
        }
        if (activeSoundKind == MediaManager.PreparedSound.Kind.MIDI && sequencer != null) {
            return (int) (sequencer.getMicrosecondLength() / 1_000L);
        }
        return 0;
    }

    /**
     * Registers the channel and key used for synchronization events.
     *
     * @param channel the synchronization channel
     * @param key the synchronization key
     */
    public void setSyncEvent(int channel, int key) {
        if (playing) {
            return;
        }
        if (channel < 0 || channel > 15) {
            return;
        }
        if (key < 0 || key > 127) {
            return;
        }
        syncEventChannel = channel;
        syncEventKey = key;
        updateMldSyncConfiguration();
    }

    /**
     * Stops playback of the current media.
     */
    @Override
    public void stop() {
        if (requiresStrictStopState() && !hasUsableMediaResource()) {
            throw new UIException(UIException.ILLEGAL_STATE);
        }
        nextCallbackGeneration();
        stopPlayback();
        notifyListener(AUDIO_STOPPED, 0);
    }

    private void stopPlayback() {
        playing = false;
        activeSoundKind = MediaManager.PreparedSound.Kind.UNKNOWN;
        lastMldSyncTimeMillis = Integer.MIN_VALUE;
        cancelSyncEvents();
        clearActivePlaybackToken();
        if (sampledPlayer != null) {
            sampledPlayer.stop();
        }
        if (midiEventPlayer != null) {
            midiEventPlayer.stop();
        }
        if (sequencer != null) {
            sequencer.stop();
            sequencer.close();
            sequencer = null;
        }
        if (mldPlayer != null) {
            mldPlayer.stop();
        }
    }

    /**
     * Closes this presenter and releases any playback resources it is holding.
     */
    @Override
    public void close() {
        nextCallbackGeneration();
        stopPlayback();
        if (sampledPlayer != null) {
            sampledPlayer.close();
            sampledPlayer = null;
        }
        if (midiEventPlayer != null) {
            midiEventPlayer.close();
            midiEventPlayer = null;
        }
        if (mldPlayer != null) {
            mldPlayer.close();
            mldPlayer = null;
        }
    }

    /**
     * Sets a playback attribute on this presenter.
     *
     * @param key the attribute identifier
     * @param value the attribute value
     */
    @Override
    public void setAttribute(int key, int value) {
        if (key == LOOP_COUNT) {
            if (value < -1) {
                throw new IllegalArgumentException("value");
            }
            if (playing) {
                return;
            }
        }
        attributes.put(key, value);
        if (key == SET_VOLUME) {
            int level = currentVolumeLevel();
            if (sampledPlayer != null) {
                sampledPlayer.setVolumeLevel(level);
            }
            if (mldPlayer != null) {
                mldPlayer.setVolumeLevel(level);
            }
        } else if (key == SYNC_MODE && mldPlayer != null) {
            updateMldSyncConfiguration();
        }
    }

    /**
     * Registers a single media listener for this presenter. Passing
     * {@code null} removes the current listener.
     *
     * @param listener the listener to register, or {@code null}
     */
    @Override
    public void setMediaListener(MediaListener listener) {
        this.mediaListener = listener;
    }

    private void notifyListener(int type, int param) {
        if (mediaListener != null) {
            mediaListener.mediaAction(this, type, param);
        }
    }

    private void notifyListenerAsync(int type, int param) {
        // No listener means there is no media event to marshal back through the runtime.
        if (mediaListener == null) {
            return;
        }
        long expectedGeneration = currentCallbackGeneration();
        Runnable callback = () -> {
            if (!isCurrentCallbackGeneration(expectedGeneration)) {
                return;
            }
            notifyListener(type, param);
        };
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null) {
            // Re-enter titles on the runtime/application path so media events
            // line up with the same synchronization model as input and paint.
            runtime.postApplicationCallback(callback);
            return;
        }
        MEDIA_CALLBACKS.execute(callback);
    }

    private long nextCallbackGeneration() {
        synchronized (this) {
            return ++callbackGeneration;
        }
    }

    private void setActivePlaybackToken(long playbackToken) {
        synchronized (this) {
            activePlaybackToken = playbackToken;
        }
    }

    private long clearActivePlaybackToken() {
        synchronized (this) {
            long playbackToken = activePlaybackToken;
            activePlaybackToken = Long.MIN_VALUE;
            return playbackToken;
        }
    }

    private void clearActivePlaybackToken(long playbackToken) {
        synchronized (this) {
            if (activePlaybackToken == playbackToken) {
                activePlaybackToken = Long.MIN_VALUE;
            }
        }
    }

    private boolean isActivePlaybackToken(long playbackToken) {
        synchronized (this) {
            return activePlaybackToken == playbackToken;
        }
    }

    private long currentCallbackGeneration() {
        synchronized (this) {
            return callbackGeneration;
        }
    }

    private boolean isCurrentCallbackGeneration(long expectedGeneration) {
        synchronized (this) {
            return callbackGeneration == expectedGeneration;
        }
    }

    private void registerWithRuntime() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null) {
            runtime.registerShutdownResource(this);
        }
    }

    private int currentVolumeLevel() {
        return Math.max(0, Math.min(100, attributes.getOrDefault(SET_VOLUME, 100)));
    }

    private void updateMldSyncConfiguration() {
        if (mldPlayer == null) {
            return;
        }
        // Keep the long-lived MLD backend aligned with the current DoJa sync
        // registration so play/restart do not need a separate rescan pass.
        if (attributes.getOrDefault(SYNC_MODE, ATTR_SYNC_OFF) == ATTR_SYNC_ON &&
                syncEventChannel >= 0 && syncEventKey >= 0) {
            mldPlayer.setSyncEvent(syncEventChannel, syncEventKey);
        } else {
            mldPlayer.clearSyncEvent();
        }
    }

    private boolean useDirectMidiPlayback(MediaManager.PreparedSound prepared, int loopCount) {
        // Short one-shot MIDI effects must not pay Sequencer setup on the app
        // thread; longer or stateful MIDI stays on the sequencer path.
        return loopCount == 0
                && attributes.getOrDefault(SYNC_MODE, ATTR_SYNC_OFF) != ATTR_SYNC_ON
                && MidiEventPlayer.isLowLatencyCandidate(prepared);
    }

    private int configuredLoopCount() {
        return attributes.getOrDefault(LOOP_COUNT, 0);
    }

    private boolean hasUsableMediaResource() {
        if (resource == null) {
            return false;
        }
        if (resource instanceof MediaManager.AbstractMediaResource tracked) {
            return tracked.isUsed();
        }
        return true;
    }

    private boolean requiresStrictStopState() {
        return DoJaProfile.current().isAtLeast(2, 0);
    }

    private void handlePlaybackComplete(long playbackToken) {
        if (!isActivePlaybackToken(playbackToken)) {
            return;
        }
        clearActivePlaybackToken(playbackToken);
        playing = false;
        lastMldSyncTimeMillis = Integer.MIN_VALUE;
        cancelSyncEvents();
        notifyListenerAsync(AUDIO_COMPLETE, 0);
    }

    private void handlePlaybackFailure(long playbackToken, Exception exception, String label) {
        if (!isActivePlaybackToken(playbackToken)) {
            return;
        }
        clearActivePlaybackToken(playbackToken);
        playing = false;
        lastMldSyncTimeMillis = Integer.MIN_VALUE;
        cancelSyncEvents();
        if (TRACE_AUDIO_FAILURES) {
            OpenDoJaLog.error(AudioPresenter.class, label, exception);
        }
        notifyListenerAsync(AUDIO_STOPPED, 0);
    }

    private static int configuredExplicitPortCount() {
        // The official developer documentation says DoJa-4.0 commonly guarantees
        // at least four simultaneous AudioPresenter instances when a port is
        // explicitly specified. openDoJa uses 4 as the compatibility default
        // and leaves the exact slot count configurable per host.
        return Math.max(1, opendoja.host.OpenDoJaLaunchArgs.getInt(
                opendoja.host.OpenDoJaLaunchArgs.AUDIO_PRESENTER_PORTS, 4));
    }

    final boolean isPlaying() {
        return playing;
    }

    private void scheduleSyncEvents(MediaManager.PreparedSound prepared, int offsetMillis) {
        if (attributes.getOrDefault(SYNC_MODE, ATTR_SYNC_OFF) != ATTR_SYNC_ON) {
            return;
        }
        if (syncEventChannel < 0 || syncEventKey < 0) {
            return;
        }
        if (prepared.kind() != MediaManager.PreparedSound.Kind.MIDI || sequencer == null) {
            return;
        }
        scheduleSyncEventsForSequence(sequencer.getSequence(), offsetMillis);
    }

    private void scheduleSyncEventsForSequence(Sequence sequence, int offsetMillis) {
        cancelSyncEvents();
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null || sequence == null) {
            return;
        }
        long tickLength = sequence.getTickLength();
        long microsecondLength = sequence.getMicrosecondLength();
        if (tickLength <= 0 || microsecondLength <= 0) {
            return;
        }
        long lastScheduled = Long.MIN_VALUE;
        for (javax.sound.midi.Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                javax.sound.midi.MidiEvent event = track.get(i);
                if (!(event.getMessage() instanceof ShortMessage shortMessage)) {
                    continue;
                }
                if (shortMessage.getCommand() != ShortMessage.NOTE_ON || shortMessage.getData2() <= 0) {
                    continue;
                }
                if (shortMessage.getChannel() != syncEventChannel || shortMessage.getData1() != syncEventKey) {
                    continue;
                }
                long millis = Math.round((double) event.getTick() * microsecondLength / tickLength / 1_000.0d);
                if (millis < offsetMillis) {
                    continue;
                }
                if (lastScheduled != Long.MIN_VALUE && millis - lastScheduled < SYNC_EVENT_PRECISION_MS) {
                    continue;
                }
                long delay = Math.max(0L, millis - offsetMillis);
                syncEventTasks.add(runtime.scheduler().schedule(
                        () -> notifyListener(AUDIO_SYNC, 0),
                        delay,
                        TimeUnit.MILLISECONDS
                ));
                lastScheduled = millis;
            }
        }
    }

    private void cancelSyncEvents() {
        for (ScheduledFuture<?> future : syncEventTasks) {
            future.cancel(false);
        }
        syncEventTasks.clear();
    }

    private static int singlePlaybackDurationMillis(MediaManager.PreparedSound prepared) throws Exception {
        return switch (prepared.kind()) {
            case MIDI -> {
                Sequence sequence = prepared.midiSequence();
                yield (int) Math.round(sequence.getMicrosecondLength() / 1_000.0d);
            }
            case MLD -> {
                double seconds = prepared.mld().getDuration(true);
                yield Double.isFinite(seconds) ? (int) Math.round(seconds * 1_000.0d) : Integer.MAX_VALUE;
            }
            case SAMPLED -> {
                if (prepared.sampledFormat() == null) {
                    yield 0;
                }
                int frameSize = Math.max(1, prepared.sampledFormat().getFrameSize());
                float frameRate = Math.max(1.0f, prepared.sampledFormat().getFrameRate());
                int totalFrames = prepared.bytes().length / frameSize;
                yield (int) Math.round((totalFrames * 1_000.0d) / frameRate);
            }
            case UNKNOWN -> 0;
        };
    }

    private final class MldListener implements MLDPCMPlayer.Listener {
        @Override
        public void onLoop(long playbackToken) {
            if (!isActivePlaybackToken(playbackToken)) {
                return;
            }
            lastMldSyncTimeMillis = Integer.MIN_VALUE;
            notifyListenerAsync(AUDIO_LOOPED, 0);
        }

        @Override
        public void onSync(int timeMillis, long playbackToken) {
            if (!isActivePlaybackToken(playbackToken)) {
                return;
            }
            // DoJa collapses sync callbacks that land inside the same 100 ms window.
            if (lastMldSyncTimeMillis != Integer.MIN_VALUE &&
                    timeMillis - lastMldSyncTimeMillis < SYNC_EVENT_PRECISION_MS) {
                return;
            }
            lastMldSyncTimeMillis = timeMillis;
            notifyListenerAsync(AUDIO_SYNC, 0);
        }

        @Override
        public void onComplete(long playbackToken) {
            handlePlaybackComplete(playbackToken);
        }

        @Override
        public void onFailure(Exception exception, long playbackToken) {
            handlePlaybackFailure(playbackToken, exception, "MLD playback failed");
        }
    }

    private final class SampledListener implements SampledPCMPlayer.Listener {
        @Override
        public void onLoop(long playbackToken) {
            if (!isActivePlaybackToken(playbackToken)) {
                return;
            }
            notifyListenerAsync(AUDIO_LOOPED, 0);
        }

        @Override
        public void onComplete(long playbackToken) {
            handlePlaybackComplete(playbackToken);
        }

        @Override
        public void onFailure(Exception exception, long playbackToken) {
            handlePlaybackFailure(playbackToken, exception, "Sampled playback failed");
        }
    }

    private final class MidiEventListener implements MidiEventPlayer.Listener {
        @Override
        public void onComplete(long playbackToken) {
            handlePlaybackComplete(playbackToken);
        }

        @Override
        public void onFailure(Exception exception, long playbackToken) {
            handlePlaybackFailure(playbackToken, exception, "MIDI event playback failed");
        }
    }
}
