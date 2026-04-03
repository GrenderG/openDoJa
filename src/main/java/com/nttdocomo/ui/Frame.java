package com.nttdocomo.ui;

/**
 * Provides the display surface of an application.
 * This class defines the interface shared by objects displayed on the screen.
 * Events are delivered to instances of subclasses of this class only while the
 * frame is in the displayed state.
 */
public abstract class Frame {
    /**
     * Soft key 1 (=0).
     * Soft key 1 is the left soft key.
     */
    public static final int SOFT_KEY_1 = 0;
    /**
     * Soft key 2 (=1).
     * Soft key 2 is the right soft key.
     */
    public static final int SOFT_KEY_2 = 1;
    private int background = Graphics.getColorOfName(Graphics.WHITE);
    private final String[] softLabels = new String[2];
    private boolean softLabelVisible = true;

    /**
     * Applications cannot directly create instances of this class.
     */
    public Frame() {
    }

    /**
     * Gets the height of the frame.
     *
     * @return the frame height in pixels
     */
    public final int getHeight() {
        return Display.getHeight();
    }

    /**
     * Gets the width of the frame.
     *
     * @return the frame width in pixels
     */
    public final int getWidth() {
        return Display.getWidth();
    }

    /**
     * Sets the background color.
     *
     * @param color the integer value that represents the background color
     */
    public void setBackground(int color) {
        this.background = color;
    }

    /**
     * Sets the label string of a soft key.
     *
     * @param key the soft-key number
     * @param caption the label string to set
     */
    public void setSoftLabel(int key, String caption) {
        if (key >= 0 && key < softLabels.length) {
            softLabels[key] = caption;
        }
    }

    /**
     * Sets whether the soft-key labels are visible.
     *
     * @param visible {@code true} to show the labels, or {@code false} to hide
     *                them
     */
    public void setSoftLabelVisible(boolean visible) {
        this.softLabelVisible = visible;
    }

    /**
     * Gets soft Label.
     */
    public String getSoftLabel(int key) {
        return key >= 0 && key < softLabels.length ? softLabels[key] : null;
    }

    /**
     * Returns whether soft Label Visible.
     */
    public boolean isSoftLabelVisible() {
        return softLabelVisible;
    }

    /**
     * Performs process Soft Key Event.
     */
    public void processSoftKeyEvent(int type, int key) {
        int ignoredType = type;
        int ignoredKey = key;
    }

    int backgroundColor() {
        return background;
    }
}
