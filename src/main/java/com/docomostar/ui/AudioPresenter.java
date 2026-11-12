package com.docomostar.ui;

import com.docomostar.media.MediaManager;
import com.docomostar.media.MediaResource;
import com.docomostar.media.MediaSound;

/** Keeps STAR presenter identity while delegating preparation and playback to the host. */
public class AudioPresenter implements MediaPresenter {
    public static final int AUDIO_PLAYING = com.nttdocomo.ui.AudioPresenter.AUDIO_PLAYING;
    public static final int AUDIO_STOPPED = com.nttdocomo.ui.AudioPresenter.AUDIO_STOPPED;
    public static final int AUDIO_COMPLETE = com.nttdocomo.ui.AudioPresenter.AUDIO_COMPLETE;
    public static final int AUDIO_SYNC = com.nttdocomo.ui.AudioPresenter.AUDIO_SYNC;
    public static final int AUDIO_PAUSED = com.nttdocomo.ui.AudioPresenter.AUDIO_PAUSED;
    public static final int AUDIO_RESTARTED = com.nttdocomo.ui.AudioPresenter.AUDIO_RESTARTED;
    public static final int AUDIO_LOOPED = com.nttdocomo.ui.AudioPresenter.AUDIO_LOOPED;
    public static final int PRIORITY = com.nttdocomo.ui.AudioPresenter.PRIORITY;
    public static final int SYNC_MODE = com.nttdocomo.ui.AudioPresenter.SYNC_MODE;
    public static final int TRANSPOSE_KEY = com.nttdocomo.ui.AudioPresenter.TRANSPOSE_KEY;
    public static final int SET_VOLUME = com.nttdocomo.ui.AudioPresenter.SET_VOLUME;
    public static final int CHANGE_TEMPO = com.nttdocomo.ui.AudioPresenter.CHANGE_TEMPO;
    public static final int LOOP_COUNT = com.nttdocomo.ui.AudioPresenter.LOOP_COUNT;
    public static final int ATTR_SYNC_OFF = com.nttdocomo.ui.AudioPresenter.ATTR_SYNC_OFF;
    public static final int ATTR_SYNC_ON = com.nttdocomo.ui.AudioPresenter.ATTR_SYNC_ON;
    public static final int MIN_PRIORITY = com.nttdocomo.ui.AudioPresenter.MIN_PRIORITY;
    public static final int NORM_PRIORITY = com.nttdocomo.ui.AudioPresenter.NORM_PRIORITY;
    public static final int MAX_PRIORITY = com.nttdocomo.ui.AudioPresenter.MAX_PRIORITY;
    public static final int MIN_OPTION_ATTR = com.nttdocomo.ui.AudioPresenter.MIN_OPTION_ATTR;
    public static final int MAX_OPTION_ATTR = com.nttdocomo.ui.AudioPresenter.MAX_OPTION_ATTR;
    private final com.nttdocomo.ui.AudioPresenter delegate;
    private MediaSound sound;

    protected AudioPresenter() { this(com.nttdocomo.ui.AudioPresenter.getAudioPresenter()); }
    private AudioPresenter(com.nttdocomo.ui.AudioPresenter delegate) { this.delegate = delegate; }
    public static AudioPresenter getAudioPresenter() { return new AudioPresenter(); }
    public static AudioPresenter getAudioPresenter(int port) {
        return new AudioPresenter(com.nttdocomo.ui.AudioPresenter.getAudioPresenter(port));
    }
    public void setSound(MediaSound sound) {
        try {
            delegate.setSound((com.nttdocomo.ui.MediaSound) MediaManager.unwrap(sound));
            this.sound = sound;
        } catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public MediaResource getMediaResource() { return sound; }
    public void setMediaListener(MediaListener listener) {
        delegate.setMediaListener(listener == null ? null : (presenter, type, param) -> listener.mediaAction(this, type, param));
    }
    public void play() {
        try { delegate.play(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void play(int time) {
        try { delegate.play(time); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void stop() {
        try { delegate.stop(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void pause() {
        try { delegate.pause(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void restart() {
        try { delegate.restart(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public int getCurrentTime() {
        try { return delegate.getCurrentTime(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public int getTotalTime() {
        try { return delegate.getTotalTime(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setSyncEvent(int channel, int key) {
        try { delegate.setSyncEvent(channel, key); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setAttribute(int key, int value) {
        try { delegate.setAttribute(key, value); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
}
