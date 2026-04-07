package opendoja.probes;

import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.IApplication;
import com.nttdocomo.ui.MediaListener;
import com.nttdocomo.ui.MediaManager;
import com.nttdocomo.ui.MediaPresenter;
import com.nttdocomo.ui.MediaSound;
import opendoja.host.DoJaRuntime;
import opendoja.host.LaunchConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifies that runtime-backed async audio callbacks re-enter a direct canvas
 * loop on the application's own thread rather than on a host worker thread.
 */
public final class AudioPresenterRuntimeCallbackThreadProbe {
    private static final CountDownLatch CALLBACK_LATCH = new CountDownLatch(1);
    private static final AtomicReference<String> CALLBACK_THREAD = new AtomicReference<>();
    private static final AtomicReference<String> LOOP_THREAD = new AtomicReference<>();
    private static final AtomicReference<Throwable> FAILURE = new AtomicReference<>();
    private static volatile Path soundPath;

    private AudioPresenterRuntimeCallbackThreadProbe() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException(
                    "usage: AudioPresenterRuntimeCallbackThreadProbe <mld-file>");
        }

        soundPath = Path.of(args[0]);
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
            if (!CALLBACK_LATCH.await(8L, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for AUDIO_COMPLETE");
            }
            Throwable failure = FAILURE.get();
            if (failure != null) {
                throw new IllegalStateException("Probe canvas failed", failure);
            }
            if (!Objects.equals(CALLBACK_THREAD.get(), LOOP_THREAD.get())) {
                throw new IllegalStateException("Callback ran on " + CALLBACK_THREAD.get()
                        + " instead of " + LOOP_THREAD.get());
            }
            DemoLog.info(AudioPresenterRuntimeCallbackThreadProbe.class,
                    "Runtime callback thread probe OK thread=" + CALLBACK_THREAD.get());
        } finally {
            runtime.shutdown();
            DoJaRuntime.clearPreparedLaunch();
        }
    }

    public static final class ProbeApp extends IApplication {
        @Override
        public void start() {
            try {
                Display.setCurrent(new ProbeCanvas(soundPath));
            } catch (Exception exception) {
                FAILURE.compareAndSet(null, exception);
                CALLBACK_LATCH.countDown();
            }
        }
    }

    static final class ProbeCanvas extends Canvas implements Runnable, MediaListener {
        private final Graphics graphics;
        private final AudioPresenter presenter;
        private final Thread loopThread;

        ProbeCanvas(Path path) throws Exception {
            MediaSound sound = MediaManager.getSound(Files.readAllBytes(path));
            sound.use();

            this.graphics = getGraphics();
            this.presenter = AudioPresenter.getAudioPresenter(0);
            this.presenter.setMediaListener(this);
            this.presenter.setSound(sound);
            this.loopThread = new Thread(this, "audio-callback-loop");
            this.loopThread.setDaemon(true);
            this.loopThread.start();
            this.presenter.play();
        }

        @Override
        public void run() {
            LOOP_THREAD.set(Thread.currentThread().getName());
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(7L);
            try {
                while (CALLBACK_LATCH.getCount() > 0L && System.nanoTime() < deadline) {
                    paint(graphics);
                }
            } catch (Throwable throwable) {
                FAILURE.compareAndSet(null, throwable);
                CALLBACK_LATCH.countDown();
            }
        }

        @Override
        public synchronized void paint(Graphics g) {
            // Keep the probe close to a title-owned direct draw loop: the
            // callback should be drained around these explicit frame slices.
            g.lock();
            try {
                Thread.onSpinWait();
            } finally {
                g.unlock(false);
            }
        }

        @Override
        public synchronized void mediaAction(MediaPresenter presenter, int type, int param) {
            if (type != AudioPresenter.AUDIO_COMPLETE) {
                return;
            }
            CALLBACK_THREAD.set(Thread.currentThread().getName());
            this.presenter.stop();
            CALLBACK_LATCH.countDown();
        }
    }
}
