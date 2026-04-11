package com.nttdocomo.ui;

import opendoja.host.DesktopVideoSupport;

final class DesktopVideoMediaImage extends MediaManager.AbstractMediaResource implements MediaImage {
    private final byte[] data;
    private ExifData exifData = new ExifData();
    private volatile DesktopVideoSupport.VideoMetadata metadata;
    private volatile DesktopImage posterImage;

    DesktopVideoMediaImage(byte[] data) {
        this.data = data == null ? new byte[0] : data.clone();
    }

    @Override
    public int getWidth() {
        return 1;
    }

    @Override
    public int getHeight() {
        return 1;
    }

    @Override
    public Image getImage() {
        DesktopImage cached = posterImage;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (posterImage == null) {
                posterImage = new DesktopImage(1, 1);
            }
            return posterImage;
        }
    }

    @Override
    public ExifData getExifData() {
        return exifData;
    }

    @Override
    public void setExifData(ExifData exifData) {
        this.exifData = exifData == null ? new ExifData() : exifData;
    }

    byte[] byteView() {
        return data;
    }

    DesktopVideoSupport.VideoMetadata metadata() {
        DesktopVideoSupport.VideoMetadata cached = metadata;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (metadata == null) {
                metadata = DesktopVideoSupport.probe(data);
            }
            return metadata;
        }
    }
}
