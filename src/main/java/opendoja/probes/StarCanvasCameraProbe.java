package opendoja.probes;

import com.docomostar.ui.Canvas;
import com.docomostar.ui.Display;
import com.docomostar.ui.Graphics;
import opendoja.host.DesktopLauncher;
import opendoja.host.DoJaRuntime;
import opendoja.host.LaunchConfig;
import java.awt.image.BufferedImage;

/** Models Ni no Kuni's update-camera, lock, draw-world, unlock frame sequence. */
public final class StarCanvasCameraProbe {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        DesktopLauncher.launch(LaunchConfig.builder(StarApp.class).viewport(240, 320).parameter("AppType", "FullApp").build());
        try {
            Canvas canvas = Display.getCurrent() instanceof Canvas c ? c : null;
            Graphics graphics = canvas.getGraphics();
            var source = com.docomostar.ui.Image.createImage(24, 24);
            var tile = source.getGraphics(); tile.setColor(0xffff0000); tile.fillRect(0, 0, 24, 24);
            for (int[] camera : new int[][]{{96, 120}, {144, 120}, {464, 20}, {488, 44}, {0, 0}}) {
                int cameraX = camera[0], cameraY = camera[1];
                graphics.setOrigin(-cameraX, -cameraY);
                graphics.lock();
                graphics.setColor(0xff203040);
                graphics.fillRect(cameraX, cameraY, 240, 320);
                graphics.drawImage(source, cameraX + 16, cameraY + 16);
                graphics.unlock(true);
                BufferedImage frame = frame();
                check((frame.getRGB(200, 260) & 0xffffff) == 0x203040, "Camera clear must cover viewport");
                check((frame.getRGB(20, 20) & 0xffffff) == 0xff0000, "World tile must keep camera offset across lock");
                check((frame.getRGB(80, 20) & 0xffffff) == 0x203040, "Old camera contents must be cleared");
            }
            check(origin(com.nttdocomo.ui.Display.getCurrent() instanceof com.nttdocomo.ui.Canvas c
                    ? c.getGraphics() : null) == 0, "Fresh canvas graphics start at origin zero");
            source.dispose();
        } finally { DoJaRuntime.current().shutdown(); }
        DesktopLauncher.launch(DojaApp.class);
        try {
            var graphics = ((com.nttdocomo.ui.Canvas) com.nttdocomo.ui.Display.getCurrent()).getGraphics();
            graphics.setOrigin(-144, -120); graphics.lock();
            try { check(origin(graphics) == 0, "Keep existing DoJa canvas lock behavior"); }
            finally { graphics.unlock(false); }
        } finally { DoJaRuntime.current().shutdown(); }
        System.out.println("star-canvas-camera-probe-ok");
    }
    private static int origin(com.nttdocomo.ui.Graphics graphics) throws Exception {
        var field = com.nttdocomo.ui.Graphics.class.getDeclaredField("originX"); field.setAccessible(true);
        return field.getInt(graphics);
    }
    private static BufferedImage frame() throws Exception {
        var field = DoJaRuntime.class.getDeclaredField("presentedFrame"); field.setAccessible(true);
        return (BufferedImage) field.get(DoJaRuntime.current());
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static final class StarApp extends com.docomostar.StarApplication {
        @Override public void started(int reason) { Display.setCurrent(new StarCanvas()); }
    }
    private static final class StarCanvas extends Canvas { @Override public void paint(Graphics graphics) { } }
    public static final class DojaApp extends com.nttdocomo.ui.IApplication {
        @Override public void start() { com.nttdocomo.ui.Display.setCurrent(new DojaCanvas()); }
    }
    private static final class DojaCanvas extends com.nttdocomo.ui.Canvas {
        @Override public void paint(com.nttdocomo.ui.Graphics graphics) { }
    }
}
