package com.nttdocomo.ui;

import opendoja.audio.SampledPcmPlayer;
import opendoja.audio.mld.MldPcmPlayer;
import opendoja.host.DoJaRuntime;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequencer;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

public class AudioPresenter implements MediaPresenter, AutoCloseable {
    private static final boolean TRACE_AUDIO_FAILURES = Boolean.getBoolean("opendoja.traceAudioFailures");
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

    private final Map<Integer, Integer> attributes = new HashMap<>();
    private MediaResource resource;
    private MediaListener mediaListener;
    private SampledPcmPlayer sampledPlayer;
    private MldPcmPlayer mldPlayer;
    private Sequencer sequencer;
    private int pausedPosition;

    protected AudioPresenter() {
        registerWithRuntime();
    }

    public Audio3D getAudio3D() {
        return new Audio3D();
    }

    public static AudioPresenter getAudioPresenter() {
        return new AudioPresenter();
    }

    public static AudioPresenter getAudioPresenter(int port) {
        return new AudioPresenter();
    }

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
        play(1);
    }

    public void play(int loopCount) {
        registerWithRuntime();
        stop();
        if (!(resource instanceof MediaManager.BasicMediaSound sound)) {
            notifyListener(AUDIO_STOPPED, 0);
            return;
        }
        try {
            MediaManager.PreparedSound prepared = sound.prepared();
            if (prepared.kind() == MediaManager.PreparedSound.Kind.MIDI) {
                sequencer = MidiSystem.getSequencer();
                sequencer.open();
                sequencer.setSequence(new ByteArrayInputStream(prepared.bytes()));
                if (loopCount <= 0) {
                    sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
                } else if (loopCount > 1) {
                    sequencer.setLoopCount(loopCount - 1);
                }
                sequencer.start();
            } else if (prepared.kind() == MediaManager.PreparedSound.Kind.MLD) {
                if (mldPlayer == null) {
                    mldPlayer = new MldPcmPlayer(new MldListener());
                }
                mldPlayer.start(prepared, loopCount);
            } else {
                if (sampledPlayer == null) {
                    sampledPlayer = new SampledPcmPlayer(new SampledListener());
                }
                sampledPlayer.start(prepared, loopCount);
            }
            notifyListener(AUDIO_PLAYING, 0);
        } catch (Exception e) {
            if (TRACE_AUDIO_FAILURES) {
                e.printStackTrace(System.err);
            }
            notifyListener(AUDIO_STOPPED, 0);
        }
    }

    public void pause() {
        if (sampledPlayer != null) {
            sampledPlayer.pause();
            notifyListener(AUDIO_PAUSED, 0);
        } else if (mldPlayer != null) {
            mldPlayer.pause();
            notifyListener(AUDIO_PAUSED, 0);
        } else if (sequencer != null) {
            pausedPosition = (int) sequencer.getTickPosition();
            sequencer.stop();
            notifyListener(AUDIO_PAUSED, 0);
        }
    }

    public void restart() {
        if (sampledPlayer != null) {
            sampledPlayer.restart();
            notifyListener(AUDIO_RESTARTED, 0);
        } else if (mldPlayer != null) {
            mldPlayer.restart();
            notifyListener(AUDIO_RESTARTED, 0);
        } else if (sequencer != null) {
            sequencer.setTickPosition(pausedPosition);
            sequencer.start();
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

    public void setSyncEvent(int type, int time) {
    }

    @Override
    public void stop() {
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
        notifyListener(AUDIO_STOPPED, 0);
    }

    @Override
    public void close() {
        stop();
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
        attributes.put(key, value);
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

    private final class MldListener implements MldPcmPlayer.Listener {
        @Override
        public void onLoop() {
            notifyListener(AUDIO_LOOPED, 0);
        }

        @Override
        public void onComplete() {
            notifyListener(AUDIO_COMPLETE, 0);
        }

        @Override
        public void onFailure(Exception exception) {
            if (TRACE_AUDIO_FAILURES) {
                exception.printStackTrace(System.err);
            }
            notifyListener(AUDIO_STOPPED, 0);
        }
    }

    private final class SampledListener implements SampledPcmPlayer.Listener {
        @Override
        public void onLoop() {
            notifyListener(AUDIO_LOOPED, 0);
        }

        @Override
        public void onComplete() {
            notifyListener(AUDIO_COMPLETE, 0);
        }

        @Override
        public void onFailure(Exception exception) {
            if (TRACE_AUDIO_FAILURES) {
                exception.printStackTrace(System.err);
            }
            notifyListener(AUDIO_STOPPED, 0);
        }
    }
}
