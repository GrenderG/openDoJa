package com.nttdocomo.ui;

import com.nttdocomo.util.EventListener;

/**
 * Defines the media Listener type.
 */
public interface MediaListener extends EventListener {
    void mediaAction(MediaPresenter presenter, int type, int param);
}
