package com.docomostar.io;

import com.docomostar.device.BTStateListener;

public interface BTConnection {
    void close();
    void setBTStateListener(BTStateListener listener);
}
