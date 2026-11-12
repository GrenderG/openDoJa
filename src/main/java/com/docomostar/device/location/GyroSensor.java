package com.docomostar.device.location;

/** Compatibility stub only: lifecycle calls succeed and all angular velocities stay zero. */
public class GyroSensor {
    public static final int ANGULAR_VELOCITY_X = 1, ANGULAR_VELOCITY_Y = 2, ANGULAR_VELOCITY_Z = 3;
    private static GyroSensor instance;
    private static opendoja.host.DoJaRuntime owner;
    private boolean enabled = true, running;
    protected GyroSensor() { }
    public static synchronized GyroSensor getGyroSensor() {
        var runtime = opendoja.host.DoJaRuntime.current();
        if (instance == null || owner != runtime) { instance = new GyroSensor(); owner = runtime; }
        return instance;
    }
    public int[] getAvailableData() { return new int[]{1, 2, 3}; }
    public int getMinDataValue(int type) { checkType(type); return Short.MIN_VALUE; }
    public int getMaxDataValue(int type) { checkType(type); return Short.MAX_VALUE; }
    public synchronized GyroData getCurrentData() { return new GyroData(enabled ? 1 : 0); }
    public synchronized void start(int interval, boolean mode) {
        if (interval <= 0) throw new IllegalArgumentException("interval");
        running = true;
    }
    public synchronized void stop() { running = false; }
    public int getIntervalResolution() { return 1; }
    public int getMaxDataSize() { return 1; }
    public synchronized int getDataSize() { return enabled && running ? 1 : 0; }
    public synchronized GyroData getData() { return new GyroData(getDataSize()); }
    public synchronized GyroData peekLatestData() { return getData(); }
    public void disposeData() { }
    public synchronized void setEnabled(boolean enabled) { this.enabled = enabled; if (!enabled) running = false; }
    private static void checkType(int type) { if (type < 1 || type > 3) throw new IllegalArgumentException("type"); }
}
