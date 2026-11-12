package com.docomostar.opt.device;

/** Legacy optional namespace for the existing stationary STAR gyro service. */
public class GyroSensor {
    public static final int ANGULAR_VELOCITY_X = 1, ANGULAR_VELOCITY_Y = 2, ANGULAR_VELOCITY_Z = 3;
    private static GyroSensor instance;
    private final com.docomostar.device.location.GyroSensor delegate;
    private GyroSensor(com.docomostar.device.location.GyroSensor delegate) { this.delegate = delegate; }
    public static synchronized GyroSensor getGyroSensor() {
        var host = com.docomostar.device.location.GyroSensor.getGyroSensor();
        if (instance == null || instance.delegate != host) instance = new GyroSensor(host);
        return instance;
    }
    public int[] getAvailableData() { return delegate.getAvailableData(); }
    public int getMinDataValue(int type) { return delegate.getMinDataValue(type); }
    public int getMaxDataValue(int type) { return delegate.getMaxDataValue(type); }
    public void start(int interval, boolean overwrite) { delegate.start(interval, overwrite); }
    public void stop() { delegate.stop(); }
    public int getIntervalResolution() { return delegate.getIntervalResolution(); }
    public int getMaxDataSize() { return delegate.getMaxDataSize(); }
    public int getDataSize() { return delegate.getDataSize(); }
    public GyroData getData() { return new GyroData(delegate.getData()); }
    public void disposeData() { delegate.disposeData(); }
}
