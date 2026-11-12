package com.nttdocomo.opt.device;

import com.nttdocomo.fs.File;

import java.io.IOException;

/**
 * Provides the means to access the native media player.
 */
public class MediaPlayer {
    /** Return value meaning playback ended by the stop button (=1). */
    public static final int STATUS_STOPPED = 1;
    /** Return value meaning playback reached the end of content (=2). */
    public static final int STATUS_COMPLETED = 2;
    /** Return value meaning playback ended by the previous/rewind button (=3). */
    public static final int STATUS_PREVIOUS_SELECTED = 3;
    /** Return value meaning playback ended by the next/fast-forward button (=4). */
    public static final int STATUS_NEXT_SELECTED = 4;
    /** Return value meaning playback was suspended by an external factor (=97). */
    public static final int STATUS_SUSPENDED = 97;
    /** Return value meaning playback could not continue for another reason (=98). */
    public static final int STATUS_FAILED = 98;

    MediaPlayer() {
    }

    /**
     * Gets the media-player object.
     * The same reference is always returned.
     *
     * @return the media-player object
     */
    public static MediaPlayer getMediaPlayer() {
        return _OptionalDeviceSupport.mediaPlayer();
    }

    /**
     * Plays the specified file from the beginning with the native media
     * player.
     *
     * @param file the file to play
     * @return a playback-result status defined by this class
     * @throws IOException if the specified file cannot be accessed
     */
    public int play(File file) throws IOException {
        return play(file, 0L);
    }

    /**
     * Plays the specified file from the specified position with the native
     * media player.
     *
     * @param file the file to play
     * @param position the playback start position, in milliseconds
     * @return a playback-result status defined by this class
     * @throws IOException if the specified file cannot be accessed
     */
    public int play(File file, long position) throws IOException {
        return _OptionalDeviceSupport.playMedia(file, position);
    }

    /**
     * Gets the stopped position of the previous native playback.
     *
     * @return the stopped position in milliseconds
     */
    public long getLastStoppedPosition() {
        return _OptionalDeviceSupport.lastStoppedPosition();
    }
}
