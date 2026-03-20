package com.nttdocomo.ui;

public abstract class Component {
    private int x;
    private int y;
    private int width;
    private int height;
    private int background = Graphics.getColorOfName(Graphics.WHITE);
    private int foreground = Graphics.getColorOfName(Graphics.BLACK);
    private Font font = Font.getDefaultFont();
    private boolean visible = true;

    public Component() {
    }

    public final int getHeight() {
        return height;
    }

    public final int getWidth() {
        return width;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public void setBackground(int color) {
        this.background = color;
    }

    public void setForeground(int color) {
        this.foreground = color;
    }

    public void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public void setFont(Font font) {
        if (font != null) {
            this.font = font;
        }
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    int backgroundColor() {
        return background;
    }

    int foregroundColor() {
        return foreground;
    }

    Font font() {
        return font;
    }

    boolean visible() {
        return visible;
    }
}
