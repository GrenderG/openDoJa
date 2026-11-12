package com.docomostar.opt.device;

/** STAR pedometer uses the same measurements and settings as the DoJa backend. */
public class Pedometer {
    public static final int DEV_PEDOMETER = com.nttdocomo.opt.device.Pedometer.DEV_PEDOMETER;
    public static final int ATTR_PEDOMETER_OFF = com.nttdocomo.opt.device.Pedometer.ATTR_PEDOMETER_OFF;
    public static final int ATTR_PEDOMETER_ON = com.nttdocomo.opt.device.Pedometer.ATTR_PEDOMETER_ON;
    private static final Pedometer INSTANCE = new Pedometer();
    private final com.nttdocomo.opt.device.Pedometer delegate;

    protected Pedometer() {
        delegate = com.nttdocomo.opt.device.Pedometer.getPedometer();
    }

    public static Pedometer getPedometer() { return INSTANCE; }
    public int getAttribute(int attribute) { return delegate.getAttribute(attribute); }
    public int getCount() { return delegate.getCount(); }
    public int getTotalSteps() { return delegate.getTotalSteps(); }
    public int getTotalDistance() { return delegate.getTotalDistance(); }
    public PedometerData getTodayData() { return new PedometerData(delegate.getTodayData()); }
    public PedometerData[] getData() { return wrap(delegate.getData()); }
    public PedometerData[] getData(int index, int articles) { return wrap(delegate.getData(index, articles)); }

    private static PedometerData[] wrap(com.nttdocomo.opt.device.PedometerData[] data) {
        if (data == null) return null;
        PedometerData[] result = new PedometerData[data.length];
        for (int i = 0; i < data.length; i++) {
            result[i] = data[i] == null ? null : new PedometerData(data[i]);
        }
        return result;
    }
}
