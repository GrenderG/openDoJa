package com.docomostar.ui;

import com.docomostar.media.MediaResource;

public interface MediaPresenter {
    MediaResource getMediaResource();
    void play();
    void stop();
    void setAttribute(int key, int value);
    void setMediaListener(MediaListener listener);
}
