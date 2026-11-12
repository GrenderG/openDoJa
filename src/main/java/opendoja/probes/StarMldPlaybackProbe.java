package opendoja.probes;

import com.docomostar.StarApplication;
import com.docomostar.media.MediaManager;
import com.docomostar.ui.*;
import opendoja.host.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Verifies real MLD completion and application-driven restart through STAR wrappers. */
public final class StarMldPlaybackProbe {
    private static final CountDownLatch COMPLETE = new CountDownLatch(1);
    private static final AtomicReference<Throwable> FAILURE = new AtomicReference<>();
    private static Path soundPath;
    private static volatile boolean running = true;
    private static Thread loop;

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("usage: StarMldPlaybackProbe <short-mld-file>");
        soundPath = Path.of(args[0]);
        System.setProperty("java.awt.headless", "true");
        DesktopLauncher.launch(LaunchConfig.builder(ProbeApp.class).parameter("AppType", "FullApp").build());
        try {
            if (!COMPLETE.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for two MLD completions");
            if (FAILURE.get() != null) throw new AssertionError("STAR playback failed", FAILURE.get());
        } finally {
            running = false;
            if (loop != null) loop.join(1000);
            DoJaRuntime.current().shutdown();
        }
        System.out.println("star-mld-playback-probe-ok");
    }

    public static final class ProbeApp extends StarApplication {
        @Override public void started(int reason) {
            try {
                var sound = MediaManager.getSound(Files.readAllBytes(soundPath));
                sound.use();
                var canvas = new Canvas() { @Override public void paint(Graphics graphics) { } };
                Display.setCurrent(canvas);
                Graphics graphics = canvas.getGraphics();
                AudioPresenter presenter = AudioPresenter.getAudioPresenter(0);
                presenter.setSound(sound);
                presenter.setMediaListener(new MediaListener() {
                    private int completions;
                    @Override public void mediaAction(MediaPresenter source, int type, int param) {
                        try {
                            if (source != presenter) throw new AssertionError("Presenter identity");
                            if (type != AudioPresenter.AUDIO_COMPLETE) return;
                            if (Thread.currentThread() != loop) throw new AssertionError("Callback left the game loop");
                            if (++completions == 2) { presenter.stop(); COMPLETE.countDown(); }
                            else presenter.play();
                        } catch (Throwable failure) { FAILURE.set(failure); COMPLETE.countDown(); }
                    }
                });
                loop = new Thread(() -> {
                    try {
                        presenter.play();
                        while (running) { graphics.lock(); graphics.unlock(false); Thread.sleep(10); }
                    } catch (Throwable failure) { FAILURE.set(failure); COMPLETE.countDown(); }
                }, "star-mld-game-loop");
                loop.setDaemon(true);
                loop.start();
            } catch (Throwable failure) { FAILURE.set(failure); COMPLETE.countDown(); }
        }
    }
}
