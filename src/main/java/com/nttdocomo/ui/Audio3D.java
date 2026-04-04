package com.nttdocomo.ui;

import com.nttdocomo.ui.sound3d.SoundMotion;
import com.nttdocomo.ui.sound3d.SoundPosition;

/**
 * Defines 3D sound control.
 * This class enables and disables control of the 3D environment and sets 3D
 * localization information.
 */
public class Audio3D {
    private static final int TOTAL_RESOURCES =
            java.lang.Math.max(1, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.AUDIO3D_RESOURCES));
    private static final int TIME_RESOLUTION_MS =
            java.lang.Math.max(1, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.AUDIO3D_TIME_RESOLUTION_MS));
    private static final Object RESOURCE_LOCK = new Object();
    private static int allocatedResources;

    /** Mode constant indicating that the 3D environment is controlled by sound data (=1). */
    public static final int MODE_CONTROL_BY_DATA = 1;
    /** Mode constant indicating that the 3D environment is controlled by the application (=2). */
    public static final int MODE_CONTROL_BY_APP = 2;
    /** Event type indicating that sound-motion movement completed (=1). */
    public static final int SOUND_MOTION_COMPLETE = 1;

    private final AudioPresenter presenter;
    private boolean enabled;
    private int mode = -1;
    private int reservedResources;
    private Audio3DListener listener;
    private Audio3DLocalization localization;

    Audio3D() {
        this(null);
    }

    Audio3D(AudioPresenter presenter) {
        this.presenter = presenter;
    }

    /**
     * Gets the number of resources that the terminal has for controlling 3D
     * sound.
     * This method always returns the same value on the same terminal.
     *
     * @return the number of resources for controlling 3D sound
     */
    public static int getResources() {
        return TOTAL_RESOURCES;
    }

    /**
     * Gets the number of resources for controlling 3D sound that are currently
     * unused.
     *
     * @return the number of unused 3D-sound-control resources
     */
    public static int getFreeResources() {
        synchronized (RESOURCE_LOCK) {
            return java.lang.Math.max(0, TOTAL_RESOURCES - allocatedResources);
        }
    }

    /**
     * Gets the time resolution related to 3D localization settings.
     * This returns the recommended interval, in milliseconds, until the next
     * 3D localization setting should be applied after one localization setting
     * has been made for this object.
     *
     * @return the time resolution in milliseconds
     */
    public int getTimeResolution() {
        return TIME_RESOLUTION_MS;
    }

    /**
     * Enables control of 3D sound.
     *
     * @param mode the 3D-sound-control mode; specify either
     *             {@link #MODE_CONTROL_BY_DATA} or {@link #MODE_CONTROL_BY_APP}
     * @param resources the number of resources to secure for 3D sound control
     * @throws UIException if surround is explicitly enabled, 3D control is
     *         already enabled, audio playback is in progress, or resources
     *         cannot be secured
     * @throws IllegalArgumentException if {@code mode} is invalid or
     *         {@code resources} is not positive
     */
    public void enable(int mode, int resources) {
        if (mode != MODE_CONTROL_BY_DATA && mode != MODE_CONTROL_BY_APP) {
            throw new IllegalArgumentException("mode");
        }
        if (resources <= 0) {
            throw new IllegalArgumentException("resources");
        }
        if (PhoneSystem.getAttribute(PhoneSystem.DEV_AUDIO_SURROUND) == PhoneSystem.ATTR_SURROUND_ON) {
            throw new UIException(UIException.ILLEGAL_STATE);
        }
        if (enabled || (presenter != null && presenter.isPlaying())) {
            throw new UIException(UIException.ILLEGAL_STATE);
        }

        int actualResources = mode == MODE_CONTROL_BY_APP ? 1 : resources;
        synchronized (RESOURCE_LOCK) {
            if (actualResources > TOTAL_RESOURCES - allocatedResources) {
                throw new UIException(UIException.NO_RESOURCES);
            }
            allocatedResources += actualResources;
        }

        this.enabled = true;
        this.mode = mode;
        this.reservedResources = actualResources;
    }

    /**
     * Enables control of 3D sound.
     * This is a convenience method for {@link #enable(int, int)}.
     *
     * @param mode the 3D-sound-control mode; specify either
     *             {@link #MODE_CONTROL_BY_DATA} or {@link #MODE_CONTROL_BY_APP}
     * @throws NullPointerException if {@code mode} is
     *         {@link #MODE_CONTROL_BY_DATA} and no media sound is set on the
     *         audio presenter
     * @throws UIException if the current media sound is not in the use state or
     *         if 3D sound control cannot be enabled
     * @throws IllegalArgumentException if {@code mode} is invalid
     */
    public void enable(int mode) {
        if (mode == MODE_CONTROL_BY_DATA) {
            if (!(presenter != null && presenter.getMediaResource() instanceof MediaSound sound)) {
                throw new NullPointerException("mediaSound");
            }
            if (sound instanceof MediaManager.AbstractMediaResource tracked && !tracked.isUsed()) {
                throw new UIException(UIException.ILLEGAL_STATE);
            }
            int resources = 0;
            String value = sound.getProperty(MediaSound.AUDIO_3D_RESOURCES);
            if (value != null) {
                try {
                    resources = Integer.parseInt(value);
                } catch (NumberFormatException ignored) {
                    resources = 0;
                }
            }
            resources = java.lang.Math.min(resources, getFreeResources());
            if (resources <= 0) {
                return;
            }
            enable(mode, resources);
            return;
        }
        if (mode != MODE_CONTROL_BY_APP) {
            throw new IllegalArgumentException("mode");
        }
        enable(mode, 1);
    }

    /**
     * Disables control of 3D sound.
     * If 3D sound control is already disabled, this method does nothing.
     *
     * @throws UIException if audio playback is in progress
     */
    public void disable() {
        if (presenter != null && presenter.isPlaying()) {
            throw new UIException(UIException.ILLEGAL_STATE);
        }
        if (!enabled) {
            return;
        }
        synchronized (RESOURCE_LOCK) {
            allocatedResources = java.lang.Math.max(0, allocatedResources - reservedResources);
        }
        enabled = false;
        reservedResources = 0;
        mode = -1;
    }

    /**
     * Gets whether control of 3D sound is enabled.
     *
     * @return {@code true} if control of 3D sound is enabled, or
     *         {@code false} otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Registers a listener.
     * Only one listener can be registered with a 3D audio object. If this
     * method is called multiple times, only the last registered listener is
     * effective. Specifying {@code null} removes the current listener
     * registration.
     *
     * @param listener the listener to register
     */
    public void setListener(Audio3DListener listener) {
        this.listener = listener;
    }

    /**
     * Sets the 3D localization in the 3D environment used for 3D sound
     * control.
     * By default, localization is set so that both the virtual sound source
     * and the listener are at the origin of the coordinate system.
     *
     * @param localization the 3D localization to set
     * @throws UIException if this object has not been enabled for control by
     *         the application
     * @throws NullPointerException if {@code localization} is {@code null}
     * @throws IllegalArgumentException if {@code localization} is not a system
     *         provided 3D localization object
     */
    public void setLocalization(Audio3DLocalization localization) {
        if (!enabled || mode != MODE_CONTROL_BY_APP) {
            throw new UIException(UIException.ILLEGAL_STATE);
        }
        if (localization == null) {
            throw new NullPointerException("localization");
        }
        if (!(localization instanceof SoundPosition || localization instanceof SoundMotion)) {
            throw new IllegalArgumentException("localization");
        }
        this.localization = localization;
    }
}
