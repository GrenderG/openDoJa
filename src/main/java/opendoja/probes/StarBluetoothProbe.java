package opendoja.probes;

import com.docomostar.device.*;
import com.docomostar.device.gesturereader.RollReader;
import com.docomostar.io.SPPConnection;
import opendoja.host.OpenDoJaLaunchArgs;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Checks the STAR façade against the existing mock Bluetooth and neutral gesture services. */
public final class StarBluetoothProbe {
    private static int checks;

    public static void main(String[] args) throws Exception {
        Map<String, String> previous = new LinkedHashMap<>();
        for (String key : new String[]{OpenDoJaLaunchArgs.BLUETOOTH_SUPPORTED, OpenDoJaLaunchArgs.BLUETOOTH_DEVICES,
                OpenDoJaLaunchArgs.BLUETOOTH_SCAN_INDEX, OpenDoJaLaunchArgs.BLUETOOTH_SELECTION_INDEX,
                OpenDoJaLaunchArgs.BLUETOOTH_SEARCH_SELECTION_INDEX}) previous.put(key, System.getProperty(key));
        com.nttdocomo.device.Bluetooth nativeBluetooth = null;
        int previousTimeout = 5; boolean previousDetachment = true;
        try {
            System.setProperty(OpenDoJaLaunchArgs.BLUETOOTH_SUPPORTED, "false");
            try { Bluetooth.getInstance(); throw new AssertionError("Disabled Bluetooth accepted"); }
            catch (com.docomostar.lang.UnsupportedOperationException failure) {
                check(failure.getCause() instanceof com.nttdocomo.lang.UnsupportedOperationException, "Unsupported status uses STAR type and native cause");
            }
            System.setProperty(OpenDoJaLaunchArgs.BLUETOOTH_SUPPORTED, "true");
            System.setProperty(OpenDoJaLaunchArgs.BLUETOOTH_DEVICES, "");
            nativeBluetooth = com.nttdocomo.device.Bluetooth.getInstance();
            previousTimeout = nativeBluetooth.getInquiryTimeout(); previousDetachment = nativeBluetooth.isDetachmentMode();
            Bluetooth bluetooth = Bluetooth.getInstance(); bluetooth.turnOff();
            check(bluetooth == Bluetooth.getInstance(), "Stable shared service handle");
            check(bluetooth.getDiscoveredDevice() == 0 && bluetooth.scan() == null
                    && bluetooth.selectDevice() == null && bluetooth.searchAndSelectDevice() == null, "No configured devices means no fake discovery");
            bluetooth.setInquiryTimeout(2); bluetooth.setDetachmentMode(false);
            check(nativeBluetooth.getInquiryTimeout() == 2 && !nativeBluetooth.isDetachmentMode(), "Configuration is shared with DoJa");
            check(bluetooth.isConnectable(Bluetooth.SPP) && !bluetooth.isConnectable(Bluetooth.HDP), "Stub exposes only its implemented profile");
            try { bluetooth.setInquiryTimeout(0); throw new AssertionError("Invalid timeout accepted"); }
            catch (IllegalArgumentException expected) { checks++; }
            try { bluetooth.startPairingByOOB(0, 0, new byte[0]); throw new AssertionError("Hardware pairing fabricated"); }
            catch (com.docomostar.lang.UnsupportedOperationException expected) { checks++; }

            System.setProperty(OpenDoJaLaunchArgs.BLUETOOTH_DEVICES,
                    "001122334455|Loopback|0x000000|SPP|true;AABBCCDDEEFF|Other profile|0x000001|HDP|false");
            System.setProperty(OpenDoJaLaunchArgs.BLUETOOTH_SCAN_INDEX, "0");
            System.setProperty(OpenDoJaLaunchArgs.BLUETOOTH_SELECTION_INDEX, "0");
            System.setProperty(OpenDoJaLaunchArgs.BLUETOOTH_SEARCH_SELECTION_INDEX, "0");
            RemoteDevice remote = bluetooth.scan();
            check("001122334455".equals(remote.getAddress()) && "Loopback".equals(remote.getDeviceName())
                    && "0x000000".equals(remote.getDeviceClass()), "Configured metadata is adapted");
            check(bluetooth.searchAndSelectDevice() != null && bluetooth.getDiscoveredDevice() == 2
                    && "Loopback".equals(bluetooth.selectDevice().getDeviceName()), "Shared discovery and selection list");
            check(remote.isAvailable(Bluetooth.SPP), "Configured SPP capability");
            SPPConnection connection = (SPPConnection) remote.connect(Bluetooth.SPP);
            AtomicInteger disconnects = new AtomicInteger();
            connection.setBTStateListener(state -> {
                check(state == BTStateListener.DISCONNECT, "STAR disconnect state"); disconnects.incrementAndGet();
            });
            var output = connection.openOutputStream(); var input = connection.openInputStream();
            output.write(new byte[]{12, 34, 56}); output.flush();
            check(input.read() == 12 && input.read() == 34 && input.read() == 56, "Existing in-process SPP loopback is reused");
            remote.changePowerMode(RemoteDevice.SNIFF_MODE);
            connection.close(); connection.close();
            check(disconnects.get() == 1, "Closing is idempotent with one disconnect callback");
            expectDevice(DeviceException.ILLEGAL_STATE, () -> remote.changePowerMode(RemoteDevice.SNIFF_MODE));
            remote.interruptAcceptance();
            expectDevice(DeviceException.INTERRUPTED, () -> remote.accept(Bluetooth.SPP));
            SPPConnection accepted = (SPPConnection) remote.accept(Bluetooth.SPP);
            accepted.setBTStateListener(null); accepted.close();
            System.setProperty(OpenDoJaLaunchArgs.BLUETOOTH_SEARCH_SELECTION_INDEX, "1");
            RemoteDevice other = bluetooth.searchAndSelectDevice();
            try { other.connect(Bluetooth.SPP); throw new AssertionError("Unavailable profile accepted"); }
            catch (BluetoothException failure) {
                check(failure.getStatus() == BluetoothException.UNKNOWN_PROFILE
                        && failure.getCause() instanceof com.nttdocomo.device.BluetoothException, "STAR Bluetooth failure status and cause");
            }
            var nativeRemote = nativeBluetooth.selectDevice();
            remote.dispose();
            expectDevice(DeviceException.ILLEGAL_STATE, remote::getDeviceName);
            try { nativeRemote.getDeviceName(); throw new AssertionError("Native disposed device accepted"); }
            catch (com.nttdocomo.device.DeviceException failure) {
                check(!(failure instanceof DeviceException), "DoJa still throws its native error type");
            }
            var roll = RollReader.getRollReader(0); roll.stop();
            expectDevice(DeviceException.ILLEGAL_STATE, roll::getRollData);
            expectDevice(DeviceException.ILLEGAL_STATE, roll::recenter);
            roll.start();
            check(roll.getRollData().getQuality() >= 0, "Shared neutral gesture reader starts");
            for (float motion : roll.getRollData().getImmediateMotion()) check(motion == 0, "Motion remains a neutral stub");
            roll.stop();
            check("日本語 +".equals(com.docomostar.net.URLDecoder.decode(com.nttdocomo.net.URLEncoder.encode("日本語 +"))),
                    "STAR form decoding uses the DoJa charset");
            check(new DeviceException().getStatus() == 0 && new BluetoothException().getStatus() == 0, "Default exception constructors");
            System.out.println("star-bluetooth-probe-ok (" + checks + " checks)");
        } finally {
            if (nativeBluetooth != null) {
                nativeBluetooth.turnOff(); nativeBluetooth.setInquiryTimeout(previousTimeout);
                nativeBluetooth.setDetachmentMode(previousDetachment);
            }
            previous.forEach((key, value) -> { if (value == null) System.clearProperty(key); else System.setProperty(key, value); });
        }
    }

    private static void expectDevice(int status, Runnable action) {
        try { action.run(); throw new AssertionError("Expected device failure"); }
        catch (DeviceException failure) {
            check(failure.getStatus() == status && failure.getCause() instanceof com.nttdocomo.device.DeviceException,
                    "STAR device failure retains native status and cause");
        }
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
}
