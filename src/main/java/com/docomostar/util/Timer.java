package com.docomostar.util;

import com.docomostar.ui.UIException;

/** STAR callbacks use the existing DoJa timer, scheduler and lifecycle. */
public final class Timer implements TimeKeeper {
    private final com.nttdocomo.util.Timer delegate = new com.nttdocomo.util.Timer();

    public Timer() { }

    @Override public int getMinTimeInterval() {
        try { return delegate.getMinTimeInterval(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    @Override public int getResolution() {
        try { return delegate.getResolution(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setRepeat(boolean repeat) {
        try { delegate.setRepeat(repeat); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setTime(int time) {
        try { delegate.setTime(time); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setListener(TimerListener listener) {
        try { delegate.setListener(listener == null ? null : timer -> listener.timerExpired(this)); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    @Override public void start() {
        try { delegate.start(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    @Override public void stop() {
        try { delegate.stop(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    @Override public void dispose() {
        try { delegate.dispose(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
}
