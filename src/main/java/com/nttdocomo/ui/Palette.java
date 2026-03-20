package com.nttdocomo.ui;

public class Palette {
    private final int[] entries;

    public Palette(int count) {
        if (count <= 0) {
            throw new IllegalArgumentException("count");
        }
        this.entries = new int[count];
    }

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

    public int getEntry(int index) {
        return entries[index] | 0xFF000000;
    }

    public int getEntryCount() {
        return entries.length;
    }

    public void setEntry(int index, int color) {
        entries[index] = color;
    }

    int[] copyEntries() {
        return entries.clone();
    }
}
