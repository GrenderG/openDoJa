package com.docomostar.ui;

public class Palette {
    final com.nttdocomo.ui.Palette delegate;
    public Palette(int count) { this(new com.nttdocomo.ui.Palette(count)); }
    public Palette(int[] colors) { this(new com.nttdocomo.ui.Palette(colors)); }
    Palette(com.nttdocomo.ui.Palette delegate) { this.delegate = delegate; }
    public int getEntry(int index) { return delegate.getEntry(index); }
    public int getEntryCount() { return delegate.getEntryCount(); }
    public void setEntry(int index, int color) { delegate.setEntry(index, color); }
}
