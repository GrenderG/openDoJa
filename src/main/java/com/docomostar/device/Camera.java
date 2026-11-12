package com.docomostar.device;

import com.docomostar.media.*;
import com.docomostar.system.InterruptedOperationException;
import java.io.InputStream;

/** STAR camera view of the shared DoJa capture service. */
public class Camera {
    public static final int ATTR_CONTINUOUS_SHOT_OFF = 0;
    public static final int ATTR_CONTINUOUS_SHOT_ON = 1;
    public static final int ATTR_QUALITY_HIGH = 0;
    public static final int ATTR_QUALITY_STANDARD = 1;
    public static final int ATTR_QUALITY_LOW = 2;
    public static final int ATTR_VOLUME_MIN = 0;
    public static final int ATTR_VOLUME_MAX = 127;
    public static final int ATTR_FRAME_OFF = 0;
    public static final int ATTR_FRAME_ON = 1;
    public static final int DEV_CONTINUOUS_SHOT = 0;
    public static final int DEV_QUALITY = 1;
    public static final int DEV_SOUND = 2;
    public static final int DEV_CONTINUOUS_IMAGES = 3;
    public static final int DEV_FRAME_SHOT = 4;
    public static final int FOCUS_MACRO_MODE = 1;
    public static final int FOCUS_NORMAL_MODE = 0;
    public static final int FOCUS_HARDWARE_SWITCH = -1;
    public static final int FOCUS_AUTO_MODE = 2;
    public static final int FOCUS_CONTINUOUS_AUTO_MODE = 3;
    private static final java.util.Map<Integer, Camera> CAMERAS = new java.util.HashMap<>();
    private final com.nttdocomo.device.Camera delegate;
    protected Camera() { delegate = new HostCamera(); }
    private Camera(com.nttdocomo.device.Camera delegate) { this.delegate = delegate; }
    private static final class HostCamera extends com.nttdocomo.device.Camera { }
    public static synchronized Camera getCamera(int id) {
        var host = _DeviceExceptions.call(() -> com.nttdocomo.device.Camera.getCamera(id));
        return CAMERAS.computeIfAbsent(id, ignored -> new Camera(host));
    }
    public void takePicture() throws InterruptedOperationException {
        _DeviceExceptions.interruptible(() -> { delegate.takePicture(); return null; });
    }
    public void takeMovie() throws InterruptedOperationException {
        _DeviceExceptions.interruptible(() -> { delegate.takeMovie(); return null; });
    }
    public int getNumberOfImages() { return _DeviceExceptions.call(delegate::getNumberOfImages); }
    public long getImageLength(int index) { return _DeviceExceptions.call(() -> delegate.getImageLength(index)); }
    public long getMaxMovieLength() { return _DeviceExceptions.call(delegate::getMaxImageLength); }
    public MediaResource getImage(int index) {
        try { return MediaManager.wrapImage(_DeviceExceptions.call(() -> delegate.getImage(index))); }
        catch (com.nttdocomo.ui.UIException failure) { throw MediaException.from(failure); }
    }
    public InputStream getInputStream(int index) { return _DeviceExceptions.call(() -> delegate.getInputStream(index)); }
    public void disposeImages() { _DeviceExceptions.run(delegate::disposeImages); }
    public void setFrameImage(MediaImage image) {
        try { _DeviceExceptions.run(() -> delegate.setFrameImage((com.nttdocomo.ui.MediaImage)MediaManager.unwrap(image))); }
        catch (com.nttdocomo.ui.UIException failure) { throw MediaException.from(failure); }
    }
    public int[][] getAvailableFrameSizes() { return _DeviceExceptions.call(delegate::getAvailableFrameSizes); }
    public int[][] getAvailablePictureSizes() { return _DeviceExceptions.call(delegate::getAvailablePictureSizes); }
    public int[][] getAvailableMovieSizes() { return _DeviceExceptions.call(delegate::getAvailableMovieSizes); }
    public void setImageSize(int width, int height) { _DeviceExceptions.run(() -> delegate.setImageSize(width, height)); }
    public boolean isAvailable(int attribute) { return _DeviceExceptions.call(() -> delegate.isAvailable(attribute)); }
    public void setAttribute(int attribute, int value) { _DeviceExceptions.run(() -> delegate.setAttribute(attribute, value)); }
    public int getAttribute(int attribute) { return _DeviceExceptions.call(() -> delegate.getAttribute(attribute)); }
    public int[] getAvailableFocusModes() { return _DeviceExceptions.call(delegate::getAvailableFocusModes); }
    public void setFocusMode(int mode) { _DeviceExceptions.run(() -> delegate.setFocusMode(mode)); }
    public int getFocusMode() { return _DeviceExceptions.call(delegate::getFocusMode); }
}
