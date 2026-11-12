package com.docomostar.ui.flashplayer;

import com.docomostar.ui.Frame;
import com.docomostar.ui.SoftKeyListener;
import com.docomostar.ui.UIException;
import opendoja.host.DoJaRuntime;
import opendoja.host.flash.FlashMovie;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/** Initial Flash support: real vector/bitmap timeline playback, without ActionScript execution. */
public class FlashPlayerPane extends Frame {
    public static final int QUALITY_LOW = 0, QUALITY_MEDIUM = 1, QUALITY_HIGH = 2;
    private final HostCanvas canvas = new HostCanvas();
    private final DoJaRuntime runtime;
    private FlashMovie movie;
    private ScheduledFuture<?> task;
    private volatile com.nttdocomo.ui.Image image;
    private volatile SoftKeyListener softKeys;
    private boolean loop = true, playing;
    private int quality = QUALITY_HIGH, frame;
    private long startedAt, elapsedBeforeStart;

    public FlashPlayerPane() {
        hostFrame = canvas;
        runtime = DoJaRuntime.current();
        if (runtime != null) runtime.registerShutdownResource(this::stop);
    }
    public synchronized void setLoop(boolean loop) { this.loop = loop; }
    public synchronized boolean getLoop() { return loop; }
    public synchronized void setQuality(int quality) {
        if (quality < QUALITY_LOW || quality > QUALITY_HIGH) throw new IllegalArgumentException("quality");
        this.quality = quality;
    }
    public synchronized int getQuality() { return quality; }
    public String getPlayerVersion() { return "openDoJa SWF timeline (FFDec 26.4.0)"; }
    public void setSoftKeyListener(SoftKeyListener listener) { softKeys = listener; }
    public synchronized void set(byte[] data) {
        java.util.Objects.requireNonNull(data, "data");
        try {
            FlashMovie loaded = new FlashMovie(data.clone());
            stop(); movie = loaded; frame = 1; elapsedBeforeStart = 0;
            image = movie.render(0, 0, quality != QUALITY_LOW); canvas.repaint();
        } catch (java.io.IOException failure) {
            var translated = new UIException(UIException.UNSUPPORTED_FORMAT, failure.getMessage());
            translated.initCause(failure); throw translated;
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            var translated = new UIException(UIException.UNDEFINED, "SWF loading interrupted");
            translated.initCause(failure); throw translated;
        }
    }
    public synchronized long getFrameInterval() { requireMovie(); return movie.frameIntervalMillis(); }
    public synchronized int getFrameNumber() { requireMovie(); return frame; }
    public synchronized int getNumberOfFrames() { requireMovie(); return movie.frameCount(); }
    public synchronized boolean isPlaying() { return playing; }
    public synchronized void play() {
        requireMovie();
        if (playing) return;
        if (runtime == null) throw new UIException(UIException.ILLEGAL_STATE, "No application runtime");
        if (frame == movie.frameCount()) { frame = 1; elapsedBeforeStart = 0; }
        startedAt = System.nanoTime(); playing = true;
        task = runtime.scheduler().scheduleWithFixedDelay(this::tick, 0, movie.frameIntervalMillis(), TimeUnit.MILLISECONDS);
    }
    public synchronized void pause() {
        if (playing) elapsedBeforeStart += System.nanoTime() - startedAt;
        cancelTask();
    }
    public synchronized void stop() { cancelTask(); elapsedBeforeStart = 0; }
    public synchronized void restart() {
        requireMovie(); stop(); frame = 1; elapsedBeforeStart = 0; play();
    }
    private synchronized void tick() {
        if (!playing) return;
        long elapsed = elapsedBeforeStart + System.nanoTime() - startedAt;
        long index = elapsed / TimeUnit.MILLISECONDS.toNanos(movie.frameIntervalMillis());
        int count = movie.frameCount();
        int next = loop ? (int)(index % count) : (int)Math.min(count - 1L, index);
        image = movie.render(next, (int)Math.min(Integer.MAX_VALUE, index), quality != QUALITY_LOW);
        frame = next + 1; canvas.repaint();
        if (!loop && index >= count - 1L) cancelTask();
    }
    private void cancelTask() {
        playing = false;
        if (task != null) { task.cancel(false); task = null; }
    }
    private void requireMovie() {
        if (movie == null) throw new UIException(UIException.ILLEGAL_STATE, "No SWF loaded");
    }
    private final class HostCanvas extends com.nttdocomo.ui.Canvas implements Frame.Host {
        @Override public Frame starFrame() { return FlashPlayerPane.this; }
        @Override public void paint(com.nttdocomo.ui.Graphics graphics) {
            var current = image;
            if (current != null) graphics.drawImage(current, 0, 0);
        }
        @Override public void processEvent(int type, int key) {
            var listener = softKeys;
            if (listener == null) return;
            int index = switch (key) {
                case com.nttdocomo.ui.Display.KEY_SOFT1 -> 0;
                case com.nttdocomo.ui.Display.KEY_SOFT2 -> 1;
                case com.docomostar.ui.Display.KEY_SOFT3 -> 2;
                case com.docomostar.ui.Display.KEY_SOFT4 -> 3;
                default -> -1;
            };
            if (index < 0) return;
            if (type == com.nttdocomo.ui.Display.KEY_PRESSED_EVENT) listener.softKeyPressed(index);
            else if (type == com.nttdocomo.ui.Display.KEY_RELEASED_EVENT) listener.softKeyReleased(index);
        }
    }
}
