package com.docomostar.ui;

public interface MediaListener extends com.docomostar.util.EventListener {
    void mediaAction(MediaPresenter presenter, int type, int param);
}
