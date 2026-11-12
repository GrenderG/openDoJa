package com.docomostar.media;

public interface MediaImage extends MediaResource {
    int getWidth();
    int getHeight();
    com.docomostar.ui.Image getImage();
    com.nttdocomo.ui.ExifData getExifData();
    void setExifData(com.nttdocomo.ui.ExifData data);
}
