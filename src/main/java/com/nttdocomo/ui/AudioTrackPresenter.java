package com.nttdocomo.ui;

/**
 * Defines an audio-track presenter.
 */
public class AudioTrackPresenter extends AudioPresenter {
    AudioTrackPresenter() {
    }

    /**
     * Starts playback of the audio track from the beginning.
     */
    @Override
    public void play() {
        super.play();
    }

    /**
     * Starts playback of the audio track from the specified start position.
     *
     * @param time the start position in milliseconds from the beginning
     */
    public void play(int time) {
        super.play(time);
    }

    /**
     * Sets an attribute value related to the playback method.
     *
     * @param attr the attribute to set
     * @param value the value to set
     */
    @Override
    public void setAttribute(int attr, int value) {
        super.setAttribute(attr, value);
    }

    /**
     * Sets a sync event for the audio track.
     *
     * @param type the sync-event type
     * @param time the event time in milliseconds
     */
    @Override
    public void setSyncEvent(int type, int time) {
        super.setSyncEvent(type, time);
    }

    /**
     * Sets image data for the audio track.
     *
     * @param image the image to set
     */
    public void setSound(MediaImage image) {
        setData(null);
    }

    /**
     * Sets sound data for the audio track.
     *
     * @param sound the media sound to set
     */
    @Override
    public void setSound(MediaSound sound) {
        super.setSound(sound);
    }

    /**
     * Gets the 3D audio controller associated with this audio-track presenter.
     *
     * @return the 3D audio controller for this presenter
     */
    @Override
    public Audio3D getAudio3D() {
        return super.getAudio3D();
    }

    /**
     * Pauses playback of the audio track.
     */
    @Override
    public void pause() {
        super.pause();
    }

    /**
     * Restarts playback of the audio track.
     */
    @Override
    public void restart() {
        super.restart();
    }

    /**
     * Returns the playback time from the beginning in milliseconds.
     *
     * @return the current playback time
     */
    @Override
    public int getCurrentTime() {
        return super.getCurrentTime();
    }

    /**
     * Returns the total playback time in milliseconds.
     *
     * @return the total playback time
     */
    @Override
    public int getTotalTime() {
        return super.getTotalTime();
    }
}
