package com.nttdocomo.opt.ui;

/**
 * Defines a palette that stores an arbitrary number of colors.
 * <p>
 * This class is provided as a compatibility API for DoJa 2.0 variants and
 * older content that still references {@code com.nttdocomo.opt.ui.Palette}.
 * </p>
 * <p>
 * Support may depend on the handset. If unsupported, a method throws
 * {@link com.nttdocomo.lang.UnsupportedOperationException} when called.
 * </p>
 *
 * @see PalettedImage
 */
public class Palette {
    private static final int RED_565_MASK = 0xF800;
    private static final int GREEN_565_MASK = 0x07E0;
    private static final int BLUE_565_MASK = 0x001F;

    private final int[] entries;

    /**
     * Creates a palette object with the specified number of entries.
     * Each entry is initialized to {@code 0}.
     * <p>
     * Since {@link PalettedImage} image data is generated from GIF images, a
     * paletted image can use at most 256 colors. Specifying more than 256
     * entries is therefore meaningless, because entries at index 256 and above
     * are never referenced during drawing.
     * </p>
     *
     * @param n the number of entries
     * @throws IllegalArgumentException if {@code n} is {@code 0} or negative
     */
    public Palette(int n) {
        if (n <= 0) {
            throw new IllegalArgumentException("n");
        }
        this.entries = new int[n];
    }

    /**
     * Creates a palette object from an array that contains entry values.
     * The created palette has {@code colors.length} entries.
     * <p>
     * Since {@link PalettedImage} image data is generated from GIF images, a
     * paletted image can use at most 256 colors. Specifying an array longer
     * than 256 is therefore meaningless, because entries at index 256 and
     * above are never referenced during drawing.
     * </p>
     * <p>
     * This constructor copies the values in {@code colors}, so changes made to
     * that array after the palette is created are not reflected in the
     * palette.
     * </p>
     *
     * @param colors the array that contains palette entry values; use values
     *               returned by methods such as
     *               {@link com.nttdocomo.ui.Graphics#getColorOfRGB(int, int, int)}
     * @throws NullPointerException if {@code colors} is {@code null}
     */
    public Palette(int[] colors) {
        if (colors == null) {
            throw new NullPointerException("colors");
        }
        this.entries = new int[colors.length];
        for (int i = 0; i < colors.length; i++) {
            this.entries[i] = normalizeEntry(colors[i]);
        }
    }

    /**
     * Returns the number of entries.
     *
     * @return the number of entries
     */
    public int getEntryCount() {
        return entries.length;
    }

    /**
     * Sets the color of a palette entry.
     *
     * @param index the index of the entry whose color is set
     * @param color the color to set for the palette entry; use a value
     *              returned by methods such as
     *              {@link com.nttdocomo.ui.Graphics#getColorOfRGB(int, int, int)}
     * @throws ArrayIndexOutOfBoundsException if {@code index} is negative or
     *                                        greater than or equal to the
     *                                        number of entries
     * @throws IllegalArgumentException if {@code color} is invalid
     */
    public void setEntry(int index, int color) {
        entries[index] = normalizeEntry(color);
    }

    /**
     * Returns the color of a palette entry.
     *
     * @param index the index of the entry whose color is returned
     * @return the color of the palette entry
     * @throws ArrayIndexOutOfBoundsException if {@code index} is negative or
     *                                        greater than or equal to the
     *                                        number of entries
     */
    public int getEntry(int index) {
        return entries[index];
    }

    int getArgbEntry(int index) {
        return expandRgb565(entries[index]);
    }

    private static int normalizeEntry(int color) {
        if ((color & 0xFFFF0000) == 0) {
            return color & 0xFFFF;
        }

        int alpha = (color >>> 24) & 0xFF;
        if (alpha != 0x00 && alpha != 0xFF) {
            throw new IllegalArgumentException("color");
        }

        int red = (color >>> 16) & 0xFF;
        int green = (color >>> 8) & 0xFF;
        int blue = color & 0xFF;
        return ((red & 0xF8) << 8)
                | ((green & 0xFC) << 3)
                | ((blue & 0xF8) >>> 3);
    }

    private static int expandRgb565(int color) {
        int red = (color & RED_565_MASK) >>> 11;
        int green = (color & GREEN_565_MASK) >>> 5;
        int blue = color & BLUE_565_MASK;
        red = (red << 3) | (red >>> 2);
        green = (green << 2) | (green >>> 4);
        blue = (blue << 3) | (blue >>> 2);
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
}
