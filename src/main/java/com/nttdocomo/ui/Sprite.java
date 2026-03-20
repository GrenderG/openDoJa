package com.nttdocomo.ui;

public class Sprite {
    private Image image;
    private int sourceX;
    private int sourceY;
    private int width;
    private int height;
    private int x;
    private int y;
    private boolean visible = true;
    private int flipMode;
    private int[] rotation;

    public Sprite(Image image) {
        this(image, 0, 0, image == null ? 0 : image.getWidth(), image == null ? 0 : image.getHeight());
    }

    public Sprite(Image image, int x, int y, int width, int height) {
        setImage(image, x, y, width, height);
    }

    public void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
    }

    public void setImage(Image image) {
        setImage(image, 0, 0, image == null ? 0 : image.getWidth(), image == null ? 0 : image.getHeight());
    }

    public void setImage(Image image, int x, int y, int width, int height) {
        this.image = image;
        this.sourceX = x;
        this.sourceY = y;
        this.width = width;
        this.height = height;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public boolean isVisible() {
        return visible;
    }

    public void setFlipMode(int flipMode) {
        this.flipMode = flipMode;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void setRotation(int[] rotation) {
        this.rotation = rotation;
    }

    Image image() {
        return image;
    }

    int sourceX() {
        return sourceX;
    }

    int sourceY() {
        return sourceY;
    }
}
