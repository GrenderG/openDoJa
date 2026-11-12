package com.docomostar.device;

import com.docomostar.system.InterruptedOperationException;

/** Uses DoJa's configured mock devices and in-process SPP loopback, without a hardware radio. */
public class Bluetooth {
    public static final int SPP = com.nttdocomo.device.Bluetooth.SPP;
    public static final int HDP = 1;
    public static final int OOB_CHANNEL_FELICA = 0;
    public static final int TYPE_INITIATOR = 0;
    public static final int TYPE_RESPONDER = 1;
    private static Bluetooth instance;
    private final com.nttdocomo.device.Bluetooth delegate;

    private Bluetooth(com.nttdocomo.device.Bluetooth delegate) { this.delegate = delegate; }

    public static synchronized Bluetooth getInstance() {
        var host = _DeviceExceptions.call(com.nttdocomo.device.Bluetooth::getInstance);
        if (instance == null) instance = new Bluetooth(host);
        return instance;
    }

    public RemoteDevice scan() throws InterruptedOperationException {
        return RemoteDevice.wrap(_DeviceExceptions.interruptible(delegate::scan));
    }
    public RemoteDevice selectDevice() throws InterruptedOperationException {
        return RemoteDevice.wrap(_DeviceExceptions.interruptible(delegate::selectDevice));
    }
    public RemoteDevice searchAndSelectDevice() throws InterruptedOperationException {
        return RemoteDevice.wrap(_DeviceExceptions.interruptible(delegate::searchAndSelectDevice));
    }
    public RemoteDevice startPairingByOOB(int channel, int type, byte[] data) throws InterruptedOperationException {
        throw new com.docomostar.lang.UnsupportedOperationException("Bluetooth handover pairing is not supported by openDoJa");
    }
    public int getDiscoveredDevice() { return _DeviceExceptions.call(delegate::getDiscoveredDevice); }
    public int getInquiryTimeout() { return _DeviceExceptions.call(delegate::getInquiryTimeout); }
    public void setInquiryTimeout(int timeout) { _DeviceExceptions.run(() -> delegate.setInquiryTimeout(timeout)); }
    public boolean isConnectable(int profile) { return _DeviceExceptions.call(() -> delegate.isConnectable(profile)); }
    public synchronized void turnOff() { _DeviceExceptions.run(delegate::turnOff); }
    public synchronized void setDetachmentMode(boolean mode) { _DeviceExceptions.run(() -> delegate.setDetachmentMode(mode)); }
    public synchronized boolean isDetachmentMode() { return _DeviceExceptions.call(delegate::isDetachmentMode); }
}
