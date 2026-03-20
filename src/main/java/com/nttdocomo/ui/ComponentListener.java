package com.nttdocomo.ui;

import com.nttdocomo.util.EventListener;

public interface ComponentListener extends EventListener {
    int BUTTON_PRESSED = 1;
    int SELECTION_CHANGED = 2;
    int TEXT_CHANGED = 3;

    void componentAction(Component component, int type, int param);
}
