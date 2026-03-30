package opendoja.probes;

import com.nttdocomo.io.ConnectionException;
import com.nttdocomo.opt.ui.PointingDevice;
import com.nttdocomo.ui.Audio3D;
import com.nttdocomo.ui.AudioPresenter;
import com.nttdocomo.ui.Canvas;
import com.nttdocomo.ui.ExifData;
import com.nttdocomo.ui.Graphics;
import com.nttdocomo.ui.ShortTimer;
import com.nttdocomo.ui.UIException;
import com.nttdocomo.device.location.Degree;
import com.nttdocomo.device.location.Location;
import com.nttdocomo.device.location.LocationProvider;
import com.nttdocomo.ui.sound3d.PolarPosition;

import java.util.Enumeration;

/**
 * Headless verification for the current second-pass closure batch.
 */
public final class SecondPassProbe {
    private SecondPassProbe() {
    }

    public static void main(String[] args) {
        System.setProperty(opendoja.host.OpenDoJaLaunchArgs.AUDIO3D_RESOURCES, "1");
        System.setProperty(opendoja.host.OpenDoJaLaunchArgs.AUDIO3D_TIME_RESOLUTION_MS, "80");
        System.setProperty(opendoja.host.OpenDoJaLaunchArgs.POINTING_DEVICE_MAX_DIRECTION_Z, "7");

        verifyConnectionException();
        verifyShortTimer();
        verifyPointingDevice();
        verifyAudio3D();
        verifyExifData();

        System.out.println("second-pass-probe-ok");
    }

    private static void verifyConnectionException() {
        ConnectionException exception = new ConnectionException(ConnectionException.TIMEOUT, "timeout");
        assertEquals(ConnectionException.TIMEOUT, exception.getStatus(), "connection status");
        assertEquals(ConnectionException.NO_RESOURCES, ConnectionException.NO_RESOURCE, "deprecated NO_RESOURCE alias");
        assertEquals(ConnectionException.BUSY_RESOURCE, ConnectionException.RESOURCE_BUSY, "deprecated RESOURCE_BUSY alias");
    }

    private static void verifyShortTimer() {
        Canvas canvas = new ProbeCanvas();
        ShortTimer timer = ShortTimer.getShortTimer(canvas, 7, 0, false);
        assertTrue(timer.getMinTimeInterval() > 0, "short-timer minimum interval");
        assertTrue(timer.getResolution() > 0, "short-timer resolution");
        expectUiException(UIException.BUSY_RESOURCE,
                () -> ShortTimer.getShortTimer(canvas, 7, 1, false),
                "duplicate short-timer ID");
        timer.dispose();
        expectUiException(UIException.ILLEGAL_STATE, timer::getResolution, "disposed short-timer resolution");
        ShortTimer recreated = ShortTimer.getShortTimer(canvas, 7, 1, false);
        recreated.dispose();
    }

    private static void verifyPointingDevice() {
        assertTrue(PointingDevice.isAvailable(), "pointing device available");
        PointingDevice.setMode(PointingDevice.MODE_MOUSE);
        PointingDevice.setPosition(12, 34);
        assertEquals(12, PointingDevice.getX(), "mouse x");
        assertEquals(34, PointingDevice.getY(), "mouse y");
        PointingDevice.setMode(PointingDevice.MODE_JOYSTICK);
        PointingDevice.setPosition(56, 78);
        assertEquals(12, PointingDevice.getX(), "joystick mode ignores pointer reposition");
        assertEquals(34, PointingDevice.getY(), "joystick mode keeps prior pointer y");
        assertEquals(7, PointingDevice.getMaxDirectionZ(), "configured joystick z range");
        assertEquals(0, PointingDevice.getDirectionZ(), "default joystick z direction");
    }

    private static void verifyAudio3D() {
        AudioPresenter presenterOne = AudioPresenter.getAudioPresenter();
        AudioPresenter presenterTwo = AudioPresenter.getAudioPresenter();
        Audio3D first = presenterOne.getAudio3D();
        Audio3D second = presenterTwo.getAudio3D();

        assertSame(first, presenterOne.getAudio3D(), "audio3d object reused per presenter");
        assertEquals(1, Audio3D.getResources(), "audio3d resources");
        assertEquals(1, Audio3D.getFreeResources(), "initial free audio3d resources");
        assertEquals(80, first.getTimeResolution(), "audio3d time resolution");

        expectUiException(UIException.ILLEGAL_STATE,
                () -> second.setLocalization(new PolarPosition()),
                "setLocalization requires app-controlled enablement");

        first.enable(Audio3D.MODE_CONTROL_BY_APP);
        assertTrue(first.isEnabled(), "audio3d enabled");
        assertEquals(0, Audio3D.getFreeResources(), "audio3d free resources after enable");
        first.setLocalization(new PolarPosition());
        expectUiException(UIException.NO_RESOURCES,
                () -> second.enable(Audio3D.MODE_CONTROL_BY_APP),
                "audio3d resource exhaustion");
        first.disable();
        assertEquals(1, Audio3D.getFreeResources(), "audio3d resources released on disable");
    }

    private static void verifyExifData() {
        Location source = new Location(
                new Degree(35.681236d),
                new Degree(139.767125d),
                44,
                LocationProvider.DATUM_WGS84,
                1_700_000_000_123L,
                Location.ACCURACY_UNKNOWN);
        ExifData exif = new ExifData();
        exif.update(source);
        Location decoded = exif.toLocation();
        assertTrue(decoded != null, "exif toLocation");
        assertEquals(LocationProvider.DATUM_WGS84, decoded.getDatum(), "exif datum");
        assertEquals(44, decoded.getAltitude(), "exif altitude");
        assertTrue(java.lang.Math.abs(decoded.getLatitude().getFloatingPointNumber() - 35.681236d) < 0.001d,
                "exif latitude");
        assertTrue(java.lang.Math.abs(decoded.getLongitude().getFloatingPointNumber() - 139.767125d) < 0.001d,
                "exif longitude");

        Enumeration<ExifData.TagInfo> tags = exif.enumerateTags();
        assertTrue(tags.hasMoreElements(), "exif enumerate tags");
        ExifData.TagInfo firstTag = tags.nextElement();
        assertEquals(ExifData.GPS_INFO_TAG, firstTag.getTagGroup(), "exif tag group");
    }

    private static void expectUiException(int status, ThrowingRunnable runnable, String label) {
        try {
            runnable.run();
            throw new AssertionError(label + " did not throw UIException");
        } catch (UIException expected) {
            assertEquals(status, expected.getStatus(), label + " status");
        } catch (Exception other) {
            throw new AssertionError(label + " threw wrong exception", other);
        }
    }

    private static void assertTrue(boolean value, String label) {
        if (!value) {
            throw new AssertionError(label);
        }
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected=" + expected + " actual=" + actual);
        }
    }

    private static void assertSame(Object expected, Object actual, String label) {
        if (expected != actual) {
            throw new AssertionError(label + ": expected same instance");
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class ProbeCanvas extends Canvas {
        @Override
        public void paint(Graphics g) {
        }
    }
}
