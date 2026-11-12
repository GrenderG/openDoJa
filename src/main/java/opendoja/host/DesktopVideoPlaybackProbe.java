package opendoja.host;

import com.nttdocomo.ui.*;
import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Graphics;

import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Integration checks using a short local 3GP/MP4 clip and an installed VLC 3.x. */
public final class DesktopVideoPlaybackProbe {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) throw new IllegalArgumentException("usage: DesktopVideoPlaybackProbe <short-video> [--window]");
        System.setProperty("java.awt.headless", Boolean.toString(args.length < 2 || !"--window".equals(args[1])));
        byte[] bytes = Files.readAllBytes(Path.of(args[0]));
        DesktopVideoSupport.VideoMetadata metadata = DesktopVideoSupport.probe(bytes);
        check(metadata.isVideo() && metadata.durationMillis() >= 1000L, "Use a video at least one second long");
        LaunchConfig config = LaunchConfig.builder(ProbeApp.class).externalFrameEnabled(false)
                .parameters(Map.of("UseStorage", "ext")).build();
        DoJaRuntime runtime = DoJaRuntime.bootstrap(config);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try {
            ProbeCanvas canvas = new ProbeCanvas();
            runtime.setCurrentFrame(canvas);
            DesktopVideoPlayback playback = new DesktopVideoPlayback(runtime, metadata.data(), metadata.extension(), false, 0L);
            AtomicReference<DesktopVideoPlayback.Result> result = new AtomicReference<>();
            CountDownLatch started = new CountDownLatch(1);
            long start = System.nanoTime();
            Thread game = worker("video-probe-game", failure, () -> result.set(playback.playAndWait(started::countDown)));
            check(started.await(8, TimeUnit.SECONDS), "Player failed to start");
            Thread direct = worker("video-probe-direct", failure, () -> {
                Graphics graphics = canvas.getGraphics();
                graphics.lock();
                graphics.unlock(false);
            });
            Thread timer = worker("video-probe-timer", failure, () -> runtime.dispatchTimerEvent(canvas, 123));
            runtime.dispatchSyntheticKey(Display.KEY_SELECT, Display.KEY_PRESSED_EVENT);
            Thread.sleep(200);
            check(game.isAlive(), "Video call returned before EOF");
            check(direct.isAlive() && timer.isAlive(), "Rendering/timer advanced during video");
            check(canvas.keyEvents.get() == 0 && canvas.timerEvents.get() == 0, "Input/timers reached game during video");
            long frameDeadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!hasPixels(playback) && System.nanoTime() < frameDeadline) Thread.sleep(10);
            check(hasPixels(playback), "Decoder did not publish visible frames");
            join(game);
            join(direct);
            join(timer);
            check(result.get() == DesktopVideoPlayback.Result.COMPLETED, "Video did not complete");
            check(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) >= metadata.durationMillis() - 250L,
                    "Playback finished before the clip duration");
            check(!runtime.isVideoPlaying() && canvas.timerEvents.get() == 1, "Runtime did not resume");
            check(runtime.keypadState() == 0, "Video input leaked into game");

            // Match Layton: play() must finish before the caller disposes the resource.
            List<Integer> events = new CopyOnWriteArrayList<>();
            List<Thread> callbackThreads = new CopyOnWriteArrayList<>();
            VisualPresenter presenter = presenter(bytes);
            presenter.setMediaListener((source, type, param) -> {
                events.add(type);
                callbackThreads.add(Thread.currentThread());
            });
            Thread application = worker("video-probe-presenter", failure, () -> {
                presenter.play();
                check(events.equals(List.of(VisualPresenter.VISUAL_PLAYING, VisualPresenter.VISUAL_COMPLETE)),
                        "play() returned before VISUAL_COMPLETE or callback order changed: " + events);
                presenter.getMediaResource().dispose();
            });
            join(application);
            check(callbackThreads.stream().allMatch(thread -> thread == application), "Native thread ran game callbacks");

            // Exercise the native file API as used by Layton's SD video path, including seek/status/position.
            var token = com.nttdocomo.fs.DoJaStorageService.getAccessToken(
                    com.nttdocomo.fs.DoJaAccessToken.ACCESS_PLATFORM,
                    com.nttdocomo.fs.DoJaStorageService.SHARE_APPLICATION);
            var folder = com.nttdocomo.device.StorageDevice.getInstance("/ext0").getFolder(token);
            var file = folder.createFile("video-probe-" + Long.toUnsignedString(System.nanoTime()) + ".3gp");
            try {
                var entity = file.open(com.nttdocomo.fs.File.MODE_WRITE_ONLY);
                try (var output = entity.openOutputStream()) {
                    output.write(bytes);
                } finally {
                    entity.close();
                }
                Thread nativeCall = worker("video-probe-file", failure, () -> {
                    var nativePlayer = com.nttdocomo.opt.device.MediaPlayer.getMediaPlayer();
                    check(nativePlayer.play(file, 500L) == com.nttdocomo.opt.device.MediaPlayer.STATUS_COMPLETED,
                            "Native file playback did not complete");
                    check(nativePlayer.getLastStoppedPosition() >= metadata.durationMillis() - 250L,
                            "Native stopped position did not reach EOF");
                });
                join(nativeCall);
            } finally {
                file.delete();
            }

            VisualPresenter cancelled = presenter(bytes);
            List<Integer> cancelledEvents = new CopyOnWriteArrayList<>();
            cancelled.setMediaListener((source, type, param) -> {
                cancelledEvents.add(type);
                if (type == VisualPresenter.VISUAL_PLAYING) cancelled.stop();
            });
            Thread cancelThread = worker("video-probe-stop", failure, cancelled::play);
            join(cancelThread);
            check(cancelledEvents.equals(List.of(VisualPresenter.VISUAL_PLAYING, VisualPresenter.VISUAL_STOPPED)),
                    "Cancellation produced duplicate/completion events: " + cancelledEvents);
            check(!runtime.isVideoPlaying(), "stop() left the runtime paused");

            // A synchronous API call on Swing must still allow UI events to execute.
            runtime.setCurrentFrame(null);
            AtomicInteger uiTicks = new AtomicInteger();
            CountDownLatch edtDone = new CountDownLatch(1);
            CountDownLatch edtStarted = new CountDownLatch(1);
            CountDownLatch callbackDone = new CountDownLatch(1);
            SwingUtilities.invokeLater(() -> {
                Timer uiTimer = new Timer(25, event -> uiTicks.incrementAndGet());
                uiTimer.start();
                try {
                    VisualPresenter edtPresenter = presenter(bytes);
                    edtPresenter.setMediaListener((source, type, param) -> {
                        if (type == VisualPresenter.VISUAL_PLAYING) edtStarted.countDown();
                    });
                    edtPresenter.play();
                } catch (Throwable throwable) {
                    failure.compareAndSet(null, throwable);
                } finally {
                    uiTimer.stop();
                    edtDone.countDown();
                }
            });
            check(edtStarted.await(8, TimeUnit.SECONDS), "EDT player did not start");
            runtime.postApplicationCallback(() -> {
                if (runtime.isVideoPlaying()) failure.compareAndSet(null, new AssertionError("Game callback ran during video"));
                callbackDone.countDown();
            });
            check(edtDone.await(10, TimeUnit.SECONDS), "EDT playback deadlocked");
            check(uiTicks.get() > 0, "EDT stopped servicing window events");
            check(callbackDone.await(2, TimeUnit.SECONDS), "Deferred callback was not woken after video");

            DesktopVideoPlayback broken = new DesktopVideoPlayback(runtime, new byte[32], "3gp", false, 0L);
            AtomicReference<DesktopVideoPlayback.Result> brokenResult = new AtomicReference<>();
            Thread brokenThread = worker("video-probe-corrupt", failure, () -> brokenResult.set(broken.playAndWait(null)));
            join(brokenThread);
            check(brokenResult.get() == DesktopVideoPlayback.Result.FAILED, "Corrupt media result: " + brokenResult.get());
            check(!runtime.isVideoPlaying(), "Decode failure left runtime paused");

            DesktopVideoPlayback closing = new DesktopVideoPlayback(runtime, metadata.data(), metadata.extension(), false, 0L);
            CountDownLatch closingStarted = new CountDownLatch(1);
            Thread closingThread = worker("video-probe-close", failure, () -> {
                check(closing.playAndWait(closingStarted::countDown) == DesktopVideoPlayback.Result.STOPPED,
                        "Shutdown did not cancel playback");
            });
            check(closingStarted.await(8, TimeUnit.SECONDS), "Shutdown test failed to start");
            runtime.shutdown();
            join(closingThread);
            check(!runtime.isVideoPlaying(), "Shutdown left native player active");
            if (failure.get() != null) throw new AssertionError("Playback worker failed", failure.get());
            System.out.println("desktop-video-playback-probe-ok");
        } finally {
            runtime.shutdown();
        }
    }

    private static boolean hasPixels(DesktopVideoPlayback playback) {
        var image = playback.frame();
        for (int y = 0; y < image.getHeight(); y += 8) {
            for (int x = 0; x < image.getWidth(); x += 8) {
                if ((image.getRGB(x, y) & 0xFFFFFF) != 0) return true;
            }
        }
        return false;
    }

    private static VisualPresenter presenter(byte[] bytes) throws Exception {
        MediaImage image = MediaManager.getImage(bytes);
        image.use();
        VisualPresenter presenter = new VisualPresenter();
        presenter.setImage(image);
        presenter.setVisible(true);
        presenter.setAttribute(VisualPresenter.AUDIO_MODE, VisualPresenter.ATTR_AUDIO_OFF);
        return presenter;
    }

    private static Thread worker(String name, AtomicReference<Throwable> failure, Task task) {
        Thread thread = new Thread(() -> {
            try { task.run(); }
            catch (Throwable throwable) { failure.compareAndSet(null, throwable); }
        }, name);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private static void join(Thread thread) throws InterruptedException {
        thread.join(10_000);
        check(!thread.isAlive(), "Timed out: " + thread.getName());
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    @FunctionalInterface
    private interface Task { void run() throws Exception; }

    public static final class ProbeApp extends IApplication {
        @Override public void start() { }
    }

    private static final class ProbeCanvas extends Canvas {
        private final AtomicInteger keyEvents = new AtomicInteger();
        private final AtomicInteger timerEvents = new AtomicInteger();
        @Override public void paint(Graphics graphics) { }
        @Override public void processEvent(int type, int param) {
            if (type == Display.KEY_PRESSED_EVENT) keyEvents.incrementAndGet();
            if (type == Display.TIMER_EXPIRED_EVENT) timerEvents.incrementAndGet();
        }
    }
}
