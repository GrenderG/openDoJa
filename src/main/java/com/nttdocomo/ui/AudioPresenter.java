package com.nttdocomo.ui;

import opendoja.audio.mld.MldMidiAdapter;
import opendoja.host.DoJaRuntime;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequencer;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.ByteArrayInputStream;
import java.io.IOException;
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
    private Clip clip;
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
            if (looksLikeMidi(sound)) {
                sequencer = MidiSystem.getSequencer();
                sequencer.open();
                sequencer.setSequence(new ByteArrayInputStream(sound.bytes()));
                if (loopCount <= 0) {
                    sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
                } else if (loopCount > 1) {
                    sequencer.setLoopCount(loopCount - 1);
                }
                sequencer.start();
            } else if (looksLikeMld(sound)) {
                sequencer = MidiSystem.getSequencer();
                sequencer.open();
                sequencer.setSequence(MldMidiAdapter.toMidiSequence(sound.bytes()));
                if (loopCount <= 0) {
                    sequencer.setLoopCount(Sequencer.LOOP_CONTINUOUSLY);
                } else if (loopCount > 1) {
                    sequencer.setLoopCount(loopCount - 1);
                }
                sequencer.start();
            } else {
                AudioInputStream in = AudioSystem.getAudioInputStream(new ByteArrayInputStream(sound.bytes()));
                clip = AudioSystem.getClip();
                clip.open(in);
                if (loopCount > 1) {
                    clip.loop(loopCount - 1);
                } else {
                    clip.start();
                }
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
        if (clip != null) {
            pausedPosition = clip.getFramePosition();
            clip.stop();
            notifyListener(AUDIO_PAUSED, 0);
        } else if (sequencer != null) {
            pausedPosition = (int) sequencer.getTickPosition();
            sequencer.stop();
            notifyListener(AUDIO_PAUSED, 0);
        }
    }

    public void restart() {
        if (clip != null) {
            clip.setFramePosition(pausedPosition);
            clip.start();
            notifyListener(AUDIO_RESTARTED, 0);
        } else if (sequencer != null) {
            sequencer.setTickPosition(pausedPosition);
            sequencer.start();
            notifyListener(AUDIO_RESTARTED, 0);
        }
    }

    public int getCurrentTime() {
        if (clip != null) {
            return (int) (clip.getMicrosecondPosition() / 1_000L);
        }
        if (sequencer != null) {
            return (int) (sequencer.getMicrosecondPosition() / 1_000L);
        }
        return 0;
    }

    public int getTotalTime() {
        if (clip != null) {
            return (int) (clip.getMicrosecondLength() / 1_000L);
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
        if (clip != null) {
            clip.stop();
            clip.close();
            clip = null;
        }
        if (sequencer != null) {
            sequencer.stop();
            sequencer.close();
            sequencer = null;
        }
        notifyListener(AUDIO_STOPPED, 0);
    }

    @Override
    public void close() {
        stop();
    }

    @Override
    public void setAttribute(int key, int value) {
        attributes.put(key, value);
    }

    @Override
    public void setMediaListener(MediaListener listener) {
        this.mediaListener = listener;
    }

    private boolean looksLikeMidi(MediaManager.BasicMediaSound sound) {
        byte[] bytes = sound.bytes();
        return bytes.length > 4 && bytes[0] == 'M' && bytes[1] == 'T' && bytes[2] == 'h' && bytes[3] == 'd';
    }

    private boolean looksLikeMld(MediaManager.BasicMediaSound sound) {
        byte[] bytes = sound.bytes();
        return bytes.length > 4 && bytes[0] == 'm' && bytes[1] == 'e' && bytes[2] == 'l' && bytes[3] == 'o';
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
}
