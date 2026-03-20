package com.nttdocomo.ui;

import opendoja.host.DesktopSurface;
import opendoja.host.DoJaRuntime;

import javax.swing.JOptionPane;

public abstract class Canvas extends Frame {
    public static final int IME_COMMITTED = 0;
    public static final int IME_CANCELED = 1;

    private DesktopSurface surface;

    public Canvas() {
    }

    public Graphics getGraphics() {
        ensureSurface(Display.getWidth(), Display.getHeight());
        surface.setBackgroundColor(backgroundColor());
        surface.setRepaintHook(() -> {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.notifySurfaceFlush(this);
            }
        });
        return new Graphics(surface);
    }

    public abstract void paint(Graphics g);

    public void repaint() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null) {
            runtime.requestRender(this);
        }
    }

    public void repaint(int x, int y, int width, int height) {
        repaint();
    }

    public void processEvent(int type, int param) {
    }

    public int getKeypadState() {
        DoJaRuntime runtime = DoJaRuntime.current();
        return runtime == null ? 0 : runtime.keypadState();
    }

    public int getKeypadState(int group) {
        return getKeypadState();
    }

    public void imeOn(String title, int maxChars, int mode) {
        imeOn(title, maxChars, mode, 0);
    }

    public void imeOn(String title, int maxChars, int mode, int displayMode) {
        String result = JOptionPane.showInputDialog(null, title == null ? "" : title);
        if (result == null) {
            processIMEEvent(IME_CANCELED, null);
            return;
        }
        if (maxChars > 0 && result.length() > maxChars) {
            result = result.substring(0, maxChars);
        }
        processIMEEvent(IME_COMMITTED, result);
    }

    public void processIMEEvent(int type, String text) {
    }

    @Override
    public void setBackground(int color) {
        super.setBackground(color);
        if (surface != null) {
            surface.setBackgroundColor(color);
        }
    }

    DesktopSurface surface() {
        return surface;
    }

    void ensureSurface(int width, int height) {
        if (surface == null) {
            surface = new DesktopSurface(width, height);
        } else {
            surface.resize(width, height);
        }
        surface.setBackgroundColor(backgroundColor());
    }
}
