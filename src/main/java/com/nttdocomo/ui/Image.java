package com.nttdocomo.ui;

import com.nttdocomo.lang.UnsupportedOperationException;
import opendoja.host.DoJaProfile;
import opendoja.host.ApplicationProfile;
import opendoja.host.DesktopSurface;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

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
        Graphics graphics = image.getGraphics();
        try {
            graphics.setRGBPixels(0, 0, width, height, pixels, offset);
        } finally {
            graphics.dispose();
        }
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
        if (this instanceof DesktopImage desktopImage) {
            return desktopImage.graphics();
        }
        throw new UnsupportedOperationException("This image does not support drawing");
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
    private static final int DEFAULT_MUTABLE_IMAGE_BACKGROUND = Graphics.getColorOfName(Graphics.WHITE);
    private final DesktopSurface surface;
    private final boolean mutable;
    private final BufferedImage opaqueImage;
    private final boolean originalTransparencyPresent;
    private final boolean transparentGifImage;
    private final List<Graphics> liveGraphics = new ArrayList<>();
    private int transparentColor;
    private int appliedTransparentColor;
    private boolean transparentEnabled;
    private boolean originalTransparencySuppressed;
    private int alpha = 255;
    private boolean disposed;

    DesktopImage(int width, int height) {
        this.surface = new DesktopSurface(width, height);
        this.mutable = true;
        this.opaqueImage = null;
        this.originalTransparencyPresent = false;
        this.transparentGifImage = false;
        this.surface.setBackgroundColor(DEFAULT_MUTABLE_IMAGE_BACKGROUND);
        Graphics2D g2 = this.surface.image().createGraphics();
        try {
            g2.setColor(new Color(DEFAULT_MUTABLE_IMAGE_BACKGROUND, true));
            g2.fillRect(0, 0, width, height);
        } finally {
            g2.dispose();
        }
    }

    DesktopImage(BufferedImage bufferedImage) {
        this(bufferedImage, false);
    }

    DesktopImage(BufferedImage bufferedImage, boolean transparentGifImage) {
        this.surface = new DesktopSurface(bufferedImage.getWidth(), bufferedImage.getHeight());
        this.mutable = false;
        this.transparentGifImage = transparentGifImage;
        this.originalTransparencyPresent = transparentGifImage && hasTransparentPixels(bufferedImage);
        this.opaqueImage = originalTransparencyPresent ? copyOpaqueImage(bufferedImage) : null;
        this.surface.setBackgroundColor(DEFAULT_MUTABLE_IMAGE_BACKGROUND);
        Graphics2D g2 = this.surface.image().createGraphics();
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
        ensureNotDisposed();
        return surface.width();
    }

    int height() {
        ensureNotDisposed();
        return surface.height();
    }

    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        List<Graphics> graphicsSnapshot = new ArrayList<>(liveGraphics);
        liveGraphics.clear();
        for (Graphics graphics : graphicsSnapshot) {
            graphics.setDisposeHook(null);
            graphics.dispose();
        }
        disposed = true;
    }

    @Override
    public void setTransparentColor(int transparentColor) {
        ensureNotDisposed();
        this.transparentColor = 0xFF000000 | (transparentColor & 0x00FFFFFF);
    }

    @Override
    public int getTransparentColor() {
        ensureNotDisposed();
        return transparentColor;
    }

    @Override
    public void setTransparentEnabled(boolean transparentEnabled) {
        ensureNotDisposed();
        if (!supportsImageAlpha()) {
            return;
        }
        if (!mutable && transparentGifImage && originalTransparencyPresent && transparentEnabled) {
            originalTransparencySuppressed = true;
        }
        this.transparentEnabled = transparentEnabled;
        if (transparentEnabled) {
            appliedTransparentColor = transparentColor;
        }
    }

    @Override
    public void setAlpha(int alpha) {
        ensureNotDisposed();
        if (alpha < 0 || alpha > 255) {
            throw new IllegalArgumentException("alpha out of range: " + alpha);
        }
        if (!supportsImageAlpha()) {
            return;
        }
        this.alpha = alpha;
    }

    @Override
    public int getAlpha() {
        ensureNotDisposed();
        if (!supportsImageAlpha()) {
            return 255;
        }
        return alpha;
    }

    BufferedImage renderImage() {
        ensureNotDisposed();
        if (mutable) {
            for (Graphics graphics : new ArrayList<>(liveGraphics)) {
                // Offscreen images can be rendered through the hardware OpenGLES path without ever
                // going through an unlock/present boundary. Flush that pending 3D state back into
                // the image's software pixels before another Graphics reads the backing image.
                graphics.syncOffscreenSurfaceForReadback();
            }
        }
        BufferedImage renderedSource = renderSourceImage();
        if (!transparentEnabled) {
            return renderedSource;
        }
        BufferedImage copy = new BufferedImage(renderedSource.getWidth(), renderedSource.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < renderedSource.getHeight(); y++) {
            for (int x = 0; x < renderedSource.getWidth(); x++) {
                int rgb = renderedSource.getRGB(x, y);
                if (rgb == appliedTransparentColor) {
                    copy.setRGB(x, y, 0);
                } else {
                    copy.setRGB(x, y, rgb);
                }
            }
        }
        return copy;
    }

    void ensureNotDisposed() {
        // DoJa-2.0+ mandates ILLEGAL_STATE after dispose(); DoJa-1.0 leaves use-after-dispose
        // machine-dependent, so keep legacy access available on pre-2.0 profiles.
        if (disposed && (ApplicationProfile.current() == ApplicationProfile.STAR
                || DoJaProfile.current().isAtLeast(2, 0))) {
            throw new UIException(UIException.ILLEGAL_STATE, "Image is disposed");
        }
    }

    Graphics graphics() {
        ensureNotDisposed();
        if (!mutable) {
            throw new UnsupportedOperationException("This image does not support drawing");
        }
        Graphics graphics = Graphics.createPlatformGraphics(surface);
        graphics.setSoftwareMutationHook(this::onMutableImageDraw);
        graphics.setCopyHook(this::registerLiveGraphics);
        registerLiveGraphics(graphics);
        return graphics;
    }

    private void onMutableImageDraw() {
        transparentEnabled = false;
    }

    private void registerLiveGraphics(Graphics graphics) {
        liveGraphics.add(graphics);
        graphics.setDisposeHook(() -> liveGraphics.remove(graphics));
    }

    private BufferedImage renderSourceImage() {
        if (!mutable && originalTransparencyPresent && originalTransparencySuppressed) {
            return opaqueImage;
        }
        return surface.image();
    }

    BufferedImage copyOpaqueSourceForTransparentImage() {
        if (disposed) {
            throw new UIException(UIException.ILLEGAL_STATE, "Image is disposed");
        }
        // TransparentImage copies the decoded pixels directly and must not inherit any active
        // alpha or transparent-color rendering state from this Image instance.
        return copyOpaqueImage(surface.image());
    }

    private static boolean supportsImageAlpha() {
        return ApplicationProfile.current() == ApplicationProfile.STAR || DoJaProfile.current().isAtLeast(5, 0);
    }

    private static boolean hasTransparentPixels(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if ((image.getRGB(x, y) >>> 24) != 0xFF) {
                    return true;
                }
            }
        }
        return false;
    }

    private static BufferedImage copyOpaqueImage(BufferedImage image) {
        BufferedImage opaque = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                opaque.setRGB(x, y, 0xFF000000 | (image.getRGB(x, y) & 0x00FFFFFF));
            }
        }
        return opaque;
    }
}
