package opendoja.probes;

import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Frame;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.IApplication;
import opendoja.host.DesktopLauncher;

import java.util.concurrent.TimeUnit;

/**
 * Verifies that a direct Canvas loop which ends frames with unlock(false) keeps
 * its own pacing instead of inheriting the sync-unlock delay used by explicit
 * unlock(true) / flush() frame boundaries.
 */
public final class DirectCanvasUnlockFalseProbe {
    private DirectCanvasUnlockFalseProbe() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        System.setProperty(opendoja.host.OpenDoJaLaunchArgs.SYNC_UNLOCK_INTERVAL_MS, "120");
        ProbeApp app = (ProbeApp) DesktopLauncher.launch(ProbeApp.class);
        try {
            app.canvas.awaitFrameCount(8);
            double minGapMs = app.canvas.minFrameGapMillis(2, 8);
            double maxGapMs = app.canvas.maxFrameGapMillis(2, 8);
            double avgGapMs = app.canvas.avgFrameGapMillis(2, 8);

            System.out.println("avgFrameGapMs=" + formatMillis(avgGapMs)
                    + " minFrameGapMs=" + formatMillis(minGapMs)
                    + " maxFrameGapMs=" + formatMillis(maxGapMs));

            if (avgGapMs >= 70.0) {
                throw new IllegalStateException("unlock(false) inherited sync pacing: avg gap " + avgGapMs + " ms");
            }
            if (maxGapMs >= 95.0) {
                throw new IllegalStateException("unlock(false) frame gap still drifted toward sync pacing: max gap " + maxGapMs + " ms");
            }
        } finally {
            app.canvas.stopLoop();
            app.terminate();
        }
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
        private static final int TARGET_FRAME_COUNT = 8;
        private static final long TARGET_FRAME_GAP_NANOS = TimeUnit.MILLISECONDS.toNanos(20L);

        private final Object frameMonitor = new Object();
        private final Thread loopThread = new Thread(this, "direct-canvas-unlock-false-probe");
        private final long[] frameTimes = new long[TARGET_FRAME_COUNT];
        private volatile boolean running = true;
        private int frameCount;

        void startLoop() {
            loopThread.setDaemon(true);
            loopThread.start();
        }

        void stopLoop() throws InterruptedException {
            running = false;
            loopThread.interrupt();
            loopThread.join(TimeUnit.SECONDS.toMillis(2L));
        }

        void awaitFrameCount(int targetFrame) throws InterruptedException {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5L);
            synchronized (frameMonitor) {
                while (frameCount < targetFrame) {
                    long remainingNanos = deadline - System.nanoTime();
                    if (remainingNanos <= 0L) {
                        throw new IllegalStateException("Timed out waiting for frame " + targetFrame);
                    }
                    TimeUnit.NANOSECONDS.timedWait(frameMonitor, remainingNanos);
                }
            }
        }

        double minFrameGapMillis(int startFrameInclusive, int endFrameInclusive) {
            return summarizeFrameGapMillis(startFrameInclusive, endFrameInclusive, Summary.MIN);
        }

        double maxFrameGapMillis(int startFrameInclusive, int endFrameInclusive) {
            return summarizeFrameGapMillis(startFrameInclusive, endFrameInclusive, Summary.MAX);
        }

        double avgFrameGapMillis(int startFrameInclusive, int endFrameInclusive) {
            return summarizeFrameGapMillis(startFrameInclusive, endFrameInclusive, Summary.AVG);
        }

        @Override
        public void run() {
            Graphics g = getGraphics();
            try {
                while (running && frameCount < TARGET_FRAME_COUNT) {
                    g.lock();
                    try {
                        g.clearRect(0, 0, Display.getWidth(), Display.getHeight());
                        if ((frameCount & 1) == 0) {
                            g.fillRect(0, 0, 24, 24);
                        }
                    } finally {
                        g.unlock(false);
                    }
                    long framePresentedAt = System.nanoTime();
                    recordFrame(framePresentedAt);
                    long nextFrameAt = framePresentedAt + TARGET_FRAME_GAP_NANOS;
                    while (running) {
                        long remainingNanos = nextFrameAt - System.nanoTime();
                        if (remainingNanos <= 0L) {
                            break;
                        }
                        Thread.yield();
                    }
                }
            } finally {
                g.dispose();
            }
        }

        @Override
        public void paint(Graphics g) {
            return;
        }

        private void recordFrame(long frameTime) {
            synchronized (frameMonitor) {
                if (frameCount < frameTimes.length) {
                    frameTimes[frameCount] = frameTime;
                    frameCount++;
                    frameMonitor.notifyAll();
                }
            }
        }

        private double summarizeFrameGapMillis(int startFrameInclusive, int endFrameInclusive, Summary summary) {
            synchronized (frameMonitor) {
                int lowerBound = java.lang.Math.max(2, startFrameInclusive);
                int upperBound = java.lang.Math.min(endFrameInclusive, frameCount);
                if (lowerBound > upperBound) {
                    throw new IllegalStateException("No frame gaps recorded in requested range");
                }
                long totalGap = 0L;
                long selectedGap = summary == Summary.MIN ? Long.MAX_VALUE : Long.MIN_VALUE;
                int gaps = 0;
                for (int frame = lowerBound; frame <= upperBound; frame++) {
                    long gap = frameTimes[frame - 1] - frameTimes[frame - 2];
                    totalGap += gap;
                    gaps++;
                    if (summary == Summary.MIN) {
                        selectedGap = java.lang.Math.min(selectedGap, gap);
                    } else if (summary == Summary.MAX) {
                        selectedGap = java.lang.Math.max(selectedGap, gap);
                    }
                }
                long result = summary == Summary.AVG ? (totalGap / gaps) : selectedGap;
                return result / 1_000_000.0;
            }
        }
    }

    private enum Summary {
        MIN,
        MAX,
        AVG
    }
}
