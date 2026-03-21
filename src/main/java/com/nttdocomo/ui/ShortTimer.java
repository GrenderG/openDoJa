package com.nttdocomo.ui;

import com.nttdocomo.util.TimeKeeper;
import opendoja.host.DoJaRuntime;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class ShortTimer implements TimeKeeper {
    // Bundled DoJa titles schedule gameplay-step timers with values like 100, but those same
    // titles only progress correctly when the desktop runtime treats the interval as handset timer
    // units rather than literal Java milliseconds. A divisor of 10 matches the observed sample
    // behavior and keeps this timer usable as a general game-step primitive.
    private static final int DEFAULT_INTERVAL_DIVISOR = 10;
    private static final int INTERVAL_DIVISOR = java.lang.Math.max(
            1, Integer.getInteger("opendoja.shortTimerIntervalDivisor", DEFAULT_INTERVAL_DIVISOR));
    private final Canvas canvas;
    private final int timerId;
    private final int interval;
    private final boolean repeat;
    private ScheduledFuture<?> future;

    public static ShortTimer getShortTimer(Canvas canvas, int timerId, int interval, boolean repeat) {
        return new ShortTimer(canvas, timerId, interval, repeat);
    }

    protected ShortTimer() {
        this(null, 0, 0, false);
    }

    ShortTimer(Canvas canvas, int timerId, int interval, boolean repeat) {
        this.canvas = canvas;
        this.timerId = timerId;
        this.interval = Math.max(0, interval);
        this.repeat = repeat;
    }

    @Override
    public int getMinTimeInterval() {
        return 1;
    }

    @Override
    public int getResolution() {
        return 1;
    }

    @Override
    public void start() {
        stop();
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null || canvas == null) {
            return;
        }
        Runnable task = () -> runtime.dispatchTimerEvent(canvas, timerId);
        if (repeat) {
            int repeatInterval = java.lang.Math.max(getMinTimeInterval(), interval / INTERVAL_DIVISOR);
            future = runtime.scheduler().scheduleAtFixedRate(task, repeatInterval, repeatInterval, TimeUnit.MILLISECONDS);
        } else {
            int delay = java.lang.Math.max(0, interval / INTERVAL_DIVISOR);
            future = runtime.scheduler().schedule(task, delay, TimeUnit.MILLISECONDS);
        }
    }

    @Override
    public void stop() {
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    @Override
    public void dispose() {
        stop();
    }
}
