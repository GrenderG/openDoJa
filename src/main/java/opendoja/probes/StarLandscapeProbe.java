package opendoja.probes;

import com.docomostar.opt.system.PhoneSystem2;
import com.docomostar.ui.*;
import opendoja.host.*;

import javax.swing.*;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.concurrent.TimeUnit;

/** Models landscape back-buffer presentation and the display-style driven key layout. */
public final class StarLandscapeProbe {
    private static ProbeCanvas canvas;
    private static final int RED = 0xffff0000, GREEN = 0xff00ff00, BLUE = 0xff0000ff, WHITE = 0xffffffff;

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        System.setProperty(OpenDoJaLaunchArgs.HOST_SCALE, "1");
        System.setProperty(OpenDoJaLaunchArgs.DISPLAY_ROTATION, "none");
        DesktopLauncher.launch(LaunchConfig.builder(StarApp.class).viewport(240, 427).parameter("AppType", "FullApp")
                .parameter("TargetDevice", "P01A").build());
        try {
            var runtime = DoJaRuntime.current();
            runtime.setExternalFrameEnabled(false);
            Image buffer = Image.createImage(427, 240);
            Graphics source = buffer.getGraphics();
            source.setColor(WHITE); source.fillRect(0, 0, 427, 240);
            source.setColor(RED); source.fillRect(0, 0, 100, 100);
            source.setColor(GREEN); source.fillRect(327, 0, 100, 100);
            source.setColor(BLUE); source.fillRect(0, 140, 100, 100);
            Graphics target = canvas.getGraphics();
            for (int flip : new int[]{Graphics.FLIP_ROTATE_LEFT, Graphics.FLIP_ROTATE_RIGHT}) {
                target.setFlipMode(flip);
                for (int frame = 0; frame < 2; frame++) {
                    target.lock(); target.drawImage(buffer, 0, 0); target.unlock(true);
                    SwingUtilities.invokeAndWait(() -> { });
                    check(runtime.hasLandscapePresentation(), "Whole rotated frame selects landscape");
                    check(PhoneSystem2.getAttribute(PhoneSystem2.DEV_DISPLAY_STYLE) == 1, "Landscape key layout advertised");
                    check(canvas.getWidth() == 240 && canvas.getHeight() == 427, "Native canvas dimensions preserved");
                    BufferedImage shown = hostImage(runtime);
                    check(shown.getWidth() == 427 && shown.getHeight() == 240, "Host follows landscape frame");
                    check(shown.getRGB(10, 10) == RED && shown.getRGB(417, 10) == GREEN
                            && shown.getRGB(10, 230) == BLUE && shown.getRGB(417, 230) == WHITE,
                            "Whole upright image must survive frame lock and presentation");
                    var pointer = DoJaRuntime.class.getDeclaredMethod("hostPointToCanvas", java.awt.Point.class, boolean.class);
                    pointer.setAccessible(true);
                    var nativePoint = (java.awt.Point) pointer.invoke(runtime, new java.awt.Point(10, 20), false);
                    var expectedPoint = flip == Graphics.FLIP_ROTATE_LEFT ? new java.awt.Point(20, 417) : new java.awt.Point(220, 10);
                    check(expectedPoint.equals(nativePoint), "Pointer follows the same rotation as the displayed frame");
                }
            }
            // Both desktop input sources share this path; the app uses display style to normalize its directions.
            Object panel = field(DoJaRuntime.class, "hostPanel").get(runtime);
            var dispatch = panel.getClass().getDeclaredMethod("dispatchMappedAction", HostControlAction.class, boolean.class);
            dispatch.setAccessible(true);
            SwingUtilities.invokeAndWait(() -> {
                try {
                    dispatch.invoke(panel, HostControlAction.MOVE_UP, true);
                    dispatch.invoke(panel, HostControlAction.MOVE_RIGHT, true);
                } catch (Exception e) { throw new RuntimeException(e); }
            });
            await(() -> canvas.up && canvas.right, "Overlapping landscape directions");
            SwingUtilities.invokeAndWait(() -> {
                try { dispatch.invoke(panel, HostControlAction.MOVE_UP, false); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            await(() -> !canvas.up && canvas.right, "Releasing one direction retains the other");
            SwingUtilities.invokeAndWait(() -> {
                try { dispatch.invoke(panel, HostControlAction.MOVE_RIGHT, false); }
                catch (Exception e) { throw new RuntimeException(e); }
            });
            await(() -> !canvas.up && !canvas.right, "Landscape directions release");

            var portrait = Image.createImage(240, 427);
            target.setFlipMode(Graphics.FLIP_NONE);
            target.lock(); target.drawImage(portrait, 0, 0); target.unlock(true);
            SwingUtilities.invokeAndWait(() -> { });
            check(!runtime.hasLandscapePresentation(), "Portrait frame restores portrait presentation");
            target.setFlipMode(Graphics.FLIP_ROTATE_LEFT);
            target.lock(); target.drawImage(Image.createImage(20, 10), 0, 0); target.unlock(true);
            check(!runtime.hasLandscapePresentation(), "A sprite rotation must not select landscape");
            target.lock(); target.drawImage(buffer, 0, 0); target.unlock(true);
            check(runtime.hasLandscapePresentation(), "Landscape can be selected again");
            Display.setCurrent(new ProbeCanvas());
            check(!runtime.hasLandscapePresentation(), "Orientation belongs to its canvas");
        } finally { DoJaRuntime.current().shutdown(); }

        DesktopLauncher.launch(LaunchConfig.builder(DojaApp.class).viewport(240, 427).build());
        try {
            var target = com.nttdocomo.ui.Display.getCurrent() instanceof com.nttdocomo.ui.Canvas c ? c.getGraphics() : null;
            target.setFlipMode(com.nttdocomo.ui.Graphics.FLIP_ROTATE_LEFT);
            target.lock(); target.drawImage(com.nttdocomo.ui.Image.createImage(427, 240), 0, 0); target.unlock(true);
            check(!DoJaRuntime.current().hasLandscapePresentation(), "DoJa retains its existing framing");
            BufferedImage shown = hostImage(DoJaRuntime.current());
            check(shown.getWidth() < shown.getHeight(), "DoJa stays portrait after STAR shutdown");
        } finally { DoJaRuntime.current().shutdown(); }
        System.out.println("star-landscape-probe-ok");
    }

    private static BufferedImage hostImage(DoJaRuntime runtime) throws Exception {
        JPanel panel = (JPanel) field(DoJaRuntime.class, "hostPanel").get(runtime);
        BufferedImage[] image = new BufferedImage[1];
        SwingUtilities.invokeAndWait(() -> {
            Dimension size = panel.getPreferredSize();
            image[0] = new BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_ARGB);
            var graphics = image[0].createGraphics();
            try { panel.paint(graphics); } finally { graphics.dispose(); }
        });
        return image[0];
    }

    private static Field field(Class<?> type, String name) throws Exception { var field = type.getDeclaredField(name); field.setAccessible(true); return field; }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void await(java.util.function.BooleanSupplier condition, String message) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        check(condition.getAsBoolean(), message);
    }
    public static class ProbeCanvas extends Canvas {
        volatile boolean up, right;
        @Override public void paint(Graphics graphics) { }
        @Override public void processEvent(int type, int param) {
            int bits = getKeypadState();
            up = (bits & 1 << Display.KEY_UP) != 0;
            right = (bits & 1 << Display.KEY_RIGHT) != 0;
        }
    }
    public static class StarApp extends com.docomostar.StarApplication {
        @Override public void started(int reason) { canvas = new ProbeCanvas(); Display.setCurrent(canvas); }
    }
    public static class DojaApp extends com.nttdocomo.ui.IApplication {
        @Override public void start() { com.nttdocomo.ui.Display.setCurrent(new com.nttdocomo.ui.Canvas() {
            @Override public void paint(com.nttdocomo.ui.Graphics graphics) { }
        }); }
    }
}
