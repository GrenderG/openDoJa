package opendoja.host;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

public final class DesktopSurface {
    private static final long PRESENT_SYNC_INTERVAL_NANOS = 16_000_000L;
    private BufferedImage image;
    private final BufferedImage[] presentationBuffers = new BufferedImage[2];
    private int presentationBufferIndex;
    private int backgroundColor = 0xFF000000;
    private Consumer<BufferedImage> repaintHook;
    private float[] depthBuffer;
    private boolean depthFrameActive;
    private long nextRenderSyncNanos;
    private boolean openGlesSeen;

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
        this.presentationBuffers[0] = null;
        this.presentationBuffers[1] = null;
        this.presentationBufferIndex = 0;
        this.depthBuffer = null;
        this.depthFrameActive = false;
        this.nextRenderSyncNanos = 0L;
        this.openGlesSeen = false;
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

    public synchronized void markOpenGlesActivity() {
        openGlesSeen = true;
    }

    public synchronized boolean hasOpenGlesActivity() {
        return openGlesSeen;
    }

    public synchronized BufferedImage copyForPresentation() {
        int width = image.getWidth();
        int height = image.getHeight();
        presentationBufferIndex ^= 1;
        BufferedImage copy = presentationBuffers[presentationBufferIndex];
        if (copy == null || copy.getWidth() != width || copy.getHeight() != height) {
            copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
            presentationBuffers[presentationBufferIndex] = copy;
        }
        // Presentation must be a stable snapshot while the app continues drawing into `image`.
        Graphics2D g2 = copy.createGraphics();
        try {
            g2.drawImage(image, 0, 0, null);
        } finally {
            g2.dispose();
        }
        return copy;
    }

    public synchronized void waitForRenderSync(long intervalNanos) {
        if (intervalNanos <= 0L || repaintHook == null) {
            return;
        }
        long now = System.nanoTime();
        long target = nextRenderSyncNanos == 0L ? now + intervalNanos : nextRenderSyncNanos + intervalNanos;
        if (target > now) {
            LockSupport.parkNanos(target - now);
            nextRenderSyncNanos = target;
            return;
        }
        nextRenderSyncNanos = now;
    }

    public void flush(BufferedImage presentedFrame) {
        flush(presentedFrame, true);
    }

    public void flush(BufferedImage presentedFrame, boolean paced) {
        if (paced) {
            // The official emulator exposes a separate graphics sync interval of `16000us`.
            // Keep the paced direct-present path on that cadence, while callers that already
            // performed an explicit sync wait pass `paced=false` to avoid double throttling.
            waitForRenderSync(PRESENT_SYNC_INTERVAL_NANOS);
        }
        present(presentedFrame);
    }

    private void present(BufferedImage presentedFrame) {
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
