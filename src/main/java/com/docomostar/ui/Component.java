package com.docomostar.ui;

/** STAR component identity around a shared high-level DoJa widget. */
public abstract class Component {
    final com.nttdocomo.ui.Component delegate;
    protected Component() { this(new com.nttdocomo.ui.Component() { }); }
    Component(com.nttdocomo.ui.Component delegate) { this.delegate = delegate; }
    public final int getHeight() { return delegate.getHeight(); }
    public final int getWidth() { return delegate.getWidth(); }
    public int getX() { return delegate.getX(); }
    public int getY() { return delegate.getY(); }
    public void setBackground(int color) { delegate.setBackground(color); }
    public void setForeground(int color) { delegate.setForeground(color); }
    public void setLocation(int x, int y) { delegate.setLocation(x, y); }
    public void setSize(int width, int height) { delegate.setSize(width, height); }
    public void setFont(Font font) { delegate.setFont(font.delegate); }
    public void setVisible(boolean visible) { delegate.setVisible(visible); }
}
