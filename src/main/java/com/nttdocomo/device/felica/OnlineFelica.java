package com.nttdocomo.device.felica;

import com.nttdocomo.device.DeviceException;
import com.nttdocomo.io.ConnectionException;

import javax.microedition.io.ConnectionNotFoundException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Represents online FeliCa processing.
 */
public final class OnlineFelica {
    /** Represents the default device ID for the built-in FeliCa reader/writer (=1). */
    public static final int DEVICE_ID_FELICA = 1;
    /** Represents the default device ID for the built-in display (=2). */
    public static final int DEVICE_ID_DISPLAY = 2;

    private final Map<Integer, Device> devices = new LinkedHashMap<>();
    private OnlineListener listener;
    private int nextDeviceId = 3;
    private ScheduledFuture<?> completionTask;

    OnlineFelica() {
        devices.put(DEVICE_ID_FELICA, new Device("FeliCa", "R/W"));
        devices.put(DEVICE_ID_DISPLAY, new Device("Generic", "Display"));
    }

    /**
     * Registers the listener that receives online-processing callbacks.
     *
     * @param listener the listener to register, or {@code null} to clear it
     * @throws DeviceException if online processing has already started
     *         ({@link DeviceException#ILLEGAL_STATE})
     */
    public void setOnlineListener(OnlineListener listener) {
        if (FelicaSupport.onlineProcessing) {
            throw new DeviceException(DeviceException.ILLEGAL_STATE,
                    "Online processing has already started");
        }
        this.listener = listener;
    }

    /**
     * Adds a device entry for online processing.
     *
     * @param type the device type string
     * @param name the device name string
     * @return the registered device ID
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code type} or {@code name} is outside the documented ASCII range
     * @throws IllegalStateException if the maximum device count has been reached
     */
    public int addDevice(String type, String name) {
        FelicaSupport.requireOpen();
        if (type == null) {
            throw new NullPointerException("type");
        }
        if (name == null) {
            throw new NullPointerException("name");
        }
        validateAsciiIdentifier(type, "type");
        validateAsciiIdentifier(name, "name");
        int existing = getDeviceID(type, name);
        if (existing >= 0) {
            return existing;
        }
        if (devices.size() >= 64) {
            throw new java.lang.IllegalStateException("The maximum online-device count has been reached");
        }
        int id = nextDeviceId++;
        devices.put(id, new Device(type, name));
        return id;
    }

    /**
     * Gets the registered device name for the specified device ID.
     *
     * @param deviceID the device ID
     * @return the registered device name, or {@code null} if the device is unknown
     */
    public String getDeviceName(int deviceID) {
        Device device = devices.get(deviceID);
        return device == null ? null : device.name;
    }

    /**
     * Gets the registered device type for the specified device ID.
     *
     * @param deviceID the device ID
     * @return the registered device type, or {@code null} if the device is unknown
     */
    public String getDeviceType(int deviceID) {
        Device device = devices.get(deviceID);
        return device == null ? null : device.type;
    }

    /**
     * Gets the device ID that matches the specified type and name.
     *
     * @param type the device type string
     * @param name the device name string
     * @return the matching device ID, or {@code -1} if no device matches
     * @throws NullPointerException if an argument is {@code null}
     * @throws IllegalArgumentException if {@code type} or {@code name} is outside the documented ASCII range
     */
    public int getDeviceID(String type, String name) {
        if (type == null) {
            throw new NullPointerException("type");
        }
        if (name == null) {
            throw new NullPointerException("name");
        }
        validateAsciiIdentifier(type, "type");
        validateAsciiIdentifier(name, "name");
        for (Map.Entry<Integer, Device> entry : devices.entrySet()) {
            if (entry.getValue().type.equals(type) && entry.getValue().name.equals(name)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    /**
     * Clears the device list added by {@link #addDevice(String, String)}.
     *
     * @throws DeviceException if online processing has already started
     *         ({@link DeviceException#ILLEGAL_STATE})
     */
    public void clearDeviceList() {
        FelicaSupport.requireOpen();
        if (FelicaSupport.onlineProcessing) {
            throw new DeviceException(DeviceException.ILLEGAL_STATE,
                    "Online processing has already started");
        }
        devices.keySet().removeIf(id -> id >= 3);
        nextDeviceId = 3;
    }

    /**
     * Starts online FeliCa processing.
     *
     * @param destination the destination URL
     * @throws NullPointerException if {@code destination} is {@code null}
     * @throws IllegalArgumentException if {@code destination} is not a valid URL
     * @throws ConnectionNotFoundException if the URL scheme is not {@code http} or {@code https}
     * @throws DeviceException if FeliCa is not open, the listener is not set, or processing has already started
     *         ({@link DeviceException#ILLEGAL_STATE})
     * @throws ConnectionException if the configured host simulation cannot secure resources
     */
    public void start(String destination) throws ConnectionException, ConnectionNotFoundException {
        FelicaSupport.requireOpen();
        if (destination == null) {
            throw new NullPointerException("destination");
        }
        if (listener == null) {
            throw new DeviceException(DeviceException.ILLEGAL_STATE, "OnlineListener is not set");
        }
        if (FelicaSupport.onlineProcessing) {
            throw new DeviceException(DeviceException.ILLEGAL_STATE, "Online processing has already started");
        }
        validateDestination(destination);
        if (opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.FELICA_ONLINE_NO_RESOURCES)) {
            throw new ConnectionException(ConnectionException.NO_RESOURCES,
                    "Configured FeliCa online resource exhaustion");
        }
        FelicaSupport.onlineProcessing = true;
        int status = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.FELICA_ONLINE_STATUS);
        long delay = opendoja.host.OpenDoJaLaunchArgs.getLong(opendoja.host.OpenDoJaLaunchArgs.FELICA_ONLINE_DELAY_MILLIS);
        completionTask = FelicaSupport.EXECUTOR.schedule(() -> finishOnline(status), delay, TimeUnit.MILLISECONDS);
    }

    /**
     * Stops the active online FeliCa processing.
     * If processing is not active, this method has no effect.
     */
    public void stop() {
        if (!FelicaSupport.onlineProcessing) {
            return;
        }
        if (completionTask != null) {
            completionTask.cancel(false);
            completionTask = null;
        }
        FelicaSupport.onlineProcessing = false;
        if (listener != null) {
            listener.onlineError(OnlineListener.TYPE_INTERRUPTED_ERROR, "Interrupted by user");
        }
    }

    private void finishOnline(int status) {
        FelicaSupport.onlineProcessing = false;
        completionTask = null;
        if (listener != null) {
            listener.onlineFinished(status);
        }
    }

    private static void validateDestination(String destination) throws ConnectionNotFoundException {
        URI uri;
        try {
            uri = new URI(destination);
        } catch (URISyntaxException exception) {
            throw new java.lang.IllegalArgumentException("Invalid online destination: " + destination);
        }
        String scheme = uri.getScheme();
        if (scheme == null) {
            throw new java.lang.IllegalArgumentException("Invalid online destination: " + destination);
        }
        if (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")) {
            throw new ConnectionNotFoundException("Unsupported online scheme: " + scheme);
        }
    }

    private static void validateAsciiIdentifier(String value, String label) {
        if (value.length() < 1 || value.length() > 255) {
            throw new java.lang.IllegalArgumentException(label + " length out of range");
        }
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (ch < 0x21 || ch > 0x7E) {
                throw new java.lang.IllegalArgumentException(label + " contains non-graphic ASCII characters");
            }
        }
    }

    private record Device(String type, String name) {
    }
}
