package com.nttdocomo.device.location;

import com.nttdocomo.device.DeviceException;
import com.nttdocomo.lang.UnsupportedOperationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

final class _LocationSupport {
    static final ScheduledExecutorService EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "opendoja-location");
        thread.setDaemon(true);
        return thread;
    });

    private static final Object LOCK = new Object();

    private static GPSLocationProvider activePositioning;
    private static GPSLocationProvider activeTracking;

    private _LocationSupport() {
    }

    static boolean gpsSupported() {
        return opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.GPS_SUPPORTED);
    }

    static boolean trackingSupported() {
        if (!gpsSupported()) {
            return false;
        }
        if (!opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.GPS_TRACKING_SUPPORTED)) {
            return false;
        }
        return minimalInterval() >= 0;
    }

    static int minimalInterval() {
        return opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.GPS_MINIMAL_INTERVAL);
    }

    static boolean compassSupported() {
        return opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.COMPASS_SUPPORTED);
    }

    static boolean accelerationSupported() {
        return opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.ACCELERATION_SUPPORTED);
    }

    static int[] availableLocationMethods() {
        return gpsSupported() ? new int[]{LocationProvider.METHOD_GPS} : null;
    }

    static int[] availableLocationMethods(int capability) {
        if (capability == LocationProvider.CAPABILITY_TRACKING_MODE && trackingSupported()) {
            return new int[]{LocationProvider.METHOD_GPS};
        }
        return null;
    }

    static void beginPositioning(GPSLocationProvider provider) {
        synchronized (LOCK) {
            if (activeTracking != null) {
                throw new DeviceException(DeviceException.BUSY_RESOURCE, "Tracking is already active");
            }
            if (activePositioning != null && activePositioning != provider) {
                throw new DeviceException(DeviceException.BUSY_RESOURCE, "Positioning is already active");
            }
            activePositioning = provider;
        }
    }

    static void endPositioning(GPSLocationProvider provider) {
        synchronized (LOCK) {
            if (activePositioning == provider) {
                activePositioning = null;
            }
        }
    }

    static void beginTracking(GPSLocationProvider provider) {
        synchronized (LOCK) {
            if (activePositioning != null) {
                throw new DeviceException(DeviceException.BUSY_RESOURCE, "Positioning is already active");
            }
            if (activeTracking != null && activeTracking != provider) {
                throw new DeviceException(DeviceException.BUSY_RESOURCE, "Tracking is already active");
            }
            activeTracking = provider;
        }
    }

    static void endTracking(GPSLocationProvider provider) {
        synchronized (LOCK) {
            if (activeTracking == provider) {
                activeTracking = null;
            }
        }
    }

    static Location currentLocation() {
        Degree latitude = new Degree(opendoja.host.OpenDoJaLaunchArgs.getDouble(opendoja.host.OpenDoJaLaunchArgs.GPS_LATITUDE));
        Degree longitude = new Degree(opendoja.host.OpenDoJaLaunchArgs.getDouble(opendoja.host.OpenDoJaLaunchArgs.GPS_LONGITUDE));
        int altitude = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.GPS_ALTITUDE);
        int datum = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.GPS_DATUM);
        int accuracy = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.GPS_ACCURACY);
        return new Location(latitude, longitude, altitude, datum, System.currentTimeMillis(), accuracy);
    }

    static Degree currentAzimuth() {
        return new Degree(opendoja.host.OpenDoJaLaunchArgs.getDouble(opendoja.host.OpenDoJaLaunchArgs.COMPASS_AZIMUTH));
    }

    static long positioningDelayMillis() {
        return Math.max(0L, opendoja.host.OpenDoJaLaunchArgs.getLong(opendoja.host.OpenDoJaLaunchArgs.GPS_DELAY_MILLIS));
    }

    static LocationException configuredLocationFailure() {
        String raw = opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.GPS_FAILURE).trim().toLowerCase(Locale.ROOT);
        if (raw.isEmpty()) {
            return null;
        }
        return switch (raw) {
            case "out-of-service", "out_of_service" ->
                    new LocationException(LocationException.OUT_OF_SERVICE, "Configured GPS failure: out of service");
            case "timeout" ->
                    new LocationException(LocationException.TIMEOUT, "Configured GPS failure: timeout");
            case "interrupted" ->
                    new LocationException(LocationException.INTERRUPTED, "Configured GPS failure: interrupted");
            case "user-abort", "user_abort" ->
                    new LocationException(LocationException.USER_ABORT, "Configured GPS failure: user abort");
            case "self-mode", "self_mode" ->
                    new LocationException(LocationException.SELF_MODE, "Configured GPS failure: self mode");
            default ->
                    new LocationException(LocationException.UNDEFINED, "Configured GPS failure: " + raw);
        };
    }

    static int[] availableAccelerationData() {
        return parseIntList(
                opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.ACCELERATION_AVAILABLE_DATA),
                new int[]{
                        AccelerationSensor.ACCELERATION_X,
                        AccelerationSensor.ACCELERATION_Y,
                        AccelerationSensor.ACCELERATION_Z,
                        AccelerationSensor.ROLL,
                        AccelerationSensor.PITCH,
                        AccelerationSensor.SCREEN_ORIENTATION
                }
        );
    }

    static int[] availableAccelerationEvents() {
        if (!opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.ACCELERATION_EVENT_SUPPORTED)) {
            return null;
        }
        return parseIntList(
                opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.ACCELERATION_AVAILABLE_EVENT),
                new int[]{AccelerationSensor.EVENT_SCREEN_ORIENTATION, AccelerationSensor.EVENT_DOUBLE_TAP}
        );
    }

    static int accelerationIntervalResolution() {
        return Math.max(1, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.ACCELERATION_INTERVAL_RESOLUTION));
    }

    static int maxAccelerationDataSize() {
        return Math.max(1, opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.ACCELERATION_MAX_DATA_SIZE));
    }

    static int[] accelerationSample() {
        return parseSixTuple(opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.ACCELERATION_CURRENT));
    }

    static int screenOrientationEventValue() {
        return opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.ACCELERATION_EVENT_SCREEN_ORIENTATION);
    }

    static int doubleTapEventValue() {
        return opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.ACCELERATION_EVENT_DOUBLE_TAP);
    }

    static int minAccelerationValue(int type) {
        return switch (type) {
            case AccelerationSensor.ACCELERATION_X,
                 AccelerationSensor.ACCELERATION_Y,
                 AccelerationSensor.ACCELERATION_Z -> -4000;
            case AccelerationSensor.ROLL -> -180;
            case AccelerationSensor.PITCH -> -180;
            case AccelerationSensor.SCREEN_ORIENTATION -> 0;
            default -> throw new IllegalArgumentException("type out of range: " + type);
        };
    }

    static int maxAccelerationValue(int type) {
        return switch (type) {
            case AccelerationSensor.ACCELERATION_X,
                 AccelerationSensor.ACCELERATION_Y,
                 AccelerationSensor.ACCELERATION_Z -> 4000;
            case AccelerationSensor.ROLL -> 179;
            case AccelerationSensor.PITCH -> 180;
            case AccelerationSensor.SCREEN_ORIENTATION -> 270;
            default -> throw new IllegalArgumentException("type out of range: " + type);
        };
    }

    static UnsupportedOperationException unsupported(String message) {
        return new UnsupportedOperationException(message);
    }

    private static int[] parseIntList(String raw, int[] fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback.clone();
        }
        String[] tokens = raw.split("[, ]+");
        List<Integer> values = new ArrayList<>();
        for (String token : tokens) {
            if (token.isBlank()) {
                continue;
            }
            try {
                values.add(Integer.decode(token.trim()));
            } catch (NumberFormatException ignored) {
            }
        }
        if (values.isEmpty()) {
            return fallback.clone();
        }
        int[] result = new int[values.size()];
        for (int i = 0; i < values.size(); i++) {
            result[i] = values.get(i);
        }
        return result;
    }

    private static int[] parseSixTuple(String raw) {
        int[] sample = new int[]{0, 0, 1000, 0, 0, 0};
        if (raw == null || raw.isBlank()) {
            return sample;
        }
        String[] tokens = raw.split("[, ]+");
        for (int i = 0; i < sample.length && i < tokens.length; i++) {
            try {
                sample[i] = Integer.decode(tokens[i].trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return sample;
    }
}
