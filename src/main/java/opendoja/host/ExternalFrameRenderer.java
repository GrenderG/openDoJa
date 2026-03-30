package opendoja.host;

import com.nttdocomo.ui.Frame;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

final class ExternalFrameRenderer {
    private static final int SIDE_WIDTH = 2;
    private static final int TOP_HEIGHT = 18;
    private static final int BOTTOM_HEIGHT = 20;
    private static final int HORIZONTAL_INSET = 2;
    private static final int VERTICAL_INSET = 2;
    private static final int[] SOFT_KEYS = {
            Frame.SOFT_KEY_1,
            Frame.SOFT_KEY_2
    };

    private final List<HostOverlayRenderer> overlays = new CopyOnWriteArrayList<>();
    private volatile boolean enabled;

    ExternalFrameRenderer(boolean enabled) {
        this.enabled = enabled;
    }

    boolean enabled() {
        return enabled;
    }

    void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    void addOverlay(HostOverlayRenderer overlay) {
        overlays.add(Objects.requireNonNull(overlay, "overlay"));
    }

    void removeOverlay(HostOverlayRenderer overlay) {
        overlays.remove(overlay);
    }

    ExternalFrameLayout layoutFor(int viewportWidth, int viewportHeight, int scale) {
        int clampedScale = Math.max(1, scale);
        if (!enabled) {
            Rectangle screenArea = new Rectangle(0, 0, viewportWidth, viewportHeight);
            Rectangle drawArea = new Rectangle(0, 0, viewportWidth, viewportHeight);
            return new ExternalFrameLayout(false, clampedScale, screenArea, drawArea, new Rectangle(), new Rectangle(),
                    new Rectangle(), new Rectangle(), new Rectangle(), new Rectangle(),
                    new Dimension(viewportWidth * clampedScale, viewportHeight * clampedScale));
        }

        int outerWidth = viewportWidth + SIDE_WIDTH * 2;
        int outerHeight = viewportHeight + TOP_HEIGHT + BOTTOM_HEIGHT;

        Rectangle screenArea = new Rectangle(SIDE_WIDTH, TOP_HEIGHT, viewportWidth, viewportHeight);
        Rectangle drawArea = new Rectangle(screenArea);
        Rectangle topBar = new Rectangle(0, 0, outerWidth, TOP_HEIGHT);
        Rectangle bottomBar = new Rectangle(0, TOP_HEIGHT + viewportHeight, outerWidth, BOTTOM_HEIGHT);
        Rectangle leftConnector = new Rectangle(0, TOP_HEIGHT, SIDE_WIDTH, viewportHeight);
        Rectangle rightConnector = new Rectangle(SIDE_WIDTH + viewportWidth, TOP_HEIGHT, SIDE_WIDTH, viewportHeight);

        Rectangle statusArea = new Rectangle(HORIZONTAL_INSET, VERTICAL_INSET,
                Math.max(0, outerWidth - HORIZONTAL_INSET * 2),
                Math.max(0, TOP_HEIGHT - VERTICAL_INSET * 2));
        Rectangle softKeyArea = new Rectangle(HORIZONTAL_INSET, bottomBar.y + VERTICAL_INSET,
                Math.max(0, outerWidth - HORIZONTAL_INSET * 2),
                Math.max(0, BOTTOM_HEIGHT - VERTICAL_INSET * 2));

        return new ExternalFrameLayout(true, clampedScale, screenArea, drawArea, topBar, bottomBar,
                leftConnector, rightConnector, statusArea, softKeyArea,
                new Dimension(outerWidth * clampedScale, outerHeight * clampedScale));
    }

