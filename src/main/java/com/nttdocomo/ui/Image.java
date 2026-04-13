package com.nttdocomo.ui;

import opendoja.host.DesktopSurface;

import java.awt.image.BufferedImage;

/**
 * Defines an image.
 * {@code Image} is the abstract base class for still images.
 */
public abstract class Image {
    /**
     * Applications cannot create this class directly.
     */
    protected Image() {
    }

    /**
     * Creates a new image.
     * The pixels of the created image are filled with the same color as the default Canvas background color.
     *
     * @param width the width of the image to create
     * @param height the height of the image to create
     * @return the created image
     * @throws IllegalArgumentException if {@code width <= 0} or {@code height <= 0}
     */
    public static Image createImage(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be > 0");
        }
        return new DesktopImage(width, height);
    }

    /**
     * Creates an image object from an RGB array.
     * This is equivalent to creating an image and writing the pixels into it.
     *
     * @param width the image width
     * @param height the image height
     * @param pixels the pixel-value array to write
     * @param offset the offset in the array
     * @return the created image object
     * @throws NullPointerException if {@code pixels} is {@code null}
     * @throws IllegalArgumentException if {@code width <= 0} or {@code height <= 0}
     * @throws ArrayIndexOutOfBoundsException if the specified range does not contain {@code width * height} values
     */
    public static Image createImage(int width, int height, int[] pixels, int offset) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be > 0");
        }
        if (pixels == null) {
            throw new NullPointerException("data");
        }
        DesktopImage image = new DesktopImage(width, height);
        image.getGraphics().setRGBPixels(0, 0, width, height, pixels, offset);
        return image;
    }

    /**
     * Gets a graphics object for drawing into this image.
     *
     * @return a graphics object for drawing into the image
     * @throws UnsupportedOperationException if this image does not support drawing
     * @throws UIException if this image has already been disposed
     */
    public Graphics getGraphics() {
        if (!(this instanceof DesktopImage desktopImage)) {
            throw new UnsupportedOperationException("This image does not support drawing");
        }
        desktopImage.ensureNotDisposed();
        return Graphics.createPlatformGraphics(desktopImage.surface());
    }

    /**
     * Gets the width of the image.
     *
     * @return the image width
     * @throws UIException if this image has already been disposed
     */
    public int getWidth() {
        if (!(this instanceof DesktopImage desktopImage)) {
            return 0;
        }
        return desktopImage.width();
    }

    /**
     * Gets the height of the image.
     *
     * @return the image height
     * @throws UIException if this image has already been disposed
     */
    public int getHeight() {
        if (!(this instanceof DesktopImage desktopImage)) {
            return 0;
        }
        return desktopImage.height();
    }

    /**
     * Disposes this image.
     * After this method is called, invoking methods other than {@code dispose()} causes {@link UIException}.
     */
    public abstract void dispose();

    /**
     * Sets the transparent color.
     * To enable transparent-color drawing, call {@link #setTransparentEnabled(boolean)} separately.
     *
     * @param color the color to use as the transparent color
     * @throws UIException if this image has already been disposed
     */
    public void setTransparentColor(int color) {
        if (this instanceof DesktopImage desktopImage) {
            desktopImage.ensureNotDisposed();
            desktopImage.setTransparentColor(color);
        }
    }

    /**
     * Gets the currently configured transparent color.
     *
     * @return the configured transparent color
     * @throws UIException if this image has already been disposed
     */
    public int getTransparentColor() {
        if (this instanceof DesktopImage desktopImage) {
            return desktopImage.getTransparentColor();
        }
        return 0;
    }

    /**
     * Enables or disables transparent-color drawing.
     *
     * @param enabled {@code true} to enable transparent-color drawing, {@code false} to disable it
     * @throws UIException if this image has already been disposed
     */
    public void setTransparentEnabled(boolean enabled) {
        if (this instanceof DesktopImage desktopImage) {
            desktopImage.ensureNotDisposed();
            desktopImage.setTransparentEnabled(enabled);
        }
    }

    /**
     * Sets the alpha value of this image.
     *
     * @param alpha the alpha value to set, from 0 to 255
     * @throws UIException if this image has already been disposed
     * @throws IllegalArgumentException if {@code alpha} is outside {@code [0, 255]}
     */
    public void setAlpha(int alpha) {
        if (this instanceof DesktopImage desktopImage) {
            desktopImage.ensureNotDisposed();
            desktopImage.setAlpha(alpha);
        }
    }

    /**
     * Gets the alpha value configured for this image.
     *
     * @return the configured alpha value
     * @throws UIException if this image has already been disposed
     */
    public int getAlpha() {
        if (this instanceof DesktopImage desktopImage) {
            return desktopImage.getAlpha();
        }
        return 255;
    }

    BufferedImage renderForDisplay() {
        return renderForDisplayImpl();
    }

    /**
     * Renders this image into a platform image for drawing or encoding.
     */
    protected BufferedImage renderForDisplayImpl() {
        if (this instanceof DesktopImage desktopImage) {
            return desktopImage.renderImage();
        }
        return null;
    }

    /**
     * Renders another image through the same internal display path.
     */
    protected final BufferedImage renderImage(Image image) {
        return image == null ? null : image.renderForDisplay();
    }
}

final class DesktopImage extends Image {
    private final DesktopSurface surface;
    private int transparentColor;
    private boolean transparentEnabled;
    private int alpha = 255;
    private boolean disposed;

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
        ensureNotDisposed();
        return surface;
    }

    int width() {
        return surface.width();
    }

    int height() {
        return surface.height();
    }

    @Override
    public void dispose() {
        disposed = true;
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
        if (alpha < 0 || alpha > 255) {
            throw new IllegalArgumentException("alpha out of range: " + alpha);
        }
        this.alpha = alpha;
    }

    @Override
    public int getAlpha() {
        return alpha;
    }

    BufferedImage renderImage() {
        // Some legacy titles dispose temporary images immediately after handing them to the
        // current paint pass. Keep read-only pixel access alive after dispose, but continue to
        // reject mutating operations through ensureNotDisposed()/surface().
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

    void ensureNotDisposed() {
        if (disposed) {
            throw new UIException(UIException.ILLEGAL_STATE, "Image is disposed");
        }
    }
}
