package opendoja.host;

import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.Frame;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.Locale;

final class OpenGlesFpsOverlay implements HostOverlayRenderer {
    private static final long SAMPLE_WINDOW_NANOS = 1_000_000_000L;
    private static final long STALE_AFTER_NANOS = 1_500_000_000L;

    private final ArrayDeque<Long> frameTimes = new ArrayDeque<>();
    private Canvas trackedCanvas;
    private long lastFrameNanos;

    synchronized void recordFrame(Canvas canvas, boolean openGlesActive) {
        if (canvas == null || !openGlesActive) {
            return;
        }
        if (trackedCanvas != canvas) {
            trackedCanvas = canvas;
            frameTimes.clear();
        }
        long now = System.nanoTime();
        lastFrameNanos = now;
        frameTimes.addLast(now);
        trimFrameTimes(now);
    }

    @Override
    public void paint(Graphics2D graphics, ExternalFrameLayout layout, Frame frame, BufferedImage drawImage) {
        if (!(frame instanceof Canvas canvas)) {
            return;
        }

        String label;
        synchronized (this) {
            if (trackedCanvas != canvas) {
                return;
            }
            long now = System.nanoTime();
            trimFrameTimes(now);
            label = String.format(Locale.ROOT, "%.1f FPS", currentFps(now));
        }

        Rectangle area = layout.screenArea();
        if (area.width <= 0 || area.height <= 0) {
            return;
        }

        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int fontSize = Math.max(7, Math.min(11, area.height / 15));
            Font font = new Font(Font.MONOSPACED, Font.BOLD, fontSize);
            g.setFont(font);
            FontMetrics metrics = g.getFontMetrics();
            int paddingX = Math.max(3, fontSize / 3);
            int paddingY = Math.max(1, fontSize / 4);
            int boxWidth = metrics.stringWidth(label) + paddingX * 2;
            int boxHeight = metrics.getHeight() + paddingY * 2;
            int x = area.x + Math.max(4, area.width - boxWidth - 4);
            int y = area.y + 4;
            g.setColor(new Color(0, 0, 0, 160));
            g.fillRoundRect(x, y, boxWidth, boxHeight, 6, 6);
            g.setColor(new Color(255, 255, 255, 230));
            g.drawString(label, x + paddingX, y + paddingY + metrics.getAscent());
        } finally {
            g.dispose();
        }
    }

    private void trimFrameTimes(long now) {
        while (!frameTimes.isEmpty() && now - frameTimes.peekFirst() > SAMPLE_WINDOW_NANOS) {
            frameTimes.removeFirst();
        }
    }

    private double currentFps(long now) {
        if (lastFrameNanos == 0L || now - lastFrameNanos > STALE_AFTER_NANOS) {
            return 0.0;
        }
        if (frameTimes.size() >= 2) {
            long first = frameTimes.peekFirst();
            long last = frameTimes.peekLast();
            if (last > first) {
                return ((frameTimes.size() - 1) * 1_000_000_000.0) / (last - first);
            }
        }
        if (frameTimes.size() == 1) {
            return Math.min(999.0, 1_000_000_000.0 / Math.max(1L, now - frameTimes.peekFirst()));
        }
        return 0.0;
    }
}
