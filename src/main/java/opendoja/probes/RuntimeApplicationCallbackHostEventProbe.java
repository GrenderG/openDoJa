package opendoja.probes;

import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.IApplication;
import opendoja.host.DoJaRuntime;
import opendoja.host.LaunchConfig;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies that queued runtime callbacks do not block host input dispatch even
 * when the active canvas owns a synchronized direct paint loop.
 */
public final class RuntimeApplicationCallbackHostEventProbe {
    private static final CountDownLatch PAINT_ENTERED = new CountDownLatch(1);
    private static final CountDownLatch RELEASE_PAINT = new CountDownLatch(1);
    private static final CountDownLatch EVENT_PROCESSED = new CountDownLatch(1);
    private static final CountDownLatch CALLBACK_PROCESSED = new CountDownLatch(1);
    private static final AtomicReference<ProbeCanvas> CANVAS = new AtomicReference<>();

    private RuntimeApplicationCallbackHostEventProbe() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");

        LaunchConfig config = LaunchConfig.builder(ProbeApp.class)
                .externalFrameEnabled(false)
                .build();
        DoJaRuntime.prepareLaunch(config);
        DoJaRuntime runtime = DoJaRuntime.bootstrap(config);
        try {
            ProbeApp app = new ProbeApp();
            runtime.attachApplication(app);
            runtime.startApplication();

            if (!PAINT_ENTERED.await(2L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Direct paint loop did not enter synchronized paint()");
            }

            ProbeCanvas canvas = CANVAS.get();
            if (canvas == null) {
                throw new IllegalStateException("Probe canvas was not installed");
            }

            runtime.postApplicationCallback(canvas::handleQueuedCallback);
            runtime.dispatchSyntheticKey(Display.KEY_LEFT, Display.KEY_PRESSED_EVENT);

            if (!EVENT_PROCESSED.await(2L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Synthetic key dispatch blocked behind queued callback");
            }

            RELEASE_PAINT.countDown();

            if (!CALLBACK_PROCESSED.await(2L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Queued callback was not drained on app-owned execution");
            }

            DemoLog.info(RuntimeApplicationCallbackHostEventProbe.class,
                    "Host event callback probe OK");
        } finally {
            runtime.shutdown();
            DoJaRuntime.clearPreparedLaunch();
        }
    }

    public static final class ProbeApp extends IApplication {
        @Override
        public void start() {
            ProbeCanvas canvas = new ProbeCanvas();
            CANVAS.set(canvas);
            Display.setCurrent(canvas);
            canvas.startLoop();
        }
    }

    static final class ProbeCanvas extends Canvas implements Runnable {
        private final Graphics graphics = getGraphics();
        private final Thread loopThread = new Thread(this, "runtime-callback-probe-loop");

        void startLoop() {
            loopThread.setDaemon(true);
            loopThread.start();
        }

        @Override
        public void run() {
            while (CALLBACK_PROCESSED.getCount() > 0L) {
                paint(graphics);
            }
        }

        @Override
        public synchronized void paint(Graphics g) {
            if (PAINT_ENTERED.getCount() > 0L) {
                PAINT_ENTERED.countDown();
                try {
                    RELEASE_PAINT.await(2L, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }
            // Mirror a title-owned direct draw loop closely enough to exercise
            // callback draining at explicit graphics lock/unlock boundaries.
            g.lock();
            try {
                Thread.onSpinWait();
            } finally {
                g.unlock(false);
            }
        }

        @Override
        public void processEvent(int type, int param) {
            if (type == Display.KEY_PRESSED_EVENT && param == Display.KEY_LEFT) {
                EVENT_PROCESSED.countDown();
            }
        }

        synchronized void handleQueuedCallback() {
            CALLBACK_PROCESSED.countDown();
        }
    }
}
