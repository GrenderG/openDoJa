package com.nttdocomo.device;

import com.nttdocomo.io.SPPConnection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class _BluetoothSupport {
    private static final _BluetoothSupport INSTANCE = new _BluetoothSupport();
    private static final Bluetooth BLUETOOTH = new Bluetooth();

    private final Object lock = new Object();
    private final LinkedHashMap<String, DeviceDescriptor> discoveredDevices = new LinkedHashMap<>();
    private final Map<String, RemoteDevice> liveDevices = new HashMap<>();

    private _BluetoothSupport() {
    }

    static _BluetoothSupport instance() {
        return INSTANCE;
    }

    static Bluetooth bluetooth() {
        return BLUETOOTH;
    }

    boolean isSupported() {
        return opendoja.host.OpenDoJaLaunchArgs.getBoolean(opendoja.host.OpenDoJaLaunchArgs.BLUETOOTH_SUPPORTED);
    }

    RemoteDevice scan() {
        synchronized (lock) {
            DeviceDescriptor target = selectScanTarget(configuredDevices());
            if (target == null) {
                return null;
            }
            discoveredDevices.put(target.address(), target);
            return toRemoteDevice(target);
        }
    }

    RemoteDevice selectDevice() {
        synchronized (lock) {
            if (discoveredDevices.isEmpty()) {
                return null;
            }
            List<DeviceDescriptor> known = new ArrayList<>(discoveredDevices.values());
            return toRemoteDevice(known.get(selectedIndex(known.size(), opendoja.host.OpenDoJaLaunchArgs.BLUETOOTH_SELECTION_INDEX)));
        }
    }

    RemoteDevice searchAndSelectDevice() {
        synchronized (lock) {
            List<DeviceDescriptor> configured = configuredDevices();
            if (configured.isEmpty()) {
                return null;
            }
            for (DeviceDescriptor descriptor : configured) {
                discoveredDevices.put(descriptor.address(), descriptor);
            }
            return toRemoteDevice(configured.get(selectedIndex(configured.size(), opendoja.host.OpenDoJaLaunchArgs.BLUETOOTH_SEARCH_SELECTION_INDEX)));
        }
    }

    int getDiscoveredDeviceCount() {
        synchronized (lock) {
            return discoveredDevices.size();
        }
    }

    void turnOff() {
        synchronized (lock) {
            discoveredDevices.clear();
        }
    }

    SPPConnection openConnection(RemoteDevice device) throws IOException {
        device.ensureAvailable();
        device.connectionOpened();
        return new LoopbackSppConnection(device);
    }

    private RemoteDevice toRemoteDevice(DeviceDescriptor descriptor) {
        RemoteDevice existing = liveDevices.get(descriptor.address());
        if (existing != null && !existing.isDisposed()) {
            return existing;
        }
        RemoteDevice created = new RemoteDevice(
                descriptor.address(),
                descriptor.name(),
                descriptor.deviceClass(),
                descriptor.supportsSpp()
        );
        liveDevices.put(descriptor.address(), created);
        return created;
    }

    private static DeviceDescriptor selectScanTarget(List<DeviceDescriptor> configured) {
        if (configured.isEmpty()) {
            return null;
        }
        int configuredIndex = opendoja.host.OpenDoJaLaunchArgs.getInt(opendoja.host.OpenDoJaLaunchArgs.BLUETOOTH_SCAN_INDEX);
        if (configuredIndex >= 0 && configuredIndex < configured.size()) {
            return configured.get(configuredIndex);
        }
        for (DeviceDescriptor descriptor : configured) {
            if (descriptor.incoming()) {
                return descriptor;
            }
        }
        return configured.get(0);
    }

    private static int selectedIndex(int size, String property) {
        if (size <= 0) {
            return 0;
        }
        int index = opendoja.host.OpenDoJaLaunchArgs.getInt(property);
        if (index < 0) {
            return 0;
        }
        if (index >= size) {
            return size - 1;
        }
        return index;
    }

    private static List<DeviceDescriptor> configuredDevices() {
        String raw = opendoja.host.OpenDoJaLaunchArgs.get(opendoja.host.OpenDoJaLaunchArgs.BLUETOOTH_DEVICES).trim();
        if (raw.isEmpty()) {
            return List.of();
        }
        List<DeviceDescriptor> devices = new ArrayList<>();
        String[] entries = raw.split(";");
        for (String entry : entries) {
            if (entry.isBlank()) {
                continue;
            }
            String[] columns = entry.split("\\|", -1);
            String address = column(columns, 0, "001122334455");
            String name = column(columns, 1, address);
            String deviceClass = column(columns, 2, "0x000000");
            boolean supportsSpp = parseProfile(column(columns, 3, "SPP"));
            boolean incoming = parseBoolean(column(columns, 4, "false"));
            devices.add(new DeviceDescriptor(address, name, deviceClass, supportsSpp, incoming));
        }
        return devices;
    }

    private static String column(String[] columns, int index, String fallback) {
        if (index >= columns.length) {
            return fallback;
        }
        String value = columns[index].trim();
        return value.isEmpty() ? fallback : value;
    }

    private static boolean parseProfile(String value) {
        return value.toUpperCase(Locale.ROOT).contains("SPP");
    }

    private static boolean parseBoolean(String value) {
        return value.equalsIgnoreCase("true")
                || value.equalsIgnoreCase("yes")
                || value.equalsIgnoreCase("on")
                || value.equals("1");
    }

    private record DeviceDescriptor(
            String address,
            String name,
            String deviceClass,
            boolean supportsSpp,
            boolean incoming
    ) {
    }

    private static final class LoopbackSppConnection implements SPPConnection {
        private final RemoteDevice owner;
        private final PipedInputStream input;
        private final PipedOutputStream output;
        private BTStateListener listener;
        private boolean closed;

        private LoopbackSppConnection(RemoteDevice owner) throws IOException {
            this.owner = owner;
            this.input = new PipedInputStream(4096);
            this.output = new PipedOutputStream(input);
        }

        @Override
        public synchronized InputStream openInputStream() {
            return input;
        }

        @Override
        public synchronized OutputStream openOutputStream() {
            return output;
        }

        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            closed = true;
            owner.connectionClosed();
            try {
                output.close();
            } catch (IOException ignored) {
            }
            try {
                input.close();
            } catch (IOException ignored) {
            }
            if (listener != null) {
                listener.stateChanged(BTStateListener.DISCONNECT);
            }
        }

        @Override
        public synchronized void setBTStateListener(BTStateListener listener) {
            this.listener = listener;
        }
    }
}
