package com.nttdocomo.util;

import opendoja.host.DoJaRuntime;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public final class Timer implements TimeKeeper {
    private boolean repeat;
    private int time;
    private TimerListener listener;
    private ScheduledFuture<?> future;

    public Timer() {
    }

    @Override
    public int getMinTimeInterval() {
        return 1;
    }

    @Override
    public int getResolution() {
        return 1;
    }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }

    public void setTime(int time) {
        this.time = Math.max(0, time);
    }

    public void setListener(TimerListener listener) {
        this.listener = listener;
    }

    @Override
    public void start() {
        stop();
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null || listener == null) {
            return;
        }
        Runnable task = () -> listener.timerExpired(this);
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
        listener = null;
    }
}