    void paint(Graphics2D graphics, Frame frame, BufferedImage drawImage, int viewportWidth, int viewportHeight, int scale) {
        ExternalFrameLayout layout = layoutFor(viewportWidth, viewportHeight, scale);
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.scale(layout.scale(), layout.scale());
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_GASP);
            g.setColor(Color.BLACK);
            g.fillRect(0, 0, layout.preferredSize().width / layout.scale(), layout.preferredSize().height / layout.scale());
            if (layout.enabled()) {
                paintChrome(g, layout, frame);
            }
            paintDrawArea(g, layout.screenArea(), layout.drawArea(), drawImage);
            paintOverlays(g, layout, frame, drawImage);
        } finally {
            g.dispose();
        }
    }

    private void paintChrome(Graphics2D g, ExternalFrameLayout layout, Frame frame) {
        Color outer = new Color(0xD7D8DB);

        g.setColor(outer);
        g.fillRect(0, 0, layout.topBar().width, layout.topBar().height);
        g.fillRect(0, layout.bottomBar().y, layout.bottomBar().width, layout.bottomBar().height);
        g.fillRect(layout.leftConnector().x, layout.leftConnector().y, layout.leftConnector().width, layout.leftConnector().height);
        g.fillRect(layout.rightConnector().x, layout.rightConnector().y, layout.rightConnector().width, layout.rightConnector().height);

        paintSoftKeys(g, layout.softKeyArea(), frame);
    }

    private void paintDrawArea(Graphics2D g, Rectangle screenArea, Rectangle drawArea, BufferedImage drawImage) {
        g.setColor(Color.BLACK);
        g.fillRect(screenArea.x, screenArea.y, screenArea.width, screenArea.height);
        if (drawImage != null) {
            g.drawImage(drawImage, drawArea.x, drawArea.y, drawArea.width, drawArea.height, null);
        }
    }

    private void paintSoftKeys(Graphics2D g, Rectangle softKeyArea, Frame frame) {
        if (softKeyArea.width <= 0 || softKeyArea.height <= 0) {
            return;
        }

        if (frame == null || !frame.isSoftLabelVisible()) {
            return;
        }

        com.nttdocomo.ui.Font font = softKeyFontForHeight(softKeyArea.height);
        int argbColor = 0xFF1F2229;

        for (int i = 0; i < SOFT_KEYS.length; i++) {
            Rectangle section = new Rectangle(softKeyArea);
            String label = frame.getSoftLabel(SOFT_KEYS[i]);
            if (label == null || label.isBlank()) {
                continue;
            }
            paintSoftKeyLabel(g, font, argbColor, label, section, i);
        }
    }

    private void paintSoftKeyLabel(Graphics2D g, com.nttdocomo.ui.Font font, int argbColor, String label,
                                   Rectangle section, int alignmentIndex) {
        Graphics2D textGraphics = (Graphics2D) g.create();
        try {
            textGraphics.clipRect(section.x, section.y, section.width, section.height);
            int textWidth = font.stringWidth(label);
            int textY = section.y + ((section.height - font.getHeight()) / 2) + font.getAscent();
            int horizontalPadding = Math.max(4, section.height / 4);
            int textX = switch (alignmentIndex) {
                case 0 -> section.x + horizontalPadding;
                default -> section.x + Math.max(horizontalPadding, section.width - textWidth - horizontalPadding);
            };
            font.drawHostString(textGraphics, label, textX, textY, argbColor);
        } finally {
            textGraphics.dispose();
        }
    }

    private com.nttdocomo.ui.Font softKeyFontForHeight(int availableHeight) {
        int[] supportedSizes = com.nttdocomo.ui.Font.getSupportedFontSizes();
        int target = java.lang.Math.max(1, availableHeight - 2);
        int selected = supportedSizes[0];
        for (int size : supportedSizes) {
            selected = size;
            if (size >= target) {
                break;
            }
        }
        return com.nttdocomo.ui.Font.getFont(com.nttdocomo.ui.Font.FACE_SYSTEM | com.nttdocomo.ui.Font.STYLE_PLAIN, selected);
    }

    private void paintOverlays(Graphics2D g, ExternalFrameLayout layout, Frame frame, BufferedImage drawImage) {
        for (HostOverlayRenderer overlay : overlays) {
            overlay.paint(g, layout, frame, drawImage);
        }
    }
}
