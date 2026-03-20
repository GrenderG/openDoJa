package com.nttdocomo.ui;

import opendoja.host.DesktopSurface;

import java.awt.image.BufferedImage;

public abstract class Image {
    public static Image createImage(int width, int height) {
        return new DesktopImage(width, height);
    }

    public static Image createImage(int width, int height, int[] pixels, int offset) {
        DesktopImage image = new DesktopImage(width, height);
        int[] rgb = pixels == null ? new int[width * height] : pixels;
        image.surface().image().setRGB(0, 0, width, height, rgb, offset, width);
        return image;
    }

    public Graphics getGraphics() {
        if (!(this instanceof DesktopImage desktopImage)) {
            throw new IllegalStateException("Unsupported image implementation");
        }
        return new Graphics(desktopImage.surface());
    }

    public int getWidth() {
        if (!(this instanceof DesktopImage desktopImage)) {
            return 0;
        }
        return desktopImage.surface().width();
    }

    public int getHeight() {
        if (!(this instanceof DesktopImage desktopImage)) {
            return 0;
        }
        return desktopImage.surface().height();
    }

    public abstract void dispose();

    public void setTransparentColor(int color) {
        if (this instanceof DesktopImage desktopImage) {
            desktopImage.setTransparentColor(color);
        }
    }

    public int getTransparentColor() {
        if (this instanceof DesktopImage desktopImage) {
            return desktopImage.getTransparentColor();
        }
        return 0;
    }

    public void setTransparentEnabled(boolean enabled) {
        if (this instanceof DesktopImage desktopImage) {
            desktopImage.setTransparentEnabled(enabled);
        }
    }

    public void setAlpha(int alpha) {
        if (this instanceof DesktopImage desktopImage) {
            desktopImage.setAlpha(alpha);
        }
    }

    public int getAlpha() {
        if (this instanceof DesktopImage desktopImage) {
            return desktopImage.getAlpha();
        }
        return 255;
    }

    BufferedImage renderForDisplay() {
        if (this instanceof DesktopImage desktopImage) {
            return desktopImage.renderImage();
        }
        return null;
    }
}

final class DesktopImage extends Image {
    private final DesktopSurface surface;
    private int transparentColor;
    private boolean transparentEnabled;
    private int alpha = 255;

    DesktopImage(int width, int height) {
        this.surface = new DesktopSurface(width, height);
    }

    DesktopImage(BufferedImage bufferedImage) {
        this.surface = new DesktopSurface(bufferedImage.getWidth(), bufferedImage.getHeight());
        java.awt.Graphics2D g2 = this.surface.image().createGraphics();
        try {
            g2.drawImage(bufferedImage, 0, 0, null);
        } finally {
            g2.dispose();
        }
    }

    DesktopSurface surface() {
        return surface;
    }

    @Override
    public void dispose() {
    }

    @Override
    public void setTransparentColor(int transparentColor) {
        this.transparentColor = transparentColor;
    }

    @Override
    public int getTransparentColor() {
        return transparentColor;
    }

    @Override
    public void setTransparentEnabled(boolean transparentEnabled) {
        this.transparentEnabled = transparentEnabled;
    }

    @Override
    public void setAlpha(int alpha) {
        this.alpha = Math.max(0, Math.min(255, alpha));
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    BufferedImage renderImage() {
        if (!transparentEnabled) {
            return surface.image();
        }
        BufferedImage copy = new BufferedImage(surface.width(), surface.height(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < surface.height(); y++) {
            for (int x = 0; x < surface.width(); x++) {
                int rgb = surface.image().getRGB(x, y);
                if (rgb == transparentColor) {
                    copy.setRGB(x, y, 0);
                } else {
                    copy.setRGB(x, y, rgb);
                }
            }
        }
        return copy;
    }
}
