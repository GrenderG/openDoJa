package opendoja.probes;

import com.docomostar.StarApplication;
import com.docomostar.io.BufferedReader;
import com.docomostar.opt.ui.PointingDevice;
import com.docomostar.opt.ui.TouchDevice;
import com.docomostar.ui.*;
import com.docomostar.util.Timer;
import opendoja.host.*;

import java.io.StringReader;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/** Checks adapter boundaries: callback identity, shared input, live arrays and pixel output. */
public final class StarCommonApisProbe {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        DesktopLauncher.launch(LaunchConfig.builder(ProbeApp.class).parameter("AppType", "FullApp").build());
        try {
            verifyTimers();
            verifyPointer();
            verifyReader();
            verifySpritesAndMaps();
        } finally { DoJaRuntime.current().shutdown(); }
        System.out.println("star-common-apis-probe-ok");
    }

    private static void verifyTimers() throws Exception {
        Timer timer = new Timer();
        check(timer instanceof com.docomostar.util.TimeKeeper, "STAR TimeKeeper linkage");
        CountDownLatch fired = new CountDownLatch(1);
        AtomicReference<Timer> callback = new AtomicReference<>();
        timer.setTime(20);
        timer.setListener(source -> { callback.set(source); fired.countDown(); });
        timer.start();
        check(fired.await(3, TimeUnit.SECONDS), "Timer fires through the runtime scheduler");
        check(callback.get() == timer, "Callback receives the original STAR timer");
        timer.dispose();
        try { timer.start(); throw new AssertionError("Disposed timer accepted start"); }
        catch (UIException failure) { check(failure.getStatus() == UIException.ILLEGAL_STATE, "STAR timer exception status"); }

        Timer repeating = new Timer();
        AtomicInteger count = new AtomicInteger();
        CountDownLatch repeated = new CountDownLatch(1);
        repeating.setRepeat(true); repeating.setTime(20);
        repeating.setListener(source -> {
            if (count.incrementAndGet() == 3) { source.stop(); repeated.countDown(); }
        });
        repeating.start();
        check(repeated.await(3, TimeUnit.SECONDS), "Repeating callback can stop its own timer");
        Thread.sleep(80);
        check(count.get() == 3, "Stopped timer does not keep firing");
        repeating.dispose();

        com.nttdocomo.util.Timer nativeTimer = new com.nttdocomo.util.Timer();
        CountDownLatch nativeFired = new CountDownLatch(1);
        AtomicReference<com.nttdocomo.util.Timer> nativeCallback = new AtomicReference<>();
        nativeTimer.setTime(20);
        nativeTimer.setListener(source -> { nativeCallback.set(source); nativeFired.countDown(); });
        nativeTimer.start();
        check(nativeFired.await(3, TimeUnit.SECONDS) && nativeCallback.get() == nativeTimer, "DoJa timer identity unchanged");
        nativeTimer.dispose();
    }

    private static void verifyPointer() throws Exception {
        PointingDevice.setMode(PointingDevice.MODE_MOUSE);
        PointingDevice.setEnabled(true); TouchDevice.setEnabled(true);
        com.nttdocomo.opt.ui.PointingDevice.setPosition(23, 31);
        check(TouchDevice.getX() == 23 && TouchDevice.getY() == 31, "STAR reads coordinates updated by the DoJa host");
        PointingDevice.setPosition(41, 52);
        check(com.nttdocomo.opt.ui.TouchDevice.getX() == 41 && com.nttdocomo.opt.ui.TouchDevice.getY() == 52,
                "STAR pointer changes reach the same DoJa state");
        var dispatch = DoJaRuntime.class.getDeclaredMethod("dispatchPointerEvent", int.class, int.class, int.class);
        dispatch.setAccessible(true);
        dispatch.invoke(DoJaRuntime.current(), Display.TOUCH_PRESSED_EVENT, 61, 72);
        check(ProbeApp.touch.await(3, TimeUnit.SECONDS), "Host touch event reaches the STAR canvas");
        check(ProbeApp.touchX == 61 && ProbeApp.touchY == 72, "Touch callback reads the same host coordinates");
        TouchDevice.setEnabled(false);
        check(TouchDevice.getX() == -1 && !com.nttdocomo.opt.ui.TouchDevice.isEnabled(), "Touch enable state is shared");
        PointingDevice.setMode(PointingDevice.MODE_JOYSTICK);
        check(com.nttdocomo.opt.ui.PointingDevice.getMode() == PointingDevice.MODE_JOYSTICK, "Pointer mode is shared");
        PointingDevice.setMode(PointingDevice.MODE_MOUSE); PointingDevice.setEnabled(false);
    }

    private static void verifyReader() throws Exception {
        // Scratchpad/file readers can contain data while reporting ready() == false.
        try (BufferedReader reader = new BufferedReader(new StringReader("one\r\ntwo\nthree") {
            @Override public boolean ready() { return false; }
        }, 4)) {
            check(reader.ready(), "DoJa readiness handling also applies to STAR readers");
            check("one".equals(reader.readLine()), "Readiness probe preserves the first character");
            reader.mark(20);
            check("two".equals(reader.readLine()), "CRLF and LF lines");
            reader.reset();
            check(reader.skip(4) == 4 && "three".equals(reader.readLine()), "Inherited skip, mark and reset");
            check(reader.readLine() == null && !reader.ready(), "EOF handling");
        }
    }

    private static void verifySpritesAndMaps() {
        Image red = solid(0xffff0000), green = solid(0xff00ff00), blue = solid(0xff0000ff);
        Image target = Image.createImage(24, 16);
        Graphics g = target.getGraphics();
        Sprite first = new Sprite(red), second = new Sprite(green);
        first.setLocation(2, 2); second.setLocation(4, 2);
        Sprite[] live = {first, second};
        SpriteSet set = new SpriteSet(live);
        check(set.getSprites() == live && set.getSprite(0) == first, "Sprite and array identity retained");
        set.setCollisionAll(); check(set.isCollision(0, 1), "DoJa collision detection through STAR");
        live[1] = null;
        set.setCollisionOf(0); check(set.getCollisionFlag(0) == 0, "Collision sees replaced array elements");
        live[0] = new Sprite(blue); live[0].setLocation(10, 2);
        clear(g); g.drawSpriteSet(set);
        pixel(g, 11, 3, 0xff0000ff, "Drawing sees replaced sprites");
        pixel(g, 3, 3, 0xff000000, "Previous sprite no longer drawn");
        blue.getGraphics().setPixel(1, 1, 0xff00ff00);
        clear(g); g.drawSpriteSet(set, 0, 1);
        pixel(g, 11, 3, 0xff00ff00, "Sprite shares the original image pixels");
        live[0].setVisible(false); set.setCollisionAll();
        clear(g); g.drawSpriteSet(set); pixel(g, 11, 3, 0xff000000, "Hidden sprites are not drawn");

        Image[] tiles = {red, green};
        int[] data = {0, 1};
        ImageMap map = new ImageMap(4, 4, 2, 1, data, tiles);
        map.setWindow(1, 0, 1, 1);
        clear(g); g.drawImageMap(map, 0, 0); pixel(g, 0, 0, 0xff00ff00, "Map window uses cells");
        tiles[1] = red;
        clear(g); g.drawImageMap(map, 0, 0); pixel(g, 0, 0, 0xffff0000, "Live tile replacement retains map window");
        data[1] = 0; tiles[0] = green;
        clear(g); g.drawImageMap(map, 0, 0); pixel(g, 0, 0, 0xff00ff00, "Map data remains live");
        tiles[0] = null;
        clear(g); g.drawImageMap(map, 0, 0); pixel(g, 0, 0, 0xff000000, "Null tiles are skipped");

        Image disposed = solid(0xffffffff); disposed.dispose();
        try { new Sprite(disposed); throw new AssertionError("Disposed sprite image accepted"); }
        catch (UIException failure) { check(failure.getStatus() == UIException.ILLEGAL_STATE, "Sprite constructor translates STAR exceptions"); }
        tiles[0] = disposed;
        try { g.drawImageMap(map, 0, 0); throw new AssertionError("Disposed tile accepted"); }
        catch (UIException failure) { check(failure.getStatus() == UIException.ILLEGAL_STATE, "Map drawing translates STAR exceptions"); }
    }

    private static Image solid(int color) {
        Image image = Image.createImage(4, 4);
        Graphics g = image.getGraphics(); g.setColor(color); g.fillRect(0, 0, 4, 4);
        return image;
    }
    private static void clear(Graphics g) { g.setColor(0xff000000); g.fillRect(0, 0, 24, 16); }
    private static void pixel(Graphics g, int x, int y, int expected, String message) {
        int actual = g.getRGBPixel(x, y);
        check(actual == (expected & 0x00ffffff), message + ": " + Integer.toHexString(actual));
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static final class ProbeApp extends StarApplication {
        static final CountDownLatch touch = new CountDownLatch(1);
        static volatile int touchX, touchY;
        @Override public void started(int reason) {
            Display.setCurrent(new Canvas() {
                @Override public void paint(Graphics g) { }
                @Override public void processEvent(int type, int param) {
                    if (type == Display.TOUCH_PRESSED_EVENT) {
                        touchX = TouchDevice.getX(); touchY = TouchDevice.getY(); touch.countDown();
                    }
                }
            });
        }
    }
}
