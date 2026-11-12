package com.docomostar.ui;

import java.io.InputStream;
import java.io.IOException;

public abstract class PalettedImage extends Image {
    private Palette palette;

    protected PalettedImage() { }
    private PalettedImage(com.nttdocomo.ui.PalettedImage delegate) { super(delegate); }
    private com.nttdocomo.ui.PalettedImage hostImage() { return (com.nttdocomo.ui.PalettedImage) delegate; }

    public static PalettedImage createPalettedImage(byte[] data) {
        try { return new HostPalettedImage(com.nttdocomo.ui.PalettedImage.createPalettedImage(data)); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public static PalettedImage createPalettedImage(InputStream stream) {
        try { return new HostPalettedImage(com.nttdocomo.ui.PalettedImage.createPalettedImage(stream)); }
        catch (IOException failure) {
            UIException translated = new UIException(UIException.UNSUPPORTED_FORMAT, failure.getMessage());
            translated.initCause(failure);
            throw translated;
        }
    }
    public static PalettedImage createPalettedImage(int width, int height) {
        return new HostPalettedImage(com.nttdocomo.ui.PalettedImage.createPalettedImage(width, height));
    }
    public void changeData(InputStream stream) {
        try { hostImage().changeData(stream); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void changeData(byte[] data) {
        try { hostImage().changeData(data); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setPalette(Palette palette) {
        try {
            hostImage().setPalette(java.util.Objects.requireNonNull(palette).delegate);
            this.palette = palette;
        } catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public Palette getPalette() {
        try {
            var hostPalette = hostImage().getPalette();
            if (palette == null || palette.delegate != hostPalette) palette = new Palette(hostPalette);
            return palette;
        } catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    @Override public final Graphics getGraphics() { return super.getGraphics(); }
    @Override public final void setTransparentEnabled(boolean enabled) { super.setTransparentEnabled(enabled); }
    public void setTransparentIndex(int index) {
        try { hostImage().setTransparentIndex(index); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public int getTransparentIndex() {
        try { return hostImage().getTransparentIndex(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    @Override public final int getTransparentColor() { return super.getTransparentColor(); }
    @Override public final void setTransparentColor(int color) { super.setTransparentColor(color); }
    @Override public void dispose() { delegate.dispose(); }

    private static final class HostPalettedImage extends PalettedImage {
        HostPalettedImage(com.nttdocomo.ui.PalettedImage delegate) { super(delegate); }
    }
}
