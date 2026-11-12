package com.docomostar.opt.device;

/** Snapshot backed by the existing neutral gyro readings. */
public class GyroData {
    private final com.docomostar.device.location.GyroData delegate;
    GyroData(com.docomostar.device.location.GyroData delegate) { this.delegate = delegate; }
    public int[] getAngularVelocityX() { return delegate.getAngularVelocityX(); }
    public int[] getAngularVelocityY() { return delegate.getAngularVelocityY(); }
    public int[] getAngularVelocityZ() { return delegate.getAngularVelocityZ(); }
    public int size() { return delegate.size(); }
}
