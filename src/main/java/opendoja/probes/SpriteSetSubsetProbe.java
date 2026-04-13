package opendoja.probes;

import com.nttdocomo.ui.Display;
import com.nttdocomo.ui.Frame;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.IApplication;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.Sprite;
import com.nttdocomo.ui.SpriteSet;
import opendoja.host.DesktopLauncher;

import java.awt.image.BufferedImage;

/**
 * Verifies that `drawSpriteSet(spriteSet, offset, count)` draws only the requested sprite range.
 */
public final class SpriteSetSubsetProbe {
    private SpriteSetSubsetProbe() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        ProbeApp app = (ProbeApp) DesktopLauncher.launch(ProbeApp.class);
        try {
            app.runProbe();
        } finally {
            app.terminate();
        }
    }

    public static final class ProbeApp extends IApplication {
        private final ProbeCanvas canvas = new ProbeCanvas();

        @Override
        public void start() {
            Display.setCurrent((Frame) canvas);
        }

        void runProbe() {
            canvas.verifySubsetDraw();
        }
    }

    static final class ProbeCanvas extends com.nttdocomo.ui.Canvas {
        @Override
        public void paint(Graphics g) {
            return;
        }

        void verifySubsetDraw() {
            Graphics g = getGraphics();
            try {
                Image red = solid(0xFFFF0000);
                Image green = solid(0xFF00FF00);
                Image blue = solid(0xFF0000FF);

                Sprite[] sprites = new Sprite[]{
                        sprite(red, 4, 4),
                        sprite(green, 20, 4),
                        sprite(blue, 36, 4)
                };
                SpriteSet set = new SpriteSet(sprites);

                g.lock();
                try {
                    g.setColor(Graphics.getColorOfRGB(0, 0, 0));
                    g.fillRect(0, 0, Display.getWidth(), Display.getHeight());
                    g.drawSpriteSet(set, 1, 1);
                } finally {
                    g.unlock(true);
                }

                BufferedImage frame = snapshot();
                assertColor(frame.getRGB(7, 7), 0xFF000000, "sprite before range should stay cleared");
                assertColor(frame.getRGB(23, 7), 0xFF00FF00, "selected sprite should be drawn");
                assertColor(frame.getRGB(39, 7), 0xFF000000, "sprite after range should stay cleared");

                System.out.println("SpriteSet subset probe OK");
            } finally {
                g.dispose();
            }
        }

        private static Sprite sprite(Image image, int x, int y) {
            Sprite sprite = new Sprite(image);
            sprite.setLocation(x, y);
            return sprite;
        }

        private static Image solid(int argb) {
            Image image = Image.createImage(8, 8);
            Graphics g = image.getGraphics();
            g.setColor(argb);
            g.fillRect(0, 0, 8, 8);
            return image;
        }

        private BufferedImage snapshot() {
            Graphics graphics = getGraphics();
            try {
                java.lang.reflect.Field surfaceField = Graphics.class.getDeclaredField("surface");
                surfaceField.setAccessible(true);
                Object surface = surfaceField.get(graphics);
                java.lang.reflect.Method imageMethod = surface.getClass().getDeclaredMethod("image");
                imageMethod.setAccessible(true);
                return (BufferedImage) imageMethod.invoke(surface);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to read canvas frame", e);
            } finally {
                graphics.dispose();
            }
        }

        private static void assertColor(int actual, int expected, String message) {
            if (actual != expected) {
                throw new IllegalStateException(message + " actual=0x" + Integer.toHexString(actual)
                        + " expected=0x" + Integer.toHexString(expected));
            }
        }
    }
}
