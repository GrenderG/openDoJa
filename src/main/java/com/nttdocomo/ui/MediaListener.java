package com.nttdocomo.ui;

import com.nttdocomo.util.EventListener;

public interface MediaListener extends EventListener {
    void mediaAction(MediaPresenter presenter, int type, int param);
}
