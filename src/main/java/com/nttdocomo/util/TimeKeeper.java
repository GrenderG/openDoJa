package com.nttdocomo.util;

public interface TimeKeeper {
    void dispose();

    int getMinTimeInterval();

    int getResolution();

    void start();

    void stop();
}
