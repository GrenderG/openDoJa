package com.nttdocomo.ui;

public class Audio3D {
    public static final int MODE_CONTROL_BY_DATA = 0;
    public static final int MODE_CONTROL_BY_APP = 1;
    public static final int SOUND_MOTION_COMPLETE = 1;

    private boolean enabled;
    private Audio3DListener listener;
    private Audio3DLocalization localization;

    Audio3D() {
    }

    public static int getResources() {
        return 1;
    }

    public static int getFreeResources() {
        return 1;
    }

    public int getTimeResolution() {
        return 1;
    }

    public void enable(int mode, int option) {
        enabled = true;
    }

    public void enable(int mode) {
        enabled = true;
    }

    public void disable() {
        enabled = false;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setListener(Audio3DListener listener) {
        this.listener = listener;
    }

    public void setLocalization(Audio3DLocalization localization) {
        this.localization = localization;
    }
}
