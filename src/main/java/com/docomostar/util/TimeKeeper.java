package com.docomostar.util;

/** STAR timer lifecycle contract. */
public interface TimeKeeper {
    void dispose();
    int getMinTimeInterval();
    int getResolution();
    void start();
    void stop();
}
