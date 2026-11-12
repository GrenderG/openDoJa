package com.docomostar.device.location;

public interface AccelerationEventListener extends com.docomostar.util.EventListener {
    void actionPerformed(AccelerationSensor source, int event, int value);
}
