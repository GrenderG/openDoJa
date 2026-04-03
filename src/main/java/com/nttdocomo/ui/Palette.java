package com.nttdocomo.ui;

/**
 * Defines the palette object used by {@link PalettedImage}.
 */
public class Palette {
    private final int[] entries;

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
        System.arraycopy(entries, 0, this.entries, 0, size);
    }

    /**
     * Gets a palette entry as an ARGB color.
     *
     * @param index the palette index
     * @return the palette color
     */
    public int getEntry(int index) {
        return entries[index] | 0xFF000000;
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
        entries[index] = color;
    }

    int[] copyEntries() {
        return entries.clone();
    }
}
