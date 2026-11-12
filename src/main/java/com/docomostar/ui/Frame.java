package com.docomostar.ui;

/** STAR frame hierarchy, backed by a host frame for presentation and input. */
public abstract class Frame {
    /** Host integration: recover the STAR identity of a displayed backend frame. */
    public interface Host { Frame starFrame(); }
    public static final int SOFT_KEY_1 = 0;
    public static final int SOFT_KEY_2 = 1;
    public static final int SOFT_KEY_3 = 2;
    public static final int SOFT_KEY_4 = 3;
    public static final int ARROW_UP = 2;
    public static final int ARROW_DOWN = 8;
    public static final int ARROW_LEFT = 1;
    public static final int ARROW_RIGHT = 4;
    public static final int SELECT_KEY = 4;

    protected com.nttdocomo.ui.Frame hostFrame;

    public Frame() { }

    public final int getWidth() { return Display.getWidth(); }
    public final int getHeight() { return Display.getHeight(); }
    public void setBackground(int color) { hostFrame.setBackground(color); }
    public void setSoftLabel(int key, String caption) { hostFrame.setSoftLabel(key, caption); }
    public void setSoftLabelVisible(boolean visible) { hostFrame.setSoftLabelVisible(visible); }
}
