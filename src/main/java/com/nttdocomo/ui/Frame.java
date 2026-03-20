package com.nttdocomo.ui;

public abstract class Frame {
    public static final int SOFT_KEY_1 = 0;
    public static final int SOFT_KEY_2 = 1;

    private int background = Graphics.getColorOfName(Graphics.WHITE);
    private final String[] softLabels = new String[2];
    private boolean softLabelVisible = true;

    public Frame() {
    }

    public final int getHeight() {
        return Display.getHeight();
    }

    public final int getWidth() {
        return Display.getWidth();
    }

    public void setBackground(int color) {
        this.background = color;
    }

    public void setSoftLabel(int key, String caption) {
        if (key >= 0 && key < softLabels.length) {
            softLabels[key] = caption;
        }
    }

    public void setSoftLabelVisible(boolean visible) {
        this.softLabelVisible = visible;
    }

    int backgroundColor() {
        return background;
    }

    String softLabel(int key) {
        return key >= 0 && key < softLabels.length ? softLabels[key] : null;
    }

    boolean softLabelVisible() {
        return softLabelVisible;
    }
}
