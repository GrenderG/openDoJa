package com.nttdocomo.ui;

import com.nttdocomo.util.TimeKeeper;
import opendoja.host.DoJaRuntime;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class ShortTimer implements TimeKeeper {
    private final Canvas canvas;
    private final int time;
    private final int param;
    private final boolean repeat;
    private ScheduledFuture<?> future;

    public static ShortTimer getShortTimer(Canvas canvas, int time, int param, boolean repeat) {
        return new ShortTimer(canvas, time, param, repeat);
    }

    protected ShortTimer() {
        this(null, 0, 0, false);
    }

    ShortTimer(Canvas canvas, int time, int param, boolean repeat) {
        this.canvas = canvas;
        this.time = Math.max(0, time);
        this.param = param;
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
        Runnable task = () -> runtime.dispatchTimerEvent(canvas, param);
        if (repeat) {
            int interval = java.lang.Math.max(1, time);
            future = runtime.scheduler().scheduleAtFixedRate(task, interval, interval, TimeUnit.MILLISECONDS);
        } else {
            future = runtime.scheduler().schedule(task, time, TimeUnit.MILLISECONDS);
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
