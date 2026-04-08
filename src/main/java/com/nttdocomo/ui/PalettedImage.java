package com.nttdocomo.ui;

import com.nttdocomo.lang.UnsupportedOperationException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Defines an image whose pixel indices and palette are stored separately so
 * callers can swap palettes after creation.
 */
public class PalettedImage extends Image {
    private int width;
    private int height;
    private byte[] pixels;
    private Palette palette;
    private int transparentIndex = -1;
    private int appliedTransparentIndex = -1;
    private boolean transparentEnabled;

    /**
     * Applications cannot create this class directly.
     */
    protected PalettedImage() {
    }

    /**
     * Creates a paletted image from encoded image data held in a byte array.
     *
     * @param data the encoded image bytes
     * @return the created paletted image
     * @throws NullPointerException if {@code data} is {@code null}
     * @throws UIException if the data format is not supported
     */
    public static PalettedImage createPalettedImage(byte[] data) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        try {
            return createPalettedImage(new ByteArrayInputStream(data));
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    /**
     * Creates a paletted image from encoded image data read from a stream.
     *
     * @param inputStream the stream that supplies the encoded image
     * @return the created paletted image
     * @throws NullPointerException if {@code inputStream} is {@code null}
     * @throws IOException if the image format is not supported or the stream cannot be read
     */
    public static PalettedImage createPalettedImage(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            throw new NullPointerException("inputStream");
        }
        BufferedImage image = ImageIO.read(inputStream);
        if (image == null) {
            throw new IOException("Unsupported paletted image format");
        }
        if (image.getColorModel() instanceof IndexColorModel colorModel) {
            return fromIndexedImage(image, colorModel);
        }
        return fromArgbImage(image);
    }

