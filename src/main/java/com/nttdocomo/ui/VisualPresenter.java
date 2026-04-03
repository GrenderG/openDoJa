package com.nttdocomo.ui;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines a presenter for visual media.
 */
public class VisualPresenter extends Component implements MediaPresenter {
    /** Attribute indicating the X position at which the image is displayed (=1). */
    public static final int IMAGE_XPOS = 1;

    /** Attribute indicating the Y position at which the image is displayed (=2). */
    public static final int IMAGE_YPOS = 2;

    /** Attribute specifying the playback mode of the media image (=3). */
    public static final int PLAYER_MODE = 3;

    /** Attribute value indicating preference for the native player (=0). */
    public static final int ATTR_PREFER_NATIVE_PLAYER = 0;

    /** Attribute value indicating preference for the inline player (=1). */
    public static final int ATTR_PREFER_INLINE_PLAYER = 1;

    /** Attribute value indicating native-player-only playback (=2). */
    public static final int ATTR_FORCE_NATIVE_PLAYER = 2;

    /** Attribute value indicating inline-player-only playback (=3). */
    public static final int ATTR_FORCE_INLINE_PLAYER = 3;

    /** Attribute value indicating preference for the fullscreen player (=4). */
    public static final int ATTR_PREFER_FULLSCREEN_PLAYER = 4;

    /** Attribute value indicating fullscreen-player-only playback (=5). */
    public static final int ATTR_FORCE_FULLSCREEN_PLAYER = 5;

    /** Attribute indicating whether audio is played for media with audio (=4). */
    public static final int AUDIO_MODE = 4;

    /** Attribute value indicating that audio is not played (=0). */
    public static final int ATTR_AUDIO_OFF = 0;

    /** Attribute value indicating that audio is played (=1). */
    public static final int ATTR_AUDIO_ON = 1;

    /** Event type indicating that playback started (=1). */
    public static final int VISUAL_PLAYING = 1;

    /** Event type indicating that playback was interrupted (=2). */
    public static final int VISUAL_STOPPED = 2;

    /** Event type indicating that playback finished (=3). */
    public static final int VISUAL_COMPLETE = 3;

    /**
     * Minimum value for vendor attr.
     */
    protected static final int MIN_VENDOR_ATTR = 64;
    /**
     * Maximum value for vendor attr.
     */
    protected static final int MAX_VENDOR_ATTR = 127;
    /**
     * Minimum value for vendor visual event.
     */
    protected static final int MIN_VENDOR_VISUAL_EVENT = 64;
    /**
     * Maximum value for vendor visual event.
     */
    protected static final int MAX_VENDOR_VISUAL_EVENT = 127;

    private final Map<Integer, Integer> attributes = new HashMap<>();
    private MediaResource resource;
    private MediaListener listener;
    private boolean playing;
    private boolean pendingPlay;

    /**
     * Creates an empty presenter object.
     */
    public VisualPresenter() {
        attributes.put(PLAYER_MODE, ATTR_PREFER_NATIVE_PLAYER);
        attributes.put(AUDIO_MODE, ATTR_AUDIO_ON);
        attributes.put(IMAGE_XPOS, 0);
        attributes.put(IMAGE_YPOS, 0);
    }

    /**
     * Sets the media image.
     *
     * @param image the media image
     */
    public void setImage(MediaImage image) {
        if (image == null) {
            throw new NullPointerException("image");
        }
        setResource(image);
    }

    /**
     * Sets size.
     */
    @Override
    public void setSize(int width, int height) {
        super.setSize(width, height);
    }

    /**
     * Sets the media data.
     *
     * @param data the media data
     * @throws UnsupportedOperationException if the terminal does not support
     *         this method
     * @throws UIException if the media resource cannot be accepted
     */
    @Override
    public void setData(MediaData data) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        setResource(data);
    }

    /**
     * Gets the currently set media resource.
     *
     * @return the media resource
     */
    @Override
    public MediaResource getMediaResource() {
        return resource;
    }

    /**
     * Starts playback of the media data.
     *
     * @throws UIException if media data is not set or cannot be played
     */
    @Override
    public void play() {
        ensurePlayableResource();
        if (playing) {
            stopInternal(true);
        }
        if (!visible()) {
            pendingPlay = true;
            return;
        }
        playing = true;
        pendingPlay = false;
        notifyListener(VISUAL_PLAYING, 0);
    }

    /**
     * Stops playback of the media data.
     *
     * @throws UIException if media data is not set or has not been use()'d
     */
    @Override
    public void stop() {
        ensurePlayableResource();
        stopInternal(true);
    }

    /**
     * Sets an attribute related to playback.
     * If a non-controllable or non-existent attribute is specified, it is
     * ignored without doing anything.
     *
     * @param attr the attribute kind
     * @param value the attribute value
     * @throws IllegalArgumentException if {@code value} is invalid for a valid
     *         attribute
     */
    @Override
    public void setAttribute(int attr, int value) {
        if (attr == IMAGE_XPOS || attr == IMAGE_YPOS) {
            attributes.put(attr, value);
            return;
        }
        if (attr == PLAYER_MODE) {
            if (value < ATTR_PREFER_NATIVE_PLAYER || value > ATTR_FORCE_FULLSCREEN_PLAYER) {
                throw new IllegalArgumentException("Invalid player mode: " + value);
            }
            attributes.put(attr, value);
            return;
        }
        if (attr == AUDIO_MODE) {
            if (value != ATTR_AUDIO_OFF && value != ATTR_AUDIO_ON) {
                throw new IllegalArgumentException("Invalid audio mode: " + value);
            }
            attributes.put(attr, value);
        }
    }

    /**
     * Registers a listener.
     * Only one listener can be registered with a presenter.
     *
     * @param listener the listener to register, or {@code null} to clear it
     */
    @Override
    public void setMediaListener(MediaListener listener) {
        this.listener = listener;
    }

    /**
     * Sets whether this presenter is visible.
     * If playback was requested while the presenter was not visible, playback
     * starts when it becomes visible.
     *
     * @param visible {@code true} if the presenter should be visible
     */
    @Override
    public void setVisible(boolean visible) {
        boolean wasVisible = visible();
        super.setVisible(visible);
        if (!wasVisible && visible && pendingPlay && !playing) {
            playing = true;
            pendingPlay = false;
            notifyListener(VISUAL_PLAYING, 0);
        }
    }

    private void ensurePlayableResource() {
        if (!(resource instanceof MediaManager.AbstractMediaResource tracked)) {
            throw new UIException(UIException.ILLEGAL_STATE, "No media data is set");
        }
        if (!tracked.isUsed()) {
            throw new UIException(UIException.ILLEGAL_STATE, "Media data has not been use()'d");
        }
    }

    private void setResource(MediaResource resource) {
        if (playing) {
            throw new UIException(UIException.ILLEGAL_STATE, "Cannot change media while playing");
        }
        if (!(resource instanceof MediaManager.AbstractMediaResource tracked)) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, "Unsupported media resource implementation");
        }
        if (!tracked.isUsed()) {
            throw new UIException(UIException.ILLEGAL_STATE, "Media data has not been use()'d");
        }
        this.resource = resource;
    }

    private void stopInternal(boolean notify) {
        boolean wasPlaying = playing || pendingPlay;
        playing = false;
        pendingPlay = false;
        if (notify && wasPlaying) {
            notifyListener(VISUAL_STOPPED, 0);
        }
    }

    private void notifyListener(int type, int param) {
        if (listener != null) {
            listener.mediaAction(this, type, param);
        }
    }
}
