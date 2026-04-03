package com.nttdocomo.ui;

/**
 * Defines the media Presenter type.
 */
public interface MediaPresenter {
    void setData(MediaData data);

    MediaResource getMediaResource();

    void play();

    void stop();

    void setAttribute(int key, int value);

    void setMediaListener(MediaListener listener);
}
