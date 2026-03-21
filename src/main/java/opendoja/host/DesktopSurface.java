package opendoja.host;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Objects;
import java.util.function.Consumer;

public final class DesktopSurface {
    private BufferedImage image;
    private int backgroundColor = 0xFF000000;
    private Consumer<BufferedImage> repaintHook;

    public DesktopSurface(int width, int height) {
        this.image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    public void resize(int width, int height) {
        if (image.getWidth() == width && image.getHeight() == height) {
            return;
        }
        BufferedImage resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = resized.createGraphics();
        g2.drawImage(image, 0, 0, null);
        g2.dispose();
        this.image = resized;
    }

    public BufferedImage image() {
        return image;
    }

    public int width() {
        return image.getWidth();
    }

    public int height() {
        return image.getHeight();
    }

    public int backgroundColor() {
        return backgroundColor;
    }

    public void setBackgroundColor(int backgroundColor) {
        this.backgroundColor = backgroundColor;
    }

    public void setRepaintHook(Consumer<BufferedImage> repaintHook) {
        this.repaintHook = repaintHook;
    }

    public void flush(BufferedImage presentedFrame) {
        if (repaintHook != null) {
            repaintHook.accept(presentedFrame);
        }
    }

    @Override
    public String toString() {
        return "DesktopSurface{" + image.getWidth() + "x" + image.getHeight() + ", background=" + backgroundColor + "}";
    }
}
