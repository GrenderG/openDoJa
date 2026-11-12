package com.docomostar.ui;

/** Adapts STAR callbacks to the existing host canvas and its shared surface. */
public abstract class Canvas extends Frame {
    public static final int IME_COMMITTED = 0;
    public static final int IME_CANCELED = 1;
    public static final int NUMBER = 0;
    public static final int ALPHA = 1;
    public static final int KANA = 2;
    public static final int DISPLAY_ANY = 0;
    public static final int DISPLAY_PASSWORD = 1;
    private final HostCanvas host = new HostCanvas();
    private boolean initialized;

    public Canvas() { hostFrame = host; }

    public Graphics getGraphics() { return Graphics.wrap(host.getGraphics()); }
    public void init(Graphics graphics) { }
    public abstract void paint(Graphics graphics);
    public void repaint() { host.repaint(); }
    public void repaint(int x, int y, int width, int height) { host.repaint(x, y, width, height); }
    public int getKeypadState() { return host.getKeypadState(); }
    public int getKeypadState(int group) { return host.getKeypadState(group); }
    public void processEvent(int type, int param) { }
    public void processIMEEvent(int type, String text) { }
    public void imeOn(String text, int displayMode, int inputMode) { host.imeOn(text, displayMode, inputMode); }
    public void imeOn(String text, int displayMode, int inputMode, int inputSize) {
        host.imeOn(text, displayMode, inputMode, inputSize);
    }

    final class HostCanvas extends com.nttdocomo.ui.Canvas implements Frame.Host {
        @Override public Frame starFrame() { return Canvas.this; }
        Canvas owner() { return Canvas.this; }

        @Override public void paint(com.nttdocomo.ui.Graphics graphics) {
            Graphics starGraphics = Graphics.wrap(graphics);
            if (!initialized) {
                initialized = true;
                Canvas.this.init(starGraphics);
            }
            Canvas.this.paint(starGraphics);
        }
        @Override public void processEvent(int type, int param) { Canvas.this.processEvent(type, param); }
        @Override public void processIMEEvent(int type, String text) { Canvas.this.processIMEEvent(type, text); }
    }
}
