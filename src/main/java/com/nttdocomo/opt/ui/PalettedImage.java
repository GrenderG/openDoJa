package com.nttdocomo.opt.ui;

import com.nttdocomo.lang.UnsupportedOperationException;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.Image;
import com.nttdocomo.ui.UIException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;

/**
 * Defines an image whose palette can be replaced.
 * <p>
 * This class is provided as a compatibility API for DoJa 2.0 variants and
 * older content that still references
 * {@code com.nttdocomo.opt.ui.PalettedImage}.
 * </p>
 * <p>
 * A {@code PalettedImage} object keeps image data and palette data
 * separately, so a differently colored image can be drawn later by swapping
 * the palette.
 * The drawing methods that accept {@code PalettedImage} are device-dependent.
 * If a {@code PalettedImage} object is passed to a drawing method that does
 * not support it, {@link UIException} with
 * {@link UIException#UNSUPPORTED_FORMAT} occurs.
 * </p>
 * <p>
 * Support may depend on the handset. If unsupported, a method throws
 * {@link UnsupportedOperationException} when called.
 * </p>
 *
 * @see Palette
 */
public class PalettedImage extends Image {
    private static final byte[] GIF87A = {'G', 'I', 'F', '8', '7', 'a'};
    private static final byte[] GIF89A = {'G', 'I', 'F', '8', '9', 'a'};

    private int width;
    private int height;
    private byte[] pixels;
    private Palette palette;
    private int minimumPaletteEntries;
    private int transparentIndex = -1;

    /**
     * Applications cannot create instances of this class directly.
     */
    protected PalettedImage() {
    }

    /**
     * Creates an image whose palette can be set.
     * Image information and a palette object are generated from the GIF image
     * binary data.
     * The generated palette object has the same color-table order and size as
     * the color table recorded in the GIF header.
     * Image information is stored as indexed-color data with up to 256 colors,
     * and drawing restores colors using the currently configured palette.
     * <br>
     * The palette size obtained by {@code createPalettedImage} (the original
     * GIF color count) is recorded in the returned {@code PalettedImage}
     * object, and a palette smaller than that color count cannot be set later.
     * <br>
     * Behavior is device-dependent if an animated GIF is specified.
     * Behavior is also device-dependent if the original GIF uses a transparent
     * color.
     *
     * @param data the image data; specify a byte array that contains the GIF
     *             image binary data as-is
     * @return the image whose palette can be set
     * @throws NullPointerException if {@code data} is {@code null}
     * @throws UIException if {@code data} is invalid
     *                     ({@link UIException#UNSUPPORTED_FORMAT})
     */
    public static PalettedImage createPalettedImage(byte[] data) {
        if (data == null) {
            throw new NullPointerException("data");
        }
        if (!isGif(data)) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, "data");
        }

        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null || !(image.getColorModel() instanceof IndexColorModel colorModel)) {
                throw new UIException(UIException.UNSUPPORTED_FORMAT, "data");
            }
            return fromIndexedGif(image, colorModel);
        } catch (UIException e) {
            throw e;
        } catch (Exception e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    /**
     * Sets the palette.
     * This method copies the reference to the {@link Palette} object.
     * Therefore, if {@link Palette#setEntry(int, int)} is called on that
     * {@link Palette} object after this method returns, the palette change is
     * reflected the next time the image is drawn.
     *
     * @param palette the palette object
     * @throws NullPointerException if {@code palette} is {@code null}
     * @throws IllegalArgumentException if {@code palette} has fewer entries
     *                                  than the palette recorded when this
     *                                  image was created
     */
    public void setPalette(Palette palette) {
        ensureActive();
        if (palette == null) {
            throw new NullPointerException("palette");
        }
        if (palette.getEntryCount() < minimumPaletteEntries) {
            throw new IllegalArgumentException("palette");
        }
        this.palette = palette;
    }

    /**
     * Returns the palette.
     *
     * @return the palette object
     */
    public Palette getPalette() {
        ensureActive();
        return palette;
    }

    /**
     * This method cannot be called for instances of this class.
     *
     * @return a graphics object for drawing into the image
     * @throws UnsupportedOperationException if this method is called
     */
    @Override
    public final Graphics getGraphics() {
        throw new UnsupportedOperationException("PalettedImage does not support getGraphics()");
    }

    @Override
    public void dispose() {
        pixels = null;
        palette = null;
    }

    @Override
    public int getWidth() {
        ensureActive();
        return width;
    }

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
                if (index == transparentIndex) {
                    argb &= 0x00FFFFFF;
                }
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }

    private static PalettedImage fromIndexedGif(BufferedImage image, IndexColorModel colorModel) {
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
        result.minimumPaletteEntries = paletteSize;
        result.transparentIndex = colorModel.getTransparentPixel();
        return result;
    }

    private static boolean isGif(byte[] data) {
        return hasHeader(data, GIF87A) || hasHeader(data, GIF89A);
    }

    private static boolean hasHeader(byte[] data, byte[] header) {
        if (data.length < header.length) {
            return false;
        }
        for (int i = 0; i < header.length; i++) {
            if (data[i] != header[i]) {
                return false;
            }
        }
        return true;
    }

    private void ensureActive() {
        if (pixels == null || palette == null) {
            throw new UIException(UIException.ILLEGAL_STATE, "PalettedImage is disposed");
        }
    }
}
