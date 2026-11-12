package com.docomostar.device.location;

/** STAR view of the existing host's emulated acceleration data. */
public class AccelerationData {
    private final com.nttdocomo.device.location.AccelerationData host;
    AccelerationData(com.nttdocomo.device.location.AccelerationData host) { this.host = host; }
    public int[] getAccelerationX() { return host.getAccelerationX(); }
    public int[] getAccelerationY() { return host.getAccelerationY(); }
    public int[] getAccelerationZ() { return host.getAccelerationZ(); }
    public int[] getRoll() { return host.getRoll(); }
    public int[] getPitch() { return host.getPitch(); }
    public int[] getScreenOrientation() { return host.getScreenOrientation(); }
    public int size() { return host.size(); }
}
