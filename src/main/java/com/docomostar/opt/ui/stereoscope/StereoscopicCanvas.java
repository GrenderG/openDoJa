package com.docomostar.opt.ui.stereoscope;

/** STAR stereoscopic canvas presented as a single eye on the desktop display. */
public abstract class StereoscopicCanvas extends com.docomostar.ui.Canvas {
    public StereoscopicCanvas() { this(false); }
    public StereoscopicCanvas(boolean stereo) { super(); }
}
