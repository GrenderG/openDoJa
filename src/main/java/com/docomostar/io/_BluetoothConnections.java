package com.docomostar.io;

import com.docomostar.device.BTStateListener;
import com.docomostar.device.DeviceException;
import com.docomostar.device._DeviceExceptions;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Adapts the existing SPP stub and its disconnect callbacks. */
public final class _BluetoothConnections {
    private _BluetoothConnections() { }

    public static BTConnection wrap(com.nttdocomo.io.BTConnection connection) {
        if (connection == null) return null;
        if (connection instanceof com.nttdocomo.io.SPPConnection spp) return new HostSpp(spp);
        throw new DeviceException(DeviceException.NO_RESOURCES, "Unsupported Bluetooth connection profile");
    }

    private static final class HostSpp implements SPPConnection {
        private final com.nttdocomo.io.SPPConnection delegate;
        HostSpp(com.nttdocomo.io.SPPConnection delegate) { this.delegate = delegate; }
        @Override public InputStream openInputStream() throws IOException {
            try { return delegate.openInputStream(); }
            catch (com.nttdocomo.io.ConnectionException failure) { throw ConnectionException.from(failure); }
            catch (RuntimeException failure) { throw _DeviceExceptions.translate(failure); }
        }
        @Override public OutputStream openOutputStream() throws IOException {
            try { return delegate.openOutputStream(); }
            catch (com.nttdocomo.io.ConnectionException failure) { throw ConnectionException.from(failure); }
            catch (RuntimeException failure) { throw _DeviceExceptions.translate(failure); }
        }
        @Override public void close() { _DeviceExceptions.run(delegate::close); }
        @Override public void setBTStateListener(BTStateListener listener) {
            _DeviceExceptions.run(() -> delegate.setBTStateListener(listener == null ? null : listener::stateChanged));
        }
    }
}
