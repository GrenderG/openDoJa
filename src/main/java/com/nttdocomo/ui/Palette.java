package com.nttdocomo.ui;

/**
 * Defines the palette object used by {@link PalettedImage}.
 */
public class Palette {
    private static final int RED_565_MASK = 0xF800;
    private static final int GREEN_565_MASK = 0x07E0;
    private static final int BLUE_565_MASK = 0x001F;

    private final int[] entries;
    private volatile long revision;

    /**
     * Creates a palette with the specified number of entries.
     *
     * @param count the palette-entry count
     * @throws IllegalArgumentException if {@code count} is zero or negative
     */
    public Palette(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count");
        }
        this.entries = new int[count];
    }

    /**
     * Creates a palette by copying the supplied entry array.
     *
     * @param entries the palette entries
     * @throws NullPointerException if {@code entries} is {@code null}
     * @throws IllegalArgumentException if the array is empty
     */
    public Palette(int[] entries) {
        if (entries == null) {
            throw new NullPointerException("entries");
        }
        if (entries.length == 0) {
            throw new IllegalArgumentException("entries");
        }
        int size = java.lang.Math.min(256, entries.length);
        this.entries = new int[size];
        for (int i = 0; i < size; i++) {
            this.entries[i] = normalizeEntry(entries[i]);
        }
    }

    /**
     * Gets a palette entry in the palette's device-facing color encoding.
     *
     * @param index the palette index
     * @return the palette color
     */
    public int getEntry(int index) {
        return entries[index];
    }

    /**
     * Gets the number of palette entries.
     *
     * @return the palette-entry count
     */
    public int getEntryCount() {
        return entries.length;
    }

    /**
     * Sets a palette entry.
     *
     * @param index the palette index
     * @param color the color value
     */
    public void setEntry(int index, int color) {
        int normalized = normalizeEntry(color);
        if (entries[index] != normalized) {
            entries[index] = normalized;
            revision++;
        }
    }

    final long revision() {
        return revision;
    }

    int[] copyEntries() {
        return entries.clone();
    }

    int getArgbEntry(int index) {
        return expandRgb565(entries[index]);
    }

    private static int normalizeEntry(int color) {
        // DoJa palette APIs expose device-facing color integers. FF1 reads palette entries
        // back and applies RGB565 masks directly, so paletted images must store that format
        // even though desktop rendering ultimately needs full ARGB pixels.
        if ((color & 0xFFFF0000) == 0) {
            return color & 0xFFFF;
        }
        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return ((red & 0xF8) << 8)
                | ((green & 0xFC) << 3)
                | ((blue & 0xF8) >>> 3);
    }

    private static int expandRgb565(int color) {
        // Rendering happens on a normal ARGB BufferedImage, so expand the stored device color
        // back to 8-bit channels only at draw time.
        int red = (color & RED_565_MASK) >>> 11;
        int green = (color & GREEN_565_MASK) >>> 5;
        int blue = color & BLUE_565_MASK;
        red = (red << 3) | (red >>> 2);
        green = (green << 2) | (green >>> 4);
        blue = (blue << 3) | (blue >>> 2);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
}
