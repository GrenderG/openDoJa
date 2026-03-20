package com.nttdocomo.ui;

import com.nttdocomo.util.EventListener;

public interface SoftKeyListener extends EventListener {
    void softKeyPressed(int key);

    void softKeyReleased(int key);
}
