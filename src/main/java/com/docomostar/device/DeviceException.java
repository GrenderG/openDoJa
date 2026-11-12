package com.docomostar.device;

/** STAR view of the shared device error status and message. */
public class DeviceException extends com.nttdocomo.device.DeviceException {
    public DeviceException() { super(); }
    public DeviceException(int status) { super(status); }
    public DeviceException(int status, String message) { super(status, message); }

    public static DeviceException from(com.nttdocomo.device.DeviceException failure) {
        if (failure instanceof DeviceException star) return star;
        DeviceException translated = new DeviceException(failure.getStatus(), failure.getMessage());
        translated.initCause(failure);
        return translated;
    }
}
