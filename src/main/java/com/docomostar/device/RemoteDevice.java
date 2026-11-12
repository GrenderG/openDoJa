package com.docomostar.device;

import com.docomostar.io.BTConnection;
import com.docomostar.io._BluetoothConnections;

/** STAR handle for a remote-device record supplied by the shared DoJa stub. */
public class RemoteDevice {
    public static final int SNIFF_MODE = com.nttdocomo.device.RemoteDevice.SNIFF_MODE;
    private final com.nttdocomo.device.RemoteDevice delegate;

    private RemoteDevice(com.nttdocomo.device.RemoteDevice delegate) { this.delegate = delegate; }
    static RemoteDevice wrap(com.nttdocomo.device.RemoteDevice device) {
        return device == null ? null : new RemoteDevice(device);
    }

    public String getAddress() { return _DeviceExceptions.call(delegate::getAddress); }
    public String getDeviceName() { return _DeviceExceptions.call(delegate::getDeviceName); }
    public String getDeviceClass() { return _DeviceExceptions.call(delegate::getDeviceClass); }
    public boolean isAvailable(int profile) { return _DeviceExceptions.call(() -> delegate.isAvailable(profile)); }
    public void interruptAcceptance() { _DeviceExceptions.run(delegate::interruptAcceptance); }
    public void dispose() { _DeviceExceptions.run(delegate::dispose); }
    public void changePowerMode(int type) { _DeviceExceptions.run(() -> delegate.changePowerMode(type)); }

    public BTConnection connect(int profile) { return connection(profile, false); }
    public BTConnection accept(int profile) { return connection(profile, true); }

    private BTConnection connection(int profile, boolean accepting) {
        try {
            return _BluetoothConnections.wrap(_DeviceExceptions.interruptible(
                    () -> accepting ? delegate.accept(profile) : delegate.connect(profile)));
        } catch (com.docomostar.system.InterruptedOperationException failure) {
            DeviceException translated = new DeviceException(DeviceException.INTERRUPTED, failure.getMessage());
            translated.initCause(failure);
            throw translated;
        }
    }
}
