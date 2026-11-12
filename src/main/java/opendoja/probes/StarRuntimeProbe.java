package opendoja.probes;

import com.docomostar.StarApplication;
import com.docomostar.media.MediaManager;
import com.docomostar.media.MediaSound;
import com.docomostar.ui.*;
import opendoja.host.*;

import javax.sound.midi.*;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Exercises STAR descriptors and presenter identity through the shared host. */
public final class StarRuntimeProbe {
    private static final AtomicInteger INIT = new AtomicInteger();
    private static final AtomicInteger PAINT = new AtomicInteger();
    private static final CountDownLatch COMPLETE = new CountDownLatch(1);
    private static volatile int activationReason = Integer.MIN_VALUE;
    private static volatile MediaPresenter completedPresenter;
    private static volatile int startedState = -1;
    private static ProbeCanvas canvas;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        var config = LaunchConfig.builder(ProbeApp.class).parameter("AppType", "FullApp").build();
        DesktopLauncher.launch(config);
        var runtime = DoJaRuntime.current();
        try {
            waitFor(() -> PAINT.get() >= 1, "Initial STAR paint");
            check(startedState == StarApplication.STAR_STATE_STARTED, "Started state");
            check(activationReason == 0, "Initial activation reason");
            check(StarApplication.getThisStarApplication().getAppState() == StarApplication.STAR_STATE_FULLACTIVE,
                    "Active state");
            check(Display.getCurrent() == canvas, "STAR current-frame identity");
            check("Star-2.0".equals(System.getProperty("microedition.profiles")), "STAR runtime identity");
            check(!DoJaProfile.current().isKnown(), "No DoJa profile during STAR execution");
            Graphics graphics = canvas.getGraphics();
            check(graphics instanceof com.docomostar.opt.ui.j3d.Graphics3D, "STAR optional renderer cast");
            graphics.lock();
            graphics.setColor(Graphics.getColorOfRGB(255, 0, 0));
            graphics.fillRect(0, 0, 10, 10);
            graphics.unlock(true);
            var presented = DoJaRuntime.class.getDeclaredField("presentedFrame");
            presented.setAccessible(true);
            var image = (java.awt.image.BufferedImage) presented.get(runtime);
            check((image.getRGB(2, 2) & 0xffffff) == 0xff0000, "Direct STAR drawing reaches host presentation");
            runtime.dispatchSyntheticKey(Display.KEY_LEFT, Display.KEY_PRESSED_EVENT);
            check((canvas.getKeypadState() & (1 << Display.KEY_LEFT)) != 0, "STAR key polling");
            runtime.dispatchSyntheticKey(Display.KEY_LEFT, Display.KEY_RELEASED_EVENT);
            check((canvas.getKeypadState() & (1 << Display.KEY_LEFT)) == 0, "STAR key release");
            int paints = PAINT.get();
            Display.setCurrent(null);
            Display.setCurrent(canvas);
            waitFor(() -> PAINT.get() > paints, "Reselected STAR canvas paint");
            check(INIT.get() == 1, "STAR init must run exactly once");

            MediaSound sound = MediaManager.getSound(shortMidi());
            sound.use();
            AudioPresenter presenter = AudioPresenter.getAudioPresenter(0);
            presenter.setSound(sound);
            check(presenter.getMediaResource() == sound, "STAR resource identity");
            presenter.setMediaListener((source, type, param) -> {
                if (type == AudioPresenter.AUDIO_COMPLETE) { completedPresenter = source; COMPLETE.countDown(); }
            });
            // End-position playback checks preparation, descriptor adaptation and completion
            // without depending on a physical audio device or playback duration.
            presenter.play(Integer.MAX_VALUE);
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (COMPLETE.getCount() != 0 && System.nanoTime() < deadline) {
                graphics.lock(); graphics.unlock(false); Thread.sleep(10);
            }
            check(COMPLETE.getCount() == 0, "STAR sound must reach prepared playback");
            check(completedPresenter == presenter, "Callback must retain STAR presenter identity");
            presenter.stop(); sound.dispose();
        } finally { runtime.shutdown(); }
        DesktopLauncher.launch(LaunchConfig.builder(DojaProbeApp.class).build());
        try {
            check(System.getProperty("microedition.profiles", "").startsWith("DoCoMoProfile-"),
                    "Restore DoJa identity after STAR shutdown");
            check(StarApplication.getThisStarApplication() == null, "No stale STAR application after DoJa launch");
        } finally { DoJaRuntime.current().shutdown(); }
        System.out.println("star-runtime-probe-ok");
    }

    private static byte[] shortMidi() throws Exception {
        Sequence sequence = new Sequence(Sequence.PPQ, 24);
        Track track = sequence.createTrack();
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, 60, 64), 0));
        track.add(new MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, 60, 0), 1));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        MidiSystem.write(sequence, 0, bytes);
        return bytes.toByteArray();
    }

    private static void waitFor(java.util.function.BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(10);
        check(condition.getAsBoolean(), message);
    }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static final class ProbeApp extends StarApplication {
        @Override public void started(int reason) {
            startedState = getAppState();
            canvas = new ProbeCanvas(); Display.setCurrent(canvas);
        }
        @Override public void activated(int reason) { activationReason = reason; }
    }
    private static final class ProbeCanvas extends Canvas {
        @Override public void init(Graphics graphics) { INIT.incrementAndGet(); }
        @Override public void paint(Graphics graphics) { PAINT.incrementAndGet(); }
    }
    public static final class DojaProbeApp extends com.nttdocomo.ui.IApplication {
        @Override public void start() { }
    }
}
