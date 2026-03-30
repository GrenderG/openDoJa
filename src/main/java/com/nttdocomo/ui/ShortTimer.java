package com.nttdocomo.ui;

import com.nttdocomo.util.TimeKeeper;
import opendoja.host.DoJaRuntime;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Defines the short-time timer class.
 */
public final class ShortTimer implements TimeKeeper {
    private static final int MIN_TIME_INTERVAL =
            java.lang.Math.max(1, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.SHORT_TIMER_MIN_TIME_INTERVAL));
    private static final int RESOLUTION =
            java.lang.Math.max(1, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.SHORT_TIMER_RESOLUTION));
    private static final Map<Canvas, Map<Integer, ShortTimer>> REGISTRY = new WeakHashMap<>();
    // Bundled DoJa titles schedule gameplay-step timers with values like 100, but those same
    // titles only progress correctly when the desktop runtime treats the interval as handset timer
    // units rather than literal Java milliseconds. A divisor of 10 matches the observed sample
    // behavior and keeps this timer usable as a general game-step primitive.
    private static final int DEFAULT_INTERVAL_DIVISOR = 10;
    private static final int INTERVAL_DIVISOR = java.lang.Math.max(
            1, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.SHORT_TIMER_INTERVAL_DIVISOR, DEFAULT_INTERVAL_DIVISOR));
    private final Canvas canvas;
    private final int timerId;
    private final int interval;
    private final boolean repeat;
    private final boolean registered;
    private ScheduledFuture<?> future;
    private boolean disposed;

    /**
     * Gets a short-time timer object.
     *
     * @param canvas the canvas object that receives timer-event notification
     * @param timerId the timer ID
     * @param interval the timer-event time interval in milliseconds
     * @param repeat {@code true} to generate repeated timer events, or
     *               {@code false} to generate only one
     * @return the short-time timer object
     * @throws NullPointerException if {@code canvas} is {@code null}
     * @throws IllegalArgumentException if {@code interval} is negative
     * @throws UIException if a timer with the same ID already exists for the
     *         same canvas
     */
    public static ShortTimer getShortTimer(Canvas canvas, int timerId, int interval, boolean repeat) {
        if (canvas == null) {
            throw new NullPointerException("canvas");
        }
        if (interval < 0) {
            throw new IllegalArgumentException("time");
        }
        synchronized (REGISTRY) {
            Map<Integer, ShortTimer> timers = REGISTRY.computeIfAbsent(canvas, key -> new HashMap<>());
            if (timers.containsKey(timerId)) {
                throw new UIException(UIException.BUSY_RESOURCE);
            }
            ShortTimer timer = new ShortTimer(canvas, timerId, normalizeInterval(interval), repeat, true);
            timers.put(timerId, timer);
            return timer;
        }
    }

    /**
     * Creates a short-time timer object.
     */
    protected ShortTimer() {
        this(null, 0, MIN_TIME_INTERVAL, false, false);
    }

    ShortTimer(Canvas canvas, int timerId, int interval, boolean repeat, boolean registered) {
        this.canvas = canvas;
        this.timerId = timerId;
        this.interval = java.lang.Math.max(MIN_TIME_INTERVAL, interval);
        this.repeat = repeat;
        this.registered = registered;
    }

    /**
     * Gets the minimum value among the supported timer-event occurrence time
     * intervals.
     *
     * @return the minimum supported timer-event occurrence time interval, in
     *         milliseconds
     * @throws UIException if this timer has already been disposed
     */
    @Override
    public int getMinTimeInterval() {
        ensureNotDisposed();
        return MIN_TIME_INTERVAL;
    }

    /**
     * Gets the timer resolution.
     *
     * @return the timer resolution in milliseconds
     * @throws UIException if this timer has already been disposed
     */
    @Override
    public int getResolution() {
        ensureNotDisposed();
        return RESOLUTION;
    }

    /**
     * Starts the timer.
     *
     * @throws UIException if the timer is already running or has been disposed
     */
    @Override
    public void start() {
        ensureNotDisposed();
        if (future != null) {
            throw new UIException(UIException.ILLEGAL_STATE);
        }
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime == null || canvas == null) {
            return;
        }
        Runnable task = () -> {
            runtime.dispatchTimerEvent(canvas, timerId);
            if (!repeat) {
                synchronized (ShortTimer.this) {
                    future = null;
                }
            }
        };
        int scheduledInterval = java.lang.Math.max(1, interval / INTERVAL_DIVISOR);
        if (repeat) {
            future = runtime.scheduler().scheduleAtFixedRate(
                    task, scheduledInterval, scheduledInterval, TimeUnit.MILLISECONDS);
        } else {
            future = runtime.scheduler().schedule(task, scheduledInterval, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Stops the timer.
     * If the timer is already stopped, this method does nothing.
     *
     * @throws UIException if this timer has already been disposed
     */
    @Override
    public synchronized void stop() {
        ensureNotDisposed();
        if (future != null) {
            future.cancel(false);
            future = null;
        }
    }

    /**
     * Disposes of the timer.
     * If methods other than {@code dispose} are called after this method is
     * called, a {@link UIException} occurs with
     * {@link UIException#ILLEGAL_STATE}. Calling {@code dispose} again does
     * nothing.
     */
    @Override
    public void dispose() {
        if (disposed) {
            return;
        }
        stop();
        disposed = true;
        unregister();
    }

    private static int normalizeInterval(int interval) {
        if (interval <= MIN_TIME_INTERVAL) {
            return MIN_TIME_INTERVAL;
        }
        long steps = (long) (interval - MIN_TIME_INTERVAL + RESOLUTION - 1) / RESOLUTION;
        long normalized = MIN_TIME_INTERVAL + steps * (long) RESOLUTION;
        return normalized > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) normalized;
    }

    private void ensureNotDisposed() {
        if (disposed) {
            throw new UIException(UIException.ILLEGAL_STATE);
        }
    }

    private void unregister() {
        if (!registered || canvas == null) {
            return;
        }
        synchronized (REGISTRY) {
            Map<Integer, ShortTimer> timers = REGISTRY.get(canvas);
            if (timers == null) {
                return;
            }
            timers.remove(timerId, this);
            if (timers.isEmpty()) {
                REGISTRY.remove(canvas);
            }
        }
    }
}
