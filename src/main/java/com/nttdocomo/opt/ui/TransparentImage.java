package com.nttdocomo.opt.ui;

import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;

/**
 * Defines an image with adjustable transparency.
 */
public class TransparentImage extends Image {
    private final Image delegate;

    /**
     * Applications cannot create this object directly.
     */
    protected TransparentImage() {
        this.delegate = Image.createImage(1, 1);
    }

    /**
     * Creates a transparent image by copying the specified source image.
     *
     * @param image the source image
     * @return the new transparent image
     */
    public static TransparentImage createTransparentImage(Image image) {
        if (image == null) {
            throw new NullPointerException("image");
        }
        return new TransparentImage(image);
    }

    private TransparentImage(Image source) {
        this.delegate = Image.createImage(source.getWidth(), source.getHeight());
        Graphics g = delegate.getGraphics();
        try {
            g.drawImage(source, 0, 0);
        } finally {
            g.dispose();
        }
    }

    /**
     * Enables or disables transparent-color processing.
     *
     * @param enabled {@code true} to enable transparent-color processing
     */
    public void setTransparentEnabled(boolean enabled) {
        delegate.setTransparentEnabled(enabled);
    }

    /**
     * Sets the transparent color.
     *
     * @param color the transparent color
     */
    public void setTransparentColor(int color) {
        delegate.setTransparentColor(color);
    }

    /**
     * Returns the transparent color.
     *
     * @return the transparent color
     */
    public int getTransparentColor() {
        return delegate.getTransparentColor();
    }

    /**
     * Sets the alpha value.
     *
     * @param alpha the alpha value
     */
    public void setAlpha(int alpha) {
        delegate.setAlpha(alpha);
    }

    /**
     * Returns the alpha value.
     *
     * @return the alpha value
     */
    public int getAlpha() {
        return delegate.getAlpha();
    }

    /**
     * Returns a graphics object for drawing onto the image.
     *
     * @return the graphics object
     */
    public final Graphics getGraphics() {
        return delegate.getGraphics();
    }

    /**
     * Disposes this object and releases its resources.
     */
    @Override
    public void dispose() {
        delegate.dispose();
    }

    /**
     * Gets width.
     */
    @Override
    public int getWidth() {
        return delegate.getWidth();
    }

    /**
     * Gets height.
     */
    @Override
    public int getHeight() {
        return delegate.getHeight();
    }
}
