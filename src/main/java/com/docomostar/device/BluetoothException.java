package com.docomostar.device;

/** STAR Bluetooth status codes; transport behavior remains in the DoJa stub. */
public class BluetoothException extends com.nttdocomo.device.BluetoothException {
    public static final int ENCRYPTION_ERROR = 6;
    public static final int HANDOVER_ERROR = 7;
    public static final int BUSY_RESOURCE = 8;

    public BluetoothException() { super(); }
    public BluetoothException(int status) { super(status); }
    public BluetoothException(int status, String message) { super(status, message); }

    public static BluetoothException from(com.nttdocomo.device.BluetoothException failure) {
        if (failure instanceof BluetoothException star) return star;
        BluetoothException translated = new BluetoothException(failure.getStatus(), failure.getMessage());
        translated.initCause(failure);
        return translated;
    }
}
