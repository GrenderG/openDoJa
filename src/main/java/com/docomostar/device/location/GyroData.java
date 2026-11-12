package com.docomostar.device.location;

/** Stationary gyro samples used by the compatibility stub. */
public class GyroData {
    private final int size;
    GyroData(int size) { this.size = size; }
    public int[] getAngularVelocityX() { return new int[size]; }
    public int[] getAngularVelocityY() { return new int[size]; }
    public int[] getAngularVelocityZ() { return new int[size]; }
    public int size() { return size; }
}
