package com.docomostar.opt.device;

import java.util.Date;

/** STAR history record backed by the existing DoJa pedometer record. */
public class PedometerData {
    private final com.nttdocomo.opt.device.PedometerData delegate;

    PedometerData(com.nttdocomo.opt.device.PedometerData delegate) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
    }

    public Date getDate() { return delegate.getDate(); }
    public int getNumberOfSteps() { return delegate.getNumberOfSteps(); }
    public int getDistance() { return delegate.getDistance(); }
    public int getNumberOfAerobicsSteps() { return delegate.getNumberOfAerobicsSteps(); }
    public int getAerobicsTime() { return delegate.getAerobicsTime(); }
    public int getConsumptionCalorie() { return delegate.getConsumptionCalorie(); }
    public int getAmountOfCombustionFat() { return delegate.getAmountOfCombustionFat(); }
}
