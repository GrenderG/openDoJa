package com.docomostar.ui;

/** STAR sprite view over the DoJa image, geometry and visibility state. */
public class Sprite {
    final com.nttdocomo.ui.Sprite delegate;

    protected Sprite() { delegate = new com.nttdocomo.ui.Sprite() { }; }
    public Sprite(Image image) {
        try { delegate = new com.nttdocomo.ui.Sprite(Image.unwrap(image)); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public Sprite(Image image, int x, int y, int width, int height) {
        try { delegate = new com.nttdocomo.ui.Sprite(Image.unwrap(image), x, y, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setImage(Image image) {
        try { delegate.setImage(Image.unwrap(image)); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setImage(Image image, int x, int y, int width, int height) {
        try { delegate.setImage(Image.unwrap(image), x, y, width, height); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setLocation(int x, int y) { delegate.setLocation(x, y); }
    public void setVisible(boolean visible) { delegate.setVisible(visible); }
    public boolean isVisible() { return delegate.isVisible(); }
    public void setFlipMode(int mode) { delegate.setFlipMode(mode); }
    public int getX() { return delegate.getX(); }
    public int getY() { return delegate.getY(); }
    public int getWidth() { return delegate.getWidth(); }
    public int getHeight() { return delegate.getHeight(); }
    public void setRotation(int[] rotation) { delegate.setRotation(rotation); }
}
