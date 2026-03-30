package com.nttdocomo.opt.ui;

import com.nttdocomo.ui.Image;

/**
 * Provides access to the optional sub display.
 */
public class SubDisplay {
    private static final int WIDTH = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.SUB_DISPLAY_WIDTH);
    private static final int HEIGHT = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.SUB_DISPLAY_HEIGHT);
    private static volatile Image image;

    /**
     * Returns the width of the sub display in pixels.
     *
     * @return the sub-display width
     */
    public static int getWidth() {
        return WIDTH;
    }

    /**
     * Returns the height of the sub display in pixels.
     *
     * @return the sub-display height
     */
    public static int getHeight() {
        return HEIGHT;
    }

    /**
     * Returns whether the sub display is color.
     *
     * @return {@code true} if the sub display is color
     */
    public static boolean isColor() {
        return WIDTH > 0 && HEIGHT > 0;
    }

    /**
     * Returns the number of colors or gray levels.
     *
     * @return the number of colors or gray levels
     */
    public static int numColors() {
        return isColor() ? (1 << 24) : 0;
    }

    /**
     * Sets the image displayed on the sub display.
     *
     * @param image the image to show, or {@code null} to clear it
     */
    public static void setImage(Image image) {
        SubDisplay.image = image;
    }

    static Image image() {
        return image;
    }
}