    /**
     * Creates an empty paletted image with the specified size.
     *
     * @param width the image width in pixels
     * @param height the image height in pixels
     * @return the created empty paletted image
     * @throws IllegalArgumentException if either dimension is zero or negative
     */
    public static PalettedImage createPalettedImage(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid dimensions");
        }
        int[] colors = new int[256];
        PalettedImage image = new PalettedImage();
        image.width = width;
        image.height = height;
        image.pixels = new byte[width * height];
        image.palette = new Palette(colors);
        return image;
    }

    /**
     * Replaces this image's contents and palette from encoded image data read
     * from a stream.
     *
     * @param inputStream the replacement image stream
     * @throws UIException if this image has already been disposed or the data format is not supported
     */
    public void changeData(InputStream inputStream) {
        ensureActive();
        try {
            PalettedImage updated = createPalettedImage(inputStream);
            replaceWith(updated);
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    /**
     * Replaces this image's contents and palette from encoded image data held
     * in a byte array.
     *
     * @param data the replacement image bytes
     * @throws UIException if this image has already been disposed or the data format is not supported
     */
    public void changeData(byte[] data) {
        ensureActive();
        replaceWith(createPalettedImage(data));
    }

    /**
     * Sets the palette object used when this image is drawn.
     *
     * @param palette the palette to associate with this image
     * @throws NullPointerException if {@code palette} is {@code null}
     * @throws UIException if this image has already been disposed
     * @throws IllegalArgumentException if the palette entry count does not match this image
     */
    public void setPalette(Palette palette) {
        ensureActive();
        if (palette == null) {
            throw new NullPointerException("palette");
        }
        if (this.palette != null && this.palette.getEntryCount() != palette.getEntryCount()) {
            throw new IllegalArgumentException("Palette size mismatch");
        }
        this.palette = palette;
    }

    /**
     * Gets the palette currently associated with this image.
     *
     * @return the current palette
     * @throws UIException if this image has already been disposed
     */
    public Palette getPalette() {
        ensureActive();
        if (palette == null) {
            throw new UIException(UIException.ILLEGAL_STATE);
        }
        return palette;
    }

    /**
     * Paletted images do not support direct graphics access.
     *
     * @return this method never returns normally
     * @throws UnsupportedOperationException always
     */
    @Override
    public final Graphics getGraphics() {
        throw new UnsupportedOperationException("PalettedImage graphics are not supported");
    }

    /**
     * Enables or disables transparent-index processing for this image.
     *
     * @param enabled {@code true} to enable transparent-index drawing
     * @throws UIException if this image has already been disposed
     */
    @Override
    public final void setTransparentEnabled(boolean enabled) {
        ensureActive();
        this.transparentEnabled = enabled;
        this.appliedTransparentIndex = enabled ? getTransparentIndex() : -1;
    }

    /**
     * Sets the transparent palette index used by this image.
     *
     * @param index the transparent palette index
     * @throws UIException if this image has already been disposed
     * @throws ArrayIndexOutOfBoundsException if {@code index} is outside the palette index range
     */
    public void setTransparentIndex(int index) {
        ensureActive();
        if (index < 0 || index >= 256) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        this.transparentIndex = index;
        if (transparentEnabled) {
            this.appliedTransparentIndex = index;
        }
    }

    /**
     * Gets the currently configured transparent palette index.
     *
     * @return the transparent palette index, or {@code 0} if none has been set explicitly
     * @throws UIException if this image has already been disposed
     */
    public int getTransparentIndex() {
        ensureActive();
        return transparentIndex < 0 ? 0 : transparentIndex;
    }

    /**
     * Paletted images use transparent indices rather than a transparent RGB
     * color value.
     *
     * @return this method never returns normally
     * @throws UnsupportedOperationException always
     */
    @Override
    public final int getTransparentColor() {
        throw new UnsupportedOperationException("PalettedImage uses transparent indices");
    }

    /**
     * Paletted images use transparent indices rather than a transparent RGB
     * color value.
     *
     * @param color ignored
     * @throws UnsupportedOperationException always
     */
    @Override
    public final void setTransparentColor(int color) {
        throw new UnsupportedOperationException("PalettedImage uses transparent indices");
    }

    /**
     * Disposes this paletted image and releases its stored pixel and palette
     * data.
     */
    @Override
    public void dispose() {
        pixels = null;
        palette = null;
        appliedTransparentIndex = -1;
    }

    /**
     * Gets the current image width.
     *
     * @return the image width in pixels
     * @throws UIException if this image has already been disposed
     */
    @Override
    public int getWidth() {
        ensureActive();
        return width;
    }

    /**
     * Gets the current image height.
     *
     * @return the image height in pixels
     * @throws UIException if this image has already been disposed
     */
    @Override
    public int getHeight() {
        ensureActive();
        return height;
    }

    @Override
    protected BufferedImage renderForDisplayImpl() {
        if (pixels == null || palette == null) {
            return null;
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0, pos = 0; y < height; y++) {
            for (int x = 0; x < width; x++, pos++) {
                int index = pixels[pos] & 0xFF;
                int argb = palette.getArgbEntry(index);
                if (transparentEnabled && appliedTransparentIndex >= 0 && index == appliedTransparentIndex) {
                    argb &= 0x00FFFFFF;
                }
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    private void replaceWith(PalettedImage updated) {
        this.width = updated.width;
        this.height = updated.height;
        this.pixels = updated.pixels;
        this.palette = updated.palette;
        this.transparentIndex = updated.transparentIndex;
        this.appliedTransparentIndex = updated.appliedTransparentIndex;
        this.transparentEnabled = updated.transparentEnabled;
    }

    private static PalettedImage fromIndexedImage(BufferedImage image, IndexColorModel colorModel) {
        int paletteSize = colorModel.getMapSize();
        int[] colors = new int[paletteSize];
        for (int i = 0; i < paletteSize; i++) {
            colors[i] = colorModel.getRGB(i);
        }
        byte[] pixels = new byte[image.getWidth() * image.getHeight()];
        Raster raster = image.getRaster();
        for (int y = 0, pos = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++, pos++) {
                pixels[pos] = (byte) raster.getSample(x, y, 0);
            }
        }
        PalettedImage result = new PalettedImage();
        result.width = image.getWidth();
        result.height = image.getHeight();
        result.pixels = pixels;
        result.palette = new Palette(colors);
        result.transparentIndex = colorModel.getTransparentPixel();
        if (result.transparentIndex >= 0) {
            result.transparentEnabled = true;
            result.appliedTransparentIndex = result.transparentIndex;
        } else {
            result.appliedTransparentIndex = -1;
        }
        return result;
    }

    private static PalettedImage fromArgbImage(BufferedImage image) {
        int[] colors = new int[256];
        java.util.Map<Integer, Integer> indices = new java.util.LinkedHashMap<>();
        byte[] pixels = new byte[image.getWidth() * image.getHeight()];
        int nextIndex = 0;
        for (int y = 0, pos = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++, pos++) {
                int argb = image.getRGB(x, y);
                Integer existing = indices.get(argb);
                if (existing == null) {
                    if (nextIndex >= 256) {
                        existing = 0;
                    } else {
                        existing = nextIndex;
                        indices.put(argb, existing);
                        colors[nextIndex++] = argb;
                    }
                }
                pixels[pos] = existing.byteValue();
            }
        }
        int[] paletteColors = new int[java.lang.Math.max(1, indices.size())];
        for (java.util.Map.Entry<Integer, Integer> entry : indices.entrySet()) {
            paletteColors[entry.getValue()] = entry.getKey();
        }
        PalettedImage result = new PalettedImage();
        result.width = image.getWidth();
        result.height = image.getHeight();
        result.pixels = pixels;
        result.palette = new Palette(paletteColors);
        return result;
    }

    private void ensureActive() {
        if (pixels == null || palette == null) {
            throw new UIException(UIException.ILLEGAL_STATE, "PalettedImage is disposed");
        }
    }
}
