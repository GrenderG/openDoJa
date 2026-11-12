package opendoja.probes;

import com.docomostar.media.MediaImage;
import com.docomostar.media.MediaManager;
import com.docomostar.opt.ui.Graphics2;
import com.docomostar.ui.*;
import opendoja.host.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;

/** Checks optional graphics on canvas, offscreen and decoded media surfaces. */
public final class StarGraphics2Probe {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        DesktopLauncher.launch(LaunchConfig.builder(ProbeApp.class).parameter("AppType", "FullApp").build());
        try {
            var canvas = new ProbeCanvas();
            check(canvas.getGraphics() instanceof Graphics2, "Canvas optional graphics");
            Image target = Image.createImage(6, 4);
            Graphics2 graphics = (Graphics2) target.getGraphics();
            graphics.setColor(0xffff0000);
            graphics.fillRect(0, 0, 6, 4);
            Image captured = graphics.getImage(1, 1, 2, 2);
            check(captured.getGraphics() instanceof Graphics2, "Captured image optional graphics");
            check(pixel(captured, 0) == 0xff0000, "Capture contains surface pixels");
            graphics.setColor(0xff000000);
            graphics.fillRect(0, 0, 6, 4);
            check(pixel(captured, 0) == 0xff0000, "Capture owns independent pixels");
            graphics.drawScaledImage(captured, 2, 1, 4, 2, 0, 0, 2, 2);
            check(pixel(target, 8) == 0xff0000 && pixel(target, 11) == 0xff0000, "Scaled drawing through STAR descriptors");
            graphics.setRenderMode(Graphics2.OP_ADD, 255, 255);
            Image blue = Image.createImage(1, 1, new int[]{0xff0000ff}, 0);
            graphics.drawImage(blue, 2, 1);
            check(pixel(target, 8) == 0xff00ff, "Optional additive blending");
            check(graphics.copy() instanceof Graphics2, "Copied graphics retain optional type");

            var gif = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
            gif.setRGB(0, 0, 0xff00ff00);
            var bytes = new ByteArrayOutputStream(); ImageIO.write(gif, "gif", bytes);
            MediaImage media = MediaManager.getImage(bytes.toByteArray());
            MediaManager.use(new MediaImage[]{media, null}, false);
            MediaManager.use((MediaImage[]) null, false);
            Image decoded = media.getImage();
            check(decoded == media.getImage(), "Stable decoded STAR image identity");
            graphics.setRenderMode(Graphics2.OP_REPL, 255, 255);
            graphics.drawImage(decoded, 0, 0);
            check(pixel(target, 0) == 0x00ff00, "Media image reaches shared renderer");
            graphics.drawNthImage(media, 0, 1, 0);
            check(pixel(target, 1) == 0x00ff00, "Optional media-frame drawing unwraps backend");
            media.unuse();
            try { graphics.drawNthImage(media, 0, 0, 0); throw new AssertionError("Unused media accepted"); }
            catch (UIException expected) { }
            media.dispose(); blue.dispose(); captured.dispose(); target.dispose();
        } finally { DoJaRuntime.current().shutdown(); }
        System.out.println("star-graphics2-probe-ok");
    }
    private static int pixel(Image image, int index) {
        return com.nttdocomo.ui._ImageInternalAccess.copyDisplayPixels(Image.unwrap(image))[index] & 0xffffff;
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    public static final class ProbeApp extends com.docomostar.StarApplication {
        @Override public void started(int reason) { }
    }
    private static final class ProbeCanvas extends Canvas {
        @Override public void paint(Graphics graphics) { }
    }
}
