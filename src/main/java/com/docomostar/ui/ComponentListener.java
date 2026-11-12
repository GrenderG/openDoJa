package com.docomostar.ui;

public interface ComponentListener extends com.docomostar.util.EventListener {
    int BUTTON_PRESSED = 1, SELECTION_CHANGED = 2, TEXT_CHANGED = 3;
    void componentAction(Component component, int type, int param);
}
