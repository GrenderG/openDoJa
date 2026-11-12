package com.docomostar.device.gesturereader;

/** Uses the existing emulated gesture lifecycle and neutral readings. */
public final class RollReader extends GestureReader {
    private static final java.util.Map<Integer, RollReader> READERS = new java.util.HashMap<>();
    private final com.nttdocomo.device.gesturereader.RollReader host;
    private RollReader(com.nttdocomo.device.gesturereader.RollReader host) { this.host = host; }
    public static synchronized RollReader getRollReader(int id) {
        var host = com.docomostar.device._DeviceExceptions.call(
                () -> com.nttdocomo.device.gesturereader.RollReader.getRollReader(id));
        return READERS.computeIfAbsent(id, ignored -> new RollReader(host));
    }
    @Override public void start() { com.docomostar.device._DeviceExceptions.run(host::start); }
    @Override public void stop() { com.docomostar.device._DeviceExceptions.run(host::stop); }
    public void recenter() { com.docomostar.device._DeviceExceptions.run(host::recenter); }
    public RollData getRollData() {
        return new RollData(com.docomostar.device._DeviceExceptions.call(host::getRollData));
    }
}
