package com.docomostar.ui;

/** STAR image objects share the host's pixels, transparency and offscreen graphics. */
public abstract class Image {
    final com.nttdocomo.ui.Image delegate;

    protected Image() { this(null); }
    protected Image(com.nttdocomo.ui.Image delegate) { this.delegate = delegate; }

    /** Host integration: share the original pixels and resource lifetime. */
    public static Image wrap(com.nttdocomo.ui.Image image) {
        return image == null ? null : new HostImage(image);
    }
    public static com.nttdocomo.ui.Image unwrap(Image image) {
        return java.util.Objects.requireNonNull(image).delegate;
    }

    public static Image createImage(int width, int height) {
        try { return new HostImage(com.nttdocomo.ui.Image.createImage(width, height)); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public static Image createImage(int width, int height, int[] pixels, int offset) {
        try { return new HostImage(com.nttdocomo.ui.Image.createImage(width, height, pixels, offset)); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public Graphics getGraphics() {
        try { return Graphics.wrap(delegate.getGraphics()); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public int getWidth() {
        try { return delegate.getWidth(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public int getHeight() {
        try { return delegate.getHeight(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public abstract void dispose();
    public void setTransparentColor(int color) {
        try { delegate.setTransparentColor(color); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public int getTransparentColor() {
        try { return delegate.getTransparentColor(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setTransparentEnabled(boolean enabled) {
        try { delegate.setTransparentEnabled(enabled); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setAlpha(int alpha) {
        try { delegate.setAlpha(alpha); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public int getAlpha() {
        try { return delegate.getAlpha(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }

    private static final class HostImage extends Image {
        HostImage(com.nttdocomo.ui.Image delegate) { super(delegate); }
        @Override public void dispose() { delegate.dispose(); }
    }
}
