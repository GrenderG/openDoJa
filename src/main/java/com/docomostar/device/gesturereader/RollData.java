package com.docomostar.device.gesturereader;

/** View of the host's neutral gesture samples. No physical camera tracking is performed. */
public class RollData {
    public static final int QUALITY_RELIABLE = com.nttdocomo.device.gesturereader.RollData.QUALITY_RELIABLE;
    public static final int QUALITY_LOW_DETAIL = com.nttdocomo.device.gesturereader.RollData.QUALITY_LOW_DETAIL;
    public static final int QUALITY_TOO_FAST = com.nttdocomo.device.gesturereader.RollData.QUALITY_TOO_FAST;
    public static final int QUALITY_SAME_VALUE = com.nttdocomo.device.gesturereader.RollData.QUALITY_SAME_VALUE;
    private final com.nttdocomo.device.gesturereader.RollData host;
    RollData(com.nttdocomo.device.gesturereader.RollData host) { this.host = host; }
    public int getQuality() { return host.getQuality(); }
    public float[] getImmediateMotion() { return host.getImmediateMotion(); }
    public float[] getAccumulatedMotion() { return host.getAccumulatedMotion(); }
}
