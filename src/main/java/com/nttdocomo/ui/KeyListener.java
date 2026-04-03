package com.nttdocomo.ui;

import com.nttdocomo.util.EventListener;

/**
 * Defines the key Listener type.
 */
public interface KeyListener extends EventListener {
    void keyPressed(Panel panel, int key);

    void keyReleased(Panel panel, int key);
}
