package com.nttdocomo.device.location;

import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Represents a positioning function that mainly uses GPS.
 * In this implementation, a more accurate result than GPS alone may be
 * obtained by using other means as appropriate.
 */
public final class GPSLocationProvider extends LocationProvider {
    /** Represents the standalone positioning mode (=2). */
    public static final int MODE_STANDALONE = 2;

    private static final int[] AVAILABLE_MEASUREMENT_MODES = {
            MODE_STANDARD,
            MODE_QUALITY_PRIORITY,
            MODE_STANDALONE
    };

    private volatile boolean interrupted;
    private ScheduledFuture<?> trackingFuture;
    private TrackingListener trackingListener;
    private int trackingThreshold = -1;

    /**
     * Creates a GPS location-provider instance.
     */
    protected GPSLocationProvider() {
    }

    /**
     * Performs positioning mainly by using GPS.
     * The returned {@link Location} is always based on the WGS84 datum.
     *
     * @param timeout the timeout value in seconds; if 0 is specified, the
     *        maximum supported timeout value is used
     * @return the location result
     * @throws IllegalArgumentException if {@code timeout} is negative
     * @throws LocationException if positioning fails
     */
    @Override
    public Location getLocation(int timeout) throws LocationException {
        if (timeout < 0) {
            throw new IllegalArgumentException("timeout out of range: " + timeout);
        }
        _LocationSupport.beginPositioning(this);
        try {
            long remaining = _LocationSupport.positioningDelayMillis();
            while (remaining > 0L) {
                if (consumeInterrupted()) {
                    throw new LocationException(LocationException.INTERRUPTED,
                            "GPS location acquisition was interrupted");
                }
                long sleep = Math.min(remaining, 50L);
                Thread.sleep(sleep);
                remaining -= sleep;
            }
            if (consumeInterrupted()) {
                throw new LocationException(LocationException.INTERRUPTED,
                        "GPS location acquisition was interrupted");
            }
            LocationException failure = _LocationSupport.configuredLocationFailure();
            if (failure != null) {
                throw failure;
            }
            return _LocationSupport.currentLocation();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new LocationException(LocationException.INTERRUPTED,
                    "GPS location acquisition was interrupted");
        } finally {
            _LocationSupport.endPositioning(this);
        }
    }

    /**
     * Interrupts positioning.
     */
    @Override
    public void interrupt() {
        interrupted = true;
    }

    /**
     * Specifies the measurement mode.
     *
     * @param mode the measurement mode
     * @throws IllegalArgumentException if {@code mode} is invalid
     */
    @Override
    public void setMeasurementMode(int mode) {
        super.setMeasurementMode(mode);
    }

    /**
     * Gets the measurement modes that can be set.
     *
     * @return an array containing the supported measurement modes
     */
    @Override
    public int[] getAvailableMeasurementMode() {
        return AVAILABLE_MEASUREMENT_MODES.clone();
    }

    /**
     * Gets the minimum interval that can be specified when periodic positioning
     * is performed.
     *
     * @return the minimum interval in milliseconds, or {@code -1} if tracking
     *         mode is not supported
     */
    @Override
    public int getMinimalInterval() {
        return _LocationSupport.trackingSupported() ? _LocationSupport.minimalInterval() : -1;
    }

    /**
     * Sets tracking Listener.
     */
    @Override
    public void setTrackingListener(TrackingListener listener, int interval, int threshold) {
        if (listener == null) {
            cancelTracking();
            return;
        }
        if (interval <= 0) {
            throw new IllegalArgumentException("interval out of range: " + interval);
        }
        if (threshold < -1) {
            throw new IllegalArgumentException("threshold out of range: " + threshold);
        }
        if (!_LocationSupport.trackingSupported()) {
            return;
        }
        int effectiveInterval = Math.max(interval, getMinimalInterval());
        _LocationSupport.beginTracking(this);
        synchronized (this) {
            cancelTrackingLocked(false, false);
            this.trackingListener = listener;
            this.trackingThreshold = threshold;
            this.trackingFuture = _LocationSupport.EXECUTOR.scheduleAtFixedRate(
                    this::dispatchTrackingSample,
                    effectiveInterval,
                    effectiveInterval,
                    TimeUnit.MILLISECONDS
            );
        }
    }

    private void dispatchTrackingSample() {
        TrackingListener currentListener;
        int currentThreshold;
        synchronized (this) {
            currentListener = trackingListener;
            currentThreshold = trackingThreshold;
        }
        if (currentListener == null) {
            return;
        }
        long delay = _LocationSupport.positioningDelayMillis();
        if (currentThreshold >= 0 && delay > currentThreshold) {
            currentListener.locationReceived(
                    this,
                    null,
                    new LocationException(LocationException.TIMEOUT, "Position unavailable")
            );
            return;
        }
        LocationException failure = _LocationSupport.configuredLocationFailure();
        if (failure != null) {
            currentListener.locationReceived(this, null, failure);
            return;
        }
        currentListener.locationReceived(this, _LocationSupport.currentLocation(), null);
    }

    private synchronized boolean consumeInterrupted() {
        if (!interrupted) {
            return false;
        }
        interrupted = false;
        return true;
    }

    private void cancelTracking() {
        synchronized (this) {
            cancelTrackingLocked(true, true);
        }
    }

    private void cancelTrackingLocked(boolean clearListener, boolean releaseSlot) {
        if (trackingFuture != null) {
            trackingFuture.cancel(false);
            trackingFuture = null;
        }
        if (clearListener) {
            trackingListener = null;
            trackingThreshold = -1;
        }
        if (releaseSlot) {
            _LocationSupport.endTracking(this);
        }
    }
}
