package com.nttdocomo.ui;

import com.nttdocomo.util.EventListener;

/**
 * Defines the soft Key Listener type.
 */
public interface SoftKeyListener extends EventListener {
    void softKeyPressed(int key);

    void softKeyReleased(int key);
}
