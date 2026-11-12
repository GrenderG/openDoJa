package com.docomostar.device;

public interface BTStateListener extends com.docomostar.util.EventListener {
    int DISCONNECT = com.nttdocomo.device.BTStateListener.DISCONNECT;
    void stateChanged(int state);
}
