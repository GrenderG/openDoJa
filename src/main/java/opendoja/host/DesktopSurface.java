package opendoja.host;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

public final class DesktopSurface {
    private BufferedImage image;
    private int backgroundColor = 0xFF000000;
    private Consumer<BufferedImage> repaintHook;
    private float[] depthBuffer;
    private boolean depthFrameActive;
    private long syncUnlockIntervalNanos;
    private long lastFlushNanos;

    public DesktopSurface(int width, int height) {
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    public void resize(int width, int height) {
        if (image.getWidth() == width && image.getHeight() == height) {
            return;
        }
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resized.createGraphics();
        g2.drawImage(image, 0, 0, null);
        g2.dispose();
        this.image = resized;
        this.depthBuffer = null;
        this.depthFrameActive = false;
    }

    public BufferedImage image() {
        return image;
    }

    public int width() {
        return image.getWidth();
    }

    public int height() {
        return image.getHeight();
    }

    public int backgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public void setRepaintHook(Consumer<BufferedImage> repaintHook) {
        this.repaintHook = repaintHook;
    }

    public boolean hasRepaintHook() {
        return repaintHook != null;
    }

    public void setSyncUnlockIntervalNanos(long syncUnlockIntervalNanos) {
        this.syncUnlockIntervalNanos = Math.max(0L, syncUnlockIntervalNanos);
        if (this.syncUnlockIntervalNanos == 0L) {
            this.lastFlushNanos = 0L;
        }
    }

    public synchronized float[] depthBufferForFrame() {
        int pixelCount = image.getWidth() * image.getHeight();
        if (depthBuffer == null || depthBuffer.length != pixelCount) {
            depthBuffer = new float[pixelCount];
            depthFrameActive = false;
        }
        if (!depthFrameActive) {
            Arrays.fill(depthBuffer, Float.NEGATIVE_INFINITY);
            depthFrameActive = true;
        }
        return depthBuffer;
    }

    public synchronized void endDepthFrame() {
        depthFrameActive = false;
    }

    public void flush(BufferedImage presentedFrame) {
        if (syncUnlockIntervalNanos > 0L && repaintHook != null) {
            long now = System.nanoTime();
            long elapsed = now - lastFlushNanos;
            if (lastFlushNanos != 0L && elapsed < syncUnlockIntervalNanos) {
                long remainingNanos = syncUnlockIntervalNanos - elapsed;
                try {
                    java.util.concurrent.locks.LockSupport.parkNanos(remainingNanos);
                } finally {
                    now = System.nanoTime();
                }
            }
            lastFlushNanos = now;
        }
        endDepthFrame();
        if (repaintHook != null) {
            repaintHook.accept(presentedFrame);
        }
    }

    @Override
    public String toString() {
        return "DesktopSurface{" + image.getWidth() + "x" + image.getHeight() + ", background=" + backgroundColor + "}";
    }
}
