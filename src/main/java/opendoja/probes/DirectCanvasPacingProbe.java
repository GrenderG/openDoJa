package opendoja.probes;

import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Frame;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.IApplication;
import opendoja.host.DesktopLauncher;
import opendoja.host.DoJaRuntime;

import java.util.concurrent.TimeUnit;

/**
 * Verifies that direct-canvas frame pacing remains active while key delivery can
 * wake a paced frame wait promptly enough for responsive menu navigation.
 */
public final class DirectCanvasPacingProbe {
    private DirectCanvasPacingProbe() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        System.setProperty(opendoja.host.OpenDoJaLaunchArgs.SYNC_UNLOCK_INTERVAL_MS, "120");
        ProbeApp app = (ProbeApp) DesktopLauncher.launch(ProbeApp.class);
        try {
            long frame1 = app.canvas.awaitFrameStart(1);
            long frame2 = app.canvas.awaitFrameStart(2);
            double idleGapMs = nanosToMillis(frame2 - frame1);

            app.canvas.awaitFrameStart(3);
            Thread.sleep(20L);
            long dispatchStart = System.nanoTime();
            DoJaRuntime.current().dispatchSyntheticKey(Display.KEY_DOWN, Display.KEY_PRESSED_EVENT);
            long visibleAt = app.canvas.awaitRenderedSelection();
            double inputVisibleMs = nanosToMillis(visibleAt - dispatchStart);

            int burstStartFrame = app.canvas.frameCount();
            int[] stormKeys = {Display.KEY_LEFT, Display.KEY_RIGHT, Display.KEY_UP, Display.KEY_DOWN};
            for (int round = 0; round < 3; round++) {
                for (int key : stormKeys) {
                    DoJaRuntime.current().dispatchSyntheticKey(key, Display.KEY_PRESSED_EVENT);
                    Thread.sleep(3L);
                }
                for (int key : stormKeys) {
                    DoJaRuntime.current().dispatchSyntheticKey(key, Display.KEY_RELEASED_EVENT);
                    Thread.sleep(3L);
                }
            }
            app.canvas.awaitFrameCount(burstStartFrame + 8);
            double burstMinGapMs = app.canvas.minFrameGapMillis(burstStartFrame + 1, burstStartFrame + 8);

            int timerDrivenBurstStartFrame = app.canvas.frameCount();
            Thread timerStorm = new Thread(() -> {
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(350L);
                while (System.nanoTime() < deadline) {
                    DoJaRuntime runtime = DoJaRuntime.current();
                    if (runtime == null) {
                        return;
                    }
                    runtime.dispatchTimerEvent(app.canvas, 0);
                    try {
                        Thread.sleep(10L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }, "direct-canvas-pacing-probe-timer");
            timerStorm.setDaemon(true);
            timerStorm.start();
            for (int round = 0; round < 3; round++) {
                for (int key : stormKeys) {
                    DoJaRuntime.current().dispatchSyntheticKey(key, Display.KEY_PRESSED_EVENT);
                    Thread.sleep(3L);
                }
                for (int key : stormKeys) {
                    DoJaRuntime.current().dispatchSyntheticKey(key, Display.KEY_RELEASED_EVENT);
                    Thread.sleep(3L);
                }
            }
            timerStorm.join(TimeUnit.SECONDS.toMillis(2L));
            app.canvas.awaitFrameCount(timerDrivenBurstStartFrame + 4);
            double timerDrivenBurstMinGapMs =
                    app.canvas.minFrameGapMillis(timerDrivenBurstStartFrame + 1, timerDrivenBurstStartFrame + 4);

            System.out.println("idleFrameGapMs=" + formatMillis(idleGapMs)
                    + " inputVisibleMs=" + formatMillis(inputVisibleMs)
                    + " burstMinGapMs=" + formatMillis(burstMinGapMs)
                    + " timerDrivenBurstMinGapMs=" + formatMillis(timerDrivenBurstMinGapMs));

            if (idleGapMs < 80.0) {
                throw new IllegalStateException("Direct canvas pacing gap was unexpectedly short: " + idleGapMs + " ms");
            }
            if (inputVisibleMs >= 60.0) {
                throw new IllegalStateException("Input remained gated by the paced frame wait: " + inputVisibleMs + " ms");
            }
            if (burstMinGapMs < 12.0) {
                throw new IllegalStateException("Rapid overlapping key input collapsed direct frame pacing: " + burstMinGapMs + " ms");
            }
            if (timerDrivenBurstMinGapMs < 80.0) {
                throw new IllegalStateException(
                        "Timer-driven direct canvas sped up under key storm: " + timerDrivenBurstMinGapMs + " ms");
            }
        } finally {
            app.canvas.stopLoop();
            app.terminate();
        }
    }

    private static double nanosToMillis(long nanos) {
        return nanos / 1_000_000.0;
    }

    private static String formatMillis(double millis) {
        return String.format("%.3f", millis);
    }

    public static final class ProbeApp extends IApplication {
        final ProbeCanvas canvas = new ProbeCanvas();

        @Override
        public void start() {
            Display.setCurrent((Frame) canvas);
            canvas.startLoop();
        }
    }

    static final class ProbeCanvas extends Canvas implements Runnable {
        private final Object frameMonitor = new Object();
        private final Thread loopThread = new Thread(this, "direct-canvas-pacing-probe");
        private volatile boolean running = true;
        private final long[] frameStarts = new long[32];
        private int frameCount;
        private volatile int selection;
        private volatile int renderedSelection = Integer.MIN_VALUE;
        private volatile long renderedSelectionAt;

        void startLoop() {
            loopThread.setDaemon(true);
            loopThread.start();
        }

        void stopLoop() throws InterruptedException {
            running = false;
            loopThread.interrupt();
            loopThread.join(TimeUnit.SECONDS.toMillis(2L));
        }

        long awaitFrameStart(int targetFrame) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
            synchronized (frameMonitor) {
                while (frameCount < targetFrame) {
                    long remainingNanos = deadline - System.nanoTime();
                    if (remainingNanos <= 0L) {
                        throw new IllegalStateException("Timed out waiting for frame " + targetFrame);
                    }
                    TimeUnit.NANOSECONDS.timedWait(frameMonitor, remainingNanos);
                }
                return frameStarts[targetFrame - 1];
            }
        }

        void awaitFrameCount(int targetFrame) throws InterruptedException {
            awaitFrameStart(targetFrame);
        }

        int frameCount() {
            synchronized (frameMonitor) {
                return frameCount;
            }
        }

        double minFrameGapMillis(int startFrameInclusive, int endFrameInclusive) {
            synchronized (frameMonitor) {
                long minGap = Long.MAX_VALUE;
                int upperBound = java.lang.Math.min(endFrameInclusive, frameCount);
                for (int frame = java.lang.Math.max(2, startFrameInclusive); frame <= upperBound; frame++) {
                    long gap = frameStarts[frame - 1] - frameStarts[frame - 2];
                    if (gap < minGap) {
                        minGap = gap;
                    }
                }
                if (minGap == Long.MAX_VALUE) {
                    throw new IllegalStateException("No frame gaps recorded in requested range");
                }
                return nanosToMillis(minGap);
            }
        }

        long awaitRenderedSelection() throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
            while (renderedSelection != 1) {
                if (System.nanoTime() >= deadline) {
                    throw new IllegalStateException("Timed out waiting for rendered selection change");
                }
                Thread.sleep(1L);
            }
            return renderedSelectionAt;
        }

        @Override
        public void run() {
            Graphics g = getGraphics();
            try {
                while (running) {
                    g.lock();
                    try {
                        int localSelection = selection;
                        if (renderedSelection != localSelection) {
                            renderedSelection = localSelection;
                            renderedSelectionAt = System.nanoTime();
                        }
                        g.clearRect(0, 0, Display.getWidth(), Display.getHeight());
                        if (localSelection != 0) {
                            g.fillRect(0, 0, 20, 20);
                        }
                        long beforeUnlock = System.nanoTime();
                        synchronized (frameMonitor) {
                            if (frameCount < frameStarts.length) {
                                frameStarts[frameCount] = beforeUnlock;
                            }
                            frameCount++;
                            frameMonitor.notifyAll();
                        }
                    } finally {
                        g.unlock(true);
                    }
                }
            } finally {
                g.dispose();
            }
        }

        @Override
        public void paint(Graphics g) {
            g.lock();
            g.clearRect(0, 0, Display.getWidth(), Display.getHeight());
            g.unlock(true);
        }

        @Override
        public void processEvent(int type, int param) {
            if (type == Display.KEY_PRESSED_EVENT && param == Display.KEY_DOWN) {
                selection = 1;
            }
        }
    }
}
