package com.docomostar.device.location;

/** Reuses the host's stationary/configured samples; physical motion input is not implemented. */
public class AccelerationSensor {
    public static final int ACCELERATION_X = 1, ACCELERATION_Y = 2, ACCELERATION_Z = 3;
    public static final int ROLL = 4, PITCH = 5, SCREEN_ORIENTATION = 6;
    public static final int DOUBLE_TAP_LEFT = 0, DOUBLE_TAP_RIGHT = 1, DOUBLE_TAP_FRONT = 2, DOUBLE_TAP_BACK = 3;
    public static final int EVENT_SCREEN_ORIENTATION = 1, EVENT_DOUBLE_TAP = 2, EVENT_ACCESSIBILITY = 4;
    public static final int ACCESSIBLE_TRUE = 0, ACCESSIBLE_FALSE = 1;
    private static AccelerationSensor instance;
    private final com.nttdocomo.device.location.AccelerationSensor host;
    private AccelerationSensor(com.nttdocomo.device.location.AccelerationSensor host) { this.host = host; }
    public static synchronized AccelerationSensor getAccelerationSensor() {
        try {
            var host = com.nttdocomo.device.location.AccelerationSensor.getAccelerationSensor();
            if (instance == null || instance.host != host) instance = new AccelerationSensor(host);
            return instance;
        } catch (com.nttdocomo.lang.UnsupportedOperationException failure) {
            var translated = new com.docomostar.lang.UnsupportedOperationException(failure.getMessage());
            translated.initCause(failure);
            throw translated;
        }
    }
    public static AccelerationSensor getAccelerationSensor(int id) {
        if (id != 0) throw new IllegalArgumentException("sensor id");
        return getAccelerationSensor();
    }
    public int[] getAvailableData() { return host.getAvailableData(); }
    public int getMinDataValue(int type) { return host.getMinDataValue(type); }
    public int getMaxDataValue(int type) { return host.getMaxDataValue(type); }
    public AccelerationData getCurrentData() { return adapt(host.getCurrentData()); }
    public void start(int interval) { host.start(interval); }
    public void stop() { host.stop(); }
    public int getIntervalResolution() { return host.getIntervalResolution(); }
    public int getMaxDataSize() { return host.getMaxDataSize(); }
    public int getDataSize() { return host.getDataSize(); }
    public AccelerationData getData() { return adapt(host.getData()); }
    public AccelerationData peekLatestData() { return adapt(host.peekLatestData()); }
    public void disposeData() { host.disposeData(); }
    public int[] getAvailableEvent() { return host.getAvailableEvent(); }
    public void startEventDetection(int event) { host.startEventDetection(event); }
    public void stopEventDetection() { host.stopEventDetection(); }
    public void setEventListener(AccelerationEventListener listener) {
        host.setEventListener(listener == null ? null : (source, event, value) -> listener.actionPerformed(this, event, value));
    }
    public boolean isAccessible() { return true; }
    private static AccelerationData adapt(com.nttdocomo.device.location.AccelerationData data) {
        return data == null ? null : new AccelerationData(data);
    }
}
