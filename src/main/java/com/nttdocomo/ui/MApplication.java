package com.nttdocomo.ui;

public abstract class MApplication extends IApplication {
    public static final int MODE_CHANGED_EVENT = 1;
    public static final int WAKEUP_TIMER_EVENT = 2;
    public static final int CLOCK_TICK_EVENT = 3;
    public static final int FOLD_CHANGED_EVENT = 4;

    private int wakeupTimer;
    private boolean clockTick;
    private boolean active = true;

    public MApplication() {
    }

    public void processSystemEvent(int type, int param) {
    }

    public final void sleep() {
        active = false;
    }

    public final void setWakeupTimer(int minutes) {
        this.wakeupTimer = minutes;
    }

    public final int getWakeupTimer() {
        return wakeupTimer;
    }

    public final void resetWakeupTimer() {
        wakeupTimer = 0;
    }

    public final void setClockTick(boolean enabled) {
        this.clockTick = enabled;
    }

    public final void deactivate() {
        active = false;
    }

    public final boolean isActive() {
        return active;
    }
}
