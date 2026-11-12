package com.docomostar.ui;

import java.util.Objects;

/** Uses DoJa tile/window drawing with live STAR image and map-data arrays. */
public class ImageMap {
    private final com.nttdocomo.ui.ImageMap delegate;
    private Image[] images;
    private com.nttdocomo.ui.Image[] hostImages;

    protected ImageMap() { this(16, 16); }
    public ImageMap(int cellWidth, int cellHeight) {
        delegate = new com.nttdocomo.ui.ImageMap(cellWidth, cellHeight);
    }
    public ImageMap(int cellWidth, int cellHeight, int mapWidth, int mapHeight, int[] data, Image[] images) {
        this(cellWidth, cellHeight, mapWidth, mapHeight, data, images, false);
    }
    public ImageMap(int cellWidth, int cellHeight, int mapWidth, int mapHeight, int[] data, Image[] images, boolean concatenated) {
        this.images = Objects.requireNonNull(images, "images");
        hostImages = unwrap(images);
        try { delegate = new com.nttdocomo.ui.ImageMap(cellWidth, cellHeight, mapWidth, mapHeight, data, hostImages, concatenated); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setImageMap(int width, int height, int[] data, Image[] images) {
        setImageMap(width, height, data, images, false);
    }
    public void setImageMap(int width, int height, int[] data, Image[] images, boolean concatenated) {
        com.nttdocomo.ui.Image[] converted = unwrap(images);
        try { delegate.setImageMap(width, height, data, converted, concatenated); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
        this.images = images;
        hostImages = converted;
    }
    public void setWindow(int x, int y, int width, int height) { delegate.setWindow(x, y, width, height); }
    public void setWindowLocation(int x, int y) { delegate.setWindowLocation(x, y); }
    public void moveWindowLocation(int dx, int dy) { delegate.moveWindowLocation(dx, dy); }

    com.nttdocomo.ui.ImageMap host() {
        if (images != null) {
            for (int i = 0; i < images.length; i++) {
                hostImages[i] = images[i] == null ? null : Image.unwrap(images[i]);
            }
        }
        return delegate;
    }
    private static com.nttdocomo.ui.Image[] unwrap(Image[] images) {
        Objects.requireNonNull(images, "images");
        com.nttdocomo.ui.Image[] result = new com.nttdocomo.ui.Image[images.length];
        for (int i = 0; i < images.length; i++) result[i] = images[i] == null ? null : Image.unwrap(images[i]);
        return result;
    }
}
