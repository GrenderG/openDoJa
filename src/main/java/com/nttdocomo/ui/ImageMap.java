package com.nttdocomo.ui;

public class ImageMap {
    private int windowX;
    private int windowY;
    private int windowWidth;
    private int windowHeight;
    private int cellWidth = 16;
    private int cellHeight = 16;
    private int[] mapData = new int[0];
    private Image[] images = new Image[0];

    public ImageMap(int width, int height) {
        this.windowWidth = width;
        this.windowHeight = height;
    }

    public ImageMap(int x, int y, int width, int height, byte[] map, Image[] images) {
        this(x, y, width, height, toIntArray(map), images, false);
    }

    public ImageMap(int x, int y, int width, int height, int[] map, Image[] images) {
        this(x, y, width, height, map, images, false);
    }

    public ImageMap(int x, int y, int width, int height, int[] map, Image[] images, boolean packed) {
        this.windowX = x;
        this.windowY = y;
        this.windowWidth = width;
        this.windowHeight = height;
        this.mapData = map == null ? new int[0] : map.clone();
        this.images = images == null ? new Image[0] : images.clone();
    }

    public void setImageMap(int cellWidth, int cellHeight, byte[] map, Image[] images) {
        setImageMap(cellWidth, cellHeight, toIntArray(map), images, false);
    }

    public void setImageMap(int cellWidth, int cellHeight, int[] map, Image[] images) {
        setImageMap(cellWidth, cellHeight, map, images, false);
    }

    public void setImageMap(int cellWidth, int cellHeight, int[] map, Image[] images, boolean packed) {
        this.cellWidth = cellWidth;
        this.cellHeight = cellHeight;
        this.mapData = map == null ? new int[0] : map.clone();
        this.images = images == null ? new Image[0] : images.clone();
    }

    public void setWindow(int x, int y, int width, int height) {
        this.windowX = x;
        this.windowY = y;
        this.windowWidth = width;
        this.windowHeight = height;
    }

    public void setWindowLocation(int x, int y) {
        this.windowX = x;
        this.windowY = y;
    }

    public void moveWindowLocation(int dx, int dy) {
        this.windowX += dx;
        this.windowY += dy;
    }

    void draw(Graphics graphics, int drawX, int drawY) {
        if (images.length == 0 || mapData.length == 0 || cellWidth <= 0 || cellHeight <= 0) {
            return;
        }
        int columns = Math.max(1, windowWidth / cellWidth);
        for (int i = 0; i < mapData.length; i++) {
            int imageIndex = mapData[i];
            if (imageIndex < 0 || imageIndex >= images.length || images[imageIndex] == null) {
                continue;
            }
            int column = i % columns;
            int row = i / columns;
            graphics.drawImage(images[imageIndex], drawX + windowX + (column * cellWidth), drawY + windowY + (row * cellHeight));
        }
    }

    private static int[] toIntArray(byte[] data) {
        if (data == null) {
            return new int[0];
        }
        int[] ints = new int[data.length];
        for (int i = 0; i < data.length; i++) {
            ints[i] = data[i] & 0xFF;
        }
        return ints;
    }
}
