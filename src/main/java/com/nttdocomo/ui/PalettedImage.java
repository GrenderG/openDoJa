package com.nttdocomo.ui;

import com.nttdocomo.lang.UnsupportedOperationException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.Raster;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

public class PalettedImage extends Image {
    private int width;
    private int height;
    private byte[] pixels;
    private Palette palette;
    private int transparentIndex = -1;
    private boolean transparentEnabled = true;

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

    public void changeData(InputStream inputStream) {
        try {
            PalettedImage updated = createPalettedImage(inputStream);
            replaceWith(updated);
        } catch (IOException e) {
            throw new UIException(UIException.UNSUPPORTED_FORMAT, e.getMessage());
        }
    }

    public void changeData(byte[] data) {
        replaceWith(createPalettedImage(data));
    }

    public void setPalette(Palette palette) {
        if (palette == null) {
            throw new NullPointerException("palette");
        }
        if (this.palette != null && this.palette.getEntryCount() != palette.getEntryCount()) {
            throw new IllegalArgumentException("Palette size mismatch");
        }
        this.palette = palette;
    }

    public Palette getPalette() {
        if (palette == null) {
            throw new UIException(UIException.ILLEGAL_STATE);
        }
        return palette;
    }

    @Override
    public final Graphics getGraphics() {
        throw new UnsupportedOperationException("PalettedImage graphics are not supported");
    }

    @Override
    public final void setTransparentEnabled(boolean enabled) {
        this.transparentEnabled = enabled;
    }

    public void setTransparentIndex(int index) {
        if (palette != null && (index < 0 || index >= palette.getEntryCount())) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
        this.transparentIndex = index;
    }

    public int getTransparentIndex() {
        return transparentIndex < 0 ? 0 : transparentIndex;
    }

    @Override
    public final int getTransparentColor() {
        throw new UnsupportedOperationException("PalettedImage uses transparent indices");
    }

    @Override
    public final void setTransparentColor(int color) {
        throw new UnsupportedOperationException("PalettedImage uses transparent indices");
    }

    @Override
    public void dispose() {
        pixels = null;
        palette = null;
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    BufferedImage renderForDisplay() {
        if (pixels == null || palette == null) {
            return null;
        }
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0, pos = 0; y < height; y++) {
            for (int x = 0; x < width; x++, pos++) {
                int index = pixels[pos] & 0xFF;
                int argb = palette.getEntry(index);
                if (transparentEnabled && transparentIndex >= 0 && index == transparentIndex) {
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
}
