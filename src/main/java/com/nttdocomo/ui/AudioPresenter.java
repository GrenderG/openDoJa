package com.nttdocomo.ui;

import opendoja.audio.SampledPCMPlayer;
import opendoja.audio.mld.MLDPCMPlayer;
import opendoja.host.DoJaProfile;
import opendoja.host.DoJaRuntime;
import opendoja.host.OpenDoJaLog;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import javax.sound.midi.ShortMessage;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class AudioPresenter implements MediaPresenter, AutoCloseable {
    private static final boolean TRACE_AUDIO_FAILURES = opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.TRACE_AUDIO_FAILURES);
    public static final int AUDIO_PLAYING = 1;
    public static final int AUDIO_STOPPED = 2;
    public static final int AUDIO_COMPLETE = 3;
    public static final int AUDIO_SYNC = 4;
    public static final int AUDIO_PAUSED = 5;
    public static final int AUDIO_RESTARTED = 6;
    public static final int AUDIO_LOOPED = 7;
    public static final int PRIORITY = 1;
    public static final int SYNC_MODE = 2;
    public static final int TRANSPOSE_KEY = 3;
    public static final int SET_VOLUME = 4;
    public static final int CHANGE_TEMPO = 5;
    public static final int LOOP_COUNT = 6;
    public static final int ATTR_SYNC_OFF = 0;
    public static final int ATTR_SYNC_ON = 1;
    public static final int MIN_PRIORITY = 1;
    public static final int NORM_PRIORITY = 5;
    public static final int MAX_PRIORITY = 10;
    public static final int MIN_OPTION_ATTR = 128;
    public static final int MAX_OPTION_ATTR = 255;
    protected static final int MIN_VENDOR_ATTR = 64;
    protected static final int MAX_VENDOR_ATTR = 127;
    protected static final int MIN_VENDOR_AUDIO_EVENT = 64;
    protected static final int MAX_VENDOR_AUDIO_EVENT = 127;
    private static final int SYNC_EVENT_PRECISION_MS = 100;

    private final Map<Integer, Integer> attributes = new HashMap<>();
    private final List<ScheduledFuture<?>> syncEventTasks = new ArrayList<>();
    private final Audio3D audio3D = new Audio3D(this);
    private MediaResource resource;
    private MediaListener mediaListener;
    private SampledPCMPlayer sampledPlayer;
    private MLDPCMPlayer mldPlayer;
    private Sequencer sequencer;
    private int pausedPosition;
    private int syncEventChannel = -1;
    private int syncEventKey = -1;
    private volatile boolean playing;

    protected AudioPresenter() {
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

    public static AudioPresenter getAudioPresenter() {
        return new AudioPresenter();
    }

    public static AudioPresenter getAudioPresenter(int port) {
        return new AudioPresenter();
    }

    /**
     * Gets an audio-track presenter object.
     *
     * @return the audio-track presenter object
     */
    public static AudioTrackPresenter getAudioTrackPresenter() {
        return new AudioTrackPresenter();
    }

    public void setSound(MediaSound sound) {
        this.resource = sound;
    }

    @Override
    public void setData(MediaData data) {
        this.resource = data;
    }

    @Override
    public MediaResource getMediaResource() {
        return resource;
    }

    @Override
    public void play() {
        play(0);
    }

    public void play(int time) {
        if (time < 0) {
            throw new IllegalArgumentException("time");
        }
        registerWithRuntime();
        stopPlayback();
        if (!(resource instanceof MediaManager.BasicMediaSound sound)) {
            playing = false;
            notifyListener(AUDIO_STOPPED, 0);
            return;
        }
        try {
            MediaManager.PreparedSound prepared = sound.prepared();
            int loopCount = configuredLoopCount();
            if (time >= singlePlaybackDurationMillis(prepared)) {
                playing = false;
                notifyListener(AUDIO_PLAYING, 0);
                notifyListener(AUDIO_COMPLETE, 0);
                return;
            }
            if (prepared.kind() == MediaManager.PreparedSound.Kind.MIDI) {
                sequencer = MidiSystem.getSequencer();
                sequencer.open();
                sequencer.setSequence(new ByteArrayInputStream(prepared.bytes()));
                if (loopCount < 0) {
                    sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
                } else {
                    sequencer.setLoopCount(loopCount);
                }
                if (time > 0) {
                    sequencer.setMicrosecondPosition(Math.min((long) time * 1_000L, sequencer.getMicrosecondLength()));
                }
                sequencer.start();
            } else if (prepared.kind() == MediaManager.PreparedSound.Kind.MLD) {
                if (mldPlayer == null) {
                    mldPlayer = new MLDPCMPlayer(new MldListener());
                }
                mldPlayer.setVolumeLevel(currentVolumeLevel());
                mldPlayer.start(prepared, loopCount, time);
            } else {
                if (sampledPlayer == null) {
                    sampledPlayer = new SampledPCMPlayer(new SampledListener());
                }
                sampledPlayer.setVolumeLevel(currentVolumeLevel());
                sampledPlayer.start(prepared, loopCount, time);
            }
            playing = true;
            notifyListener(AUDIO_PLAYING, 0);
            scheduleSyncEvents(prepared, time);
        } catch (Exception e) {
            playing = false;
            if (TRACE_AUDIO_FAILURES) {
                OpenDoJaLog.error(AudioPresenter.class, "Audio playback failed", e);
            }
            notifyListener(AUDIO_STOPPED, 0);
        }
    }

    public void pause() {
        if (sampledPlayer != null) {
            sampledPlayer.pause();
            playing = false;
            cancelSyncEvents();
            notifyListener(AUDIO_PAUSED, 0);
        } else if (mldPlayer != null) {
            mldPlayer.pause();
            playing = false;
            cancelSyncEvents();
            notifyListener(AUDIO_PAUSED, 0);
        } else if (sequencer != null) {
            pausedPosition = (int) sequencer.getTickPosition();
            sequencer.stop();
            playing = false;
            cancelSyncEvents();
            notifyListener(AUDIO_PAUSED, 0);
        }
    }

    public void restart() {
        if (sampledPlayer != null) {
            sampledPlayer.restart();
            playing = true;
            notifyListener(AUDIO_RESTARTED, 0);
        } else if (mldPlayer != null) {
            mldPlayer.restart();
            playing = true;
            notifyListener(AUDIO_RESTARTED, 0);
        } else if (sequencer != null) {
            sequencer.setTickPosition(pausedPosition);
            sequencer.start();
            playing = true;
            scheduleSyncEventsForSequence(sequencer.getSequence(), getCurrentTime());
            notifyListener(AUDIO_RESTARTED, 0);
        }
    }

    public int getCurrentTime() {
        if (sampledPlayer != null) {
            return sampledPlayer.getCurrentTimeMillis();
        }
        if (mldPlayer != null) {
            return mldPlayer.getCurrentTimeMillis();
        }
        if (sequencer != null) {
            return (int) (sequencer.getMicrosecondPosition() / 1_000L);
        }
        return 0;
    }

    public int getTotalTime() {
        if (sampledPlayer != null) {
            return sampledPlayer.getTotalTimeMillis();
        }
        if (mldPlayer != null) {
            return mldPlayer.getTotalTimeMillis();
        }
        if (sequencer != null) {
            return (int) (sequencer.getMicrosecondLength() / 1_000L);
        }
        return 0;
    }

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
    }

    @Override
    public void stop() {
        if (requiresStrictStopState() && !hasUsableMediaResource()) {
            throw new UIException(UIException.ILLEGAL_STATE);
        }
        stopPlayback();
        notifyListener(AUDIO_STOPPED, 0);
    }

    private void stopPlayback() {
        playing = false;
        cancelSyncEvents();
        if (sampledPlayer != null) {
            sampledPlayer.stop();
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

    @Override
    public void close() {
        stopPlayback();
        if (sampledPlayer != null) {
            sampledPlayer.close();
            sampledPlayer = null;
        }
        if (mldPlayer != null) {
            mldPlayer.close();
            mldPlayer = null;
        }
    }

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
        }
    }

    @Override
    public void setMediaListener(MediaListener listener) {
        this.mediaListener = listener;
    }

    private void notifyListener(int type, int param) {
        if (mediaListener != null) {
            mediaListener.mediaAction(this, type, param);
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
                Sequence sequence = MidiSystem.getSequence(new ByteArrayInputStream(prepared.bytes()));
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
        public void onLoop() {
            notifyListener(AUDIO_LOOPED, 0);
        }

        @Override
        public void onComplete() {
            playing = false;
            cancelSyncEvents();
            notifyListener(AUDIO_COMPLETE, 0);
        }

        @Override
        public void onFailure(Exception exception) {
            playing = false;
            cancelSyncEvents();
            if (TRACE_AUDIO_FAILURES) {
                OpenDoJaLog.error(AudioPresenter.class, "MLD playback failed", exception);
            }
            notifyListener(AUDIO_STOPPED, 0);
        }
    }

    private final class SampledListener implements SampledPCMPlayer.Listener {
        @Override
        public void onLoop() {
            notifyListener(AUDIO_LOOPED, 0);
        }

        @Override
        public void onComplete() {
            playing = false;
            cancelSyncEvents();
            notifyListener(AUDIO_COMPLETE, 0);
        }

        @Override
        public void onFailure(Exception exception) {
            playing = false;
            cancelSyncEvents();
            if (TRACE_AUDIO_FAILURES) {
                OpenDoJaLog.error(AudioPresenter.class, "Sampled playback failed", exception);
            }
            notifyListener(AUDIO_STOPPED, 0);
        }
    }
}
