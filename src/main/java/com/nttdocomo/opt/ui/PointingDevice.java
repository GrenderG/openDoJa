package com.nttdocomo.opt.ui;

/**
 * Represents the optional pointing device.
 */
public class PointingDevice {
    /** Constant indicating mouse mode (=0). */
    public static final int MODE_MOUSE = 0;
    /** Constant indicating joystick mode (=1). */
    public static final int MODE_JOYSTICK = 1;
    /** Maximum X-axis direction component (=127). */
    public static final int MAX_DIRECTION_X = 127;
    /** Minimum X-axis direction component (=-128). */
    public static final int MIN_DIRECTION_X = -128;
    /** Maximum Y-axis direction component (=127). */
    public static final int MAX_DIRECTION_Y = 127;
    /** Minimum Y-axis direction component (=-128). */
    public static final int MIN_DIRECTION_Y = -128;
    private static final boolean AVAILABLE = !opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.POINTING_DEVICE_UNAVAILABLE);
    private static final int CONFIGURED_MAX_DIRECTION_Z =
            Math.max(0, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.POINTING_DEVICE_MAX_DIRECTION_Z));

    private static boolean enabled;
    private static boolean visible = true;
    private static int mode = MODE_MOUSE;
    private static int x;
    private static int y;
    private static int directionX;
    private static int directionY;
    private static int directionZ;

    /**
     * Applications cannot create this object directly.
     */
    protected PointingDevice() {
    }

    /**
     * Enables or disables the pointing device.
     *
     * @param b {@code true} to enable it
     */
    public static void setEnabled(boolean b) {
        enabled = b;
    }

    /**
     * Returns whether the pointing device is enabled.
     *
     * @return {@code true} if enabled
     */
    public static boolean isEnabled() {
        return enabled;
    }

    /**
     * Returns the current x coordinate.
     *
     * @return the x coordinate
     */
    public static int getX() {
        return AVAILABLE ? x : -1;
    }

    /**
     * Returns the current y coordinate.
     *
     * @return the y coordinate
     */
    public static int getY() {
        return AVAILABLE ? y : -1;
    }

    /**
     * Sets the pointer position.
     *
     * @param x the x coordinate
     * @param y the y coordinate
     */
    public static void setPosition(int x, int y) {
        if (mode != MODE_MOUSE) {
            return;
        }
        directionX = clampDirection(x - PointingDevice.x);
        directionY = clampDirection(y - PointingDevice.y);
        PointingDevice.x = x;
        PointingDevice.y = y;
    }

    /**
     * Sets whether the pointer is visible.
     *
     * @param b {@code true} to make the pointer visible
     */
    public static void setVisible(boolean b) {
        if (mode != MODE_MOUSE) {
            return;
        }
        visible = b;
    }

    /**
     * Returns whether the pointing device is available.
     *
     * @return {@code true} on the desktop host
     */
    public static boolean isAvailable() {
        return AVAILABLE;
    }

    /**
     * Sets the pointing-device mode.
     *
     * @param mode the mode to set
     */
    public static final void setMode(int mode) {
        if (mode != MODE_MOUSE && mode != MODE_JOYSTICK) {
            throw new IllegalArgumentException("mode");
        }
        PointingDevice.mode = mode;
        if (mode == MODE_MOUSE) {
            directionZ = 0;
        }
    }

    /**
     * Returns the pointing-device mode.
     *
     * @return the current mode
     */
    public static final int getMode() {
        return mode;
    }

    /**
     * Returns the x-axis tilt.
     *
     * @return the x-axis tilt
     */
    public static int getDirectionX() {
        if (!AVAILABLE || !enabled || mode != MODE_JOYSTICK || getDirectionZ() == 0) {
            return 0;
        }
        return directionX;
    }

    /**
     * Returns the y-axis tilt.
     *
     * @return the y-axis tilt
     */
    public static int getDirectionY() {
        if (!AVAILABLE || !enabled || mode != MODE_JOYSTICK || getDirectionZ() == 0) {
            return 0;
        }
        return directionY;
    }

    /**
     * Returns the z-axis tilt.
     *
     * @return the z-axis tilt
     */
    public static int getDirectionZ() {
        if (!AVAILABLE || !enabled || mode != MODE_JOYSTICK) {
            return 0;
        }
        return java.lang.Math.max(0, java.lang.Math.min(CONFIGURED_MAX_DIRECTION_Z, directionZ));
    }

    /**
     * Returns the maximum z-axis tilt.
     *
     * @return the maximum z-axis tilt
     */
    public static int getMaxDirectionZ() {
        return mode == MODE_JOYSTICK ? CONFIGURED_MAX_DIRECTION_Z : 0;
    }

    static boolean isVisible() {
        return visible;
    }

    private static int clampDirection(int value) {
        return java.lang.Math.max(MIN_DIRECTION_X, java.lang.Math.min(MAX_DIRECTION_X, value));
    }
}
