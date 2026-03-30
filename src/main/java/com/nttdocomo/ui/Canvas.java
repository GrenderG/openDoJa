package com.nttdocomo.ui;

import opendoja.host.DesktopSurface;
import opendoja.host.DoJaRuntime;

import javax.swing.JOptionPane;

/**
 * Defines the display surface for the low-level API.
 * A canvas is the frame class used by the low-level API and does not provide a
 * scrolling function.
 */
public abstract class Canvas extends Frame {
    private static final long DIRECT_SYNC_UNLOCK_INTERVAL_NANOS =
            java.lang.Math.max(0L, opendoja.host.OpenDoJaLaunchArgs.getLong(opendoja.host.OpenDoJaLaunchArgs.SYNC_UNLOCK_INTERVAL_MS)) * 1_000_000L;
    /**
     * Indicates that IME input has been committed (=0).
     * This value is passed as the {@code type} argument of
     * {@link #processIMEEvent(int, String)}.
     */
    public static final int IME_COMMITTED = 0;
    /**
     * Indicates that IME input has been canceled (=1).
     * This value is passed as the {@code type} argument of
     * {@link #processIMEEvent(int, String)}.
     */
    public static final int IME_CANCELED = 1;

    private DesktopSurface surface;
    private volatile boolean directGraphicsMode;

    /**
     * Creates a canvas object.
     */
    public Canvas() {
    }

    /**
     * Gets the graphics object used to draw on the canvas.
     *
     * @return the graphics object for the canvas
     */
    public Graphics getGraphics() {
        DoJaRuntime runtime = DoJaRuntime.current();
        // Some titles cache a Canvas Graphics instance during construction and still rely on
        // repaint-managed paint(Graphics) once the Canvas becomes current. Only mark direct mode
        // when the Canvas is already the active frame and the app is intentionally driving draws
        // through getGraphics() at runtime.
        if (runtime != null && runtime.getCurrentFrame() == this) {
            directGraphicsMode = true;
        }
        return createGraphics();
    }

    Graphics runtimeGraphics() {
        return createGraphics();
    }

    boolean directGraphicsMode() {
        return directGraphicsMode;
    }

    private Graphics createGraphics() {
        ensureSurface(Display.getWidth(), Display.getHeight());
        surface.setBackgroundColor(backgroundColor());
        surface.setRepaintHook(frame -> {
            DoJaRuntime runtime = DoJaRuntime.current();
            if (runtime != null) {
                runtime.notifySurfaceFlush(this, frame);
            }
        });
        // The original emulator/runtime exposes a sync-unlock interval on the frame path. Direct
        // Canvas loops like Nose Hair rely on unlock(true) pacing their gameplay loop instead of
        // sleeping explicitly. Those loops advance in-game time by 9 centiseconds per frame, so a
        // 90 ms default matches the bundled sample behavior and the native sync-unlock concept.
        surface.setSyncUnlockIntervalNanos(directGraphicsMode ? DIRECT_SYNC_UNLOCK_INTERVAL_NANOS : 0L);
        return Graphics.createPlatformGraphics(surface);
    }

    /**
     * Paints the canvas.
     *
     * @param g the graphics object to use for painting
     */
    public abstract void paint(Graphics g);

    /**
     * Requests that the whole canvas be repainted.
     */
    public void repaint() {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null) {
            runtime.requestRender(this);
        }
    }

    /**
     * Requests that a rectangular region of the canvas be repainted.
     *
     * @param x the X coordinate of the region
     * @param y the Y coordinate of the region
     * @param width the region width
     * @param height the region height
     */
    public void repaint(int x, int y, int width, int height) {
        repaint();
    }

    /**
     * Called when a low-level event is delivered to the canvas.
     * The default implementation does nothing.
     *
     * @param type the event type
     * @param param the event parameter
     */
    public void processEvent(int type, int param) {
        return;
    }

    @Override
    public void processSoftKeyEvent(int type, int key) {
        int dojaKey = switch (key) {
            case SOFT_KEY_1 -> Display.KEY_SOFT1;
            case SOFT_KEY_2 -> Display.KEY_SOFT2;
            default -> -1;
        };
        if (dojaKey < 0) {
            return;
        }
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null) {
            runtime.dispatchSyntheticKey(dojaKey, type);
        }
    }

    /**
     * Gets the keypad state.
     *
     * @return the keypad state bit mask
     */
    public int getKeypadState() {
        DoJaRuntime runtime = DoJaRuntime.current();
        return runtime == null ? 0 : runtime.keypadState();
    }

    /**
     * Gets the keypad state for the specified key group.
     *
     * @param group the key group to inspect
     * @return the keypad state bit mask for the group
     */
    public int getKeypadState(int group) {
        return getKeypadState();
    }

    /**
     * Starts IME on the canvas.
     *
     * @param title the title text
     * @param maxChars the maximum number of characters
     * @param mode the input mode
     */
    public void imeOn(String title, int maxChars, int mode) {
        imeOn(title, maxChars, mode, 0);
    }

    /**
     * Starts IME on the canvas with an input-size limit.
     *
     * @param title the title text
     * @param maxChars the maximum number of characters
     * @param mode the input mode
     * @param displayMode the display mode
     */
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

    /**
     * Called when an IME event is delivered to the canvas.
     * The default implementation does nothing.
     *
     * @param type the IME event type
     * @param text the committed text, or {@code null}
     */
    public void processIMEEvent(int type, String text) {
        return;
    }

    /**
     * Sets the background color.
     *
     * @param color the integer value that represents the background color
     */
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
