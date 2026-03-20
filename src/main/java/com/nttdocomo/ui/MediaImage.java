package com.nttdocomo.ui;

public interface MediaImage extends MediaResource {
    String MP4_VIDEOTRACK = "mp4.videotrack";
    String MP4_AUDIOTRACK = "mp4.audiotrack";
    String MP4_TEXTTRACK = "mp4.texttrack";

    int getWidth();

    int getHeight();

    Image getImage();

    ExifData getExifData();

    void setExifData(ExifData exifData);
}
