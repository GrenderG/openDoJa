package opendoja.host.device;

import com.nttdocomo.device.Camera;
import com.nttdocomo.device.CodeReader;
import com.nttdocomo.device.DeviceException;
import com.nttdocomo.device.gesturereader.GestureException;
import com.nttdocomo.device.gesturereader.GestureReader;
import com.nttdocomo.opt.device.Pulsemeter;
import opendoja.host.DoJaRuntime;

import java.awt.Dimension;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Internal desktop-host support for the DoJa camera, code-reader, and
 * gesture-recognition APIs.
 */
public final class DoJaCameraSupport {
    private static final Object LOCK = new Object();
    private static ActiveNativeOperation activeNative;
    private static GestureRegistration activeGesture;
    private static PulsemeterRegistration activePulsemeter;

    private static final int[] AVAILABLE_CODES = {
            CodeReader.CODE_AUTO,
            CodeReader.CODE_JAN8,
            CodeReader.CODE_JAN13,
            CodeReader.CODE_QR,
            CodeReader.CODE_OCR,
            CodeReader.CODE_MICRO_QR,
            CodeReader.CODE_NW7,
            CodeReader.CODE_39,
            9,
            10
    };
    private static final int[] AVAILABLE_FOCUS_MODES = {
            Camera.FOCUS_NORMAL_MODE
    };

    private DoJaCameraSupport() {
    }

    /**
     * Returns the number of camera devices that can be controlled from Java.
     *
     * @return the supported camera-device count
     */
    public static int getCameraCount() {
        return 1;
    }

    /**
     * Validates a camera identifier.
     *
     * @param id the camera ID
     * @throws IllegalArgumentException if the ID is outside the supported range
     */
    public static void validateCameraId(int id) {
        if (id < 0 || id >= getCameraCount()) {
            throw new IllegalArgumentException("camera id out of range: " + id);
        }
    }

    /**
     * Returns whether gesture recognition is supported for the specified
     * camera.
     *
     * @param id the camera ID
     * @return {@code true} if gesture recognition is supported
     */
    public static boolean isGestureSupported(int id) {
        return id == 0;
    }

    /**
     * Gets the picture sizes supported by the desktop host.
     *
     * @return a deep copy of the supported picture-size table
     */
    public static int[][] getAvailablePictureSizes() {
        return copySizes(resolveCaptureSizes());
    }

    /**
     * Gets the frame-image sizes supported by the desktop host.
     *
     * @return a deep copy of the supported frame-image size table
     */
    public static int[][] getAvailableFrameSizes() {
        return copySizes(resolveCaptureSizes());
    }

    /**
     * Gets the movie sizes supported by the desktop host.
     *
     * @return always {@code null}; movie capture is unsupported on this host
     */
    public static int[][] getAvailableMovieSizes() {
        return copySizes(resolveCaptureSizes());
    }

    /**
     * Gets the largest movie size that can currently be captured.
     *
     * @return the maximum movie data size in bytes, or {@code 0} when movie
     *         capture is unavailable
     */
    public static long getMaxMovieLength() {
        return opendoja.host.OpenDoJaLaunchArgs.getLong(opendoja.host.OpenDoJaLaunchArgs.CAMERA_MAX_MOVIE_LENGTH);
    }

    /**
     * Resolves the actual capture size that will be used for the specified
     * request.
     *
     * @param requestedWidth the requested width, or {@code 0} if not specified
     * @param requestedHeight the requested height, or {@code 0} if not specified
     * @return the actual capture size
     */
    public static Dimension resolveCaptureSize(int requestedWidth, int requestedHeight) {
        int[][] sizes = resolveCaptureSizes();
        if (sizes.length == 0) {
            return new Dimension(240, 240);
        }
        if (requestedWidth <= 0 || requestedHeight <= 0) {
            return new Dimension(sizes[0][0], sizes[0][1]);
        }
        for (int[] size : sizes) {
            if (size[0] >= requestedWidth && size[1] >= requestedHeight) {
                return new Dimension(size[0], size[1]);
            }
        }
        int[] largest = sizes[sizes.length - 1];
        return new Dimension(largest[0], largest[1]);
    }

    /**
     * Returns the focus modes supported by the desktop host.
     *
     * @return a copy of the supported focus-mode list
     */
    public static int[] getAvailableFocusModes() {
        return AVAILABLE_FOCUS_MODES.clone();
    }

    /**
     * Returns the code types supported by the desktop host.
     *
     * @return a copy of the supported code-type list
     */
    public static int[] getAvailableCodes() {
        return AVAILABLE_CODES.clone();
    }

    /**
     * Returns the maximum burst count for the current still-image settings.
     *
     * @param frameImagePresent whether a frame image is set
     * @return the maximum burst count
     */
    public static int getMaxContinuousImages(boolean frameImagePresent) {
        return frameImagePresent ? 1 : 3;
    }

    /**
     * Acquires the shared native camera device for a synchronous camera or
     * code-reader operation.
     *
     * @param cameraId the camera ID
     * @param operation the operation name used in diagnostics
     * @return a release handle that must be run exactly once
     * @throws DeviceException if the shared camera device is already busy
     */
    public static Runnable beginNativeOperation(int cameraId, String operation) {
        validateCameraId(cameraId);
        synchronized (LOCK) {
            if (activeNative != null && activeNative.cameraId == cameraId) {
                throw new DeviceException(DeviceException.BUSY_RESOURCE,
                        "Camera device is busy: " + activeNative.operation);
            }
            if (activePulsemeter != null && activePulsemeter.cameraId == cameraId) {
                throw new DeviceException(DeviceException.BUSY_RESOURCE,
                        "Camera device is busy with pulse measurement");
            }
            activeNative = new ActiveNativeOperation(cameraId, operation);
        }
        return () -> releaseNativeOperation(cameraId, operation);
    }

    /**
     * Starts gesture recognition for the specified reader.
     *
     * @param reader the reader that is entering the START state
     * @param cameraId the camera ID used by the reader
     * @throws DeviceException if another gesture reader is already in the START
     *         state
     * @throws GestureException if the native camera or code-reader function is
     *         running
     */
    public static void startGesture(GestureReader reader, int cameraId) {
        validateCameraId(cameraId);
        if (!isGestureSupported(cameraId)) {
            throw new java.lang.UnsupportedOperationException("Gesture recognition is not supported for camera " + cameraId);
        }
        synchronized (LOCK) {
            if (activeGesture != null) {
                if (activeGesture.reader == reader) {
                    return;
                }
                throw new DeviceException(DeviceException.BUSY_RESOURCE,
                        "Another GestureReader is already in the START state");
            }
            if (activePulsemeter != null) {
                throw new DeviceException(DeviceException.BUSY_RESOURCE,
                        "Pulse measurement is already in the START state");
            }
            if (activeNative != null && activeNative.cameraId == cameraId) {
                throw new GestureException(GestureException.CAMERA_CONFLICT,
                        "Native camera function is running for camera " + cameraId);
            }
            activeGesture = new GestureRegistration(reader, cameraId);
        }
    }

    /**
     * Stops gesture recognition for the specified reader.
     *
     * @param reader the reader to stop
     */
    public static void stopGesture(GestureReader reader) {
        synchronized (LOCK) {
            if (activeGesture != null && activeGesture.reader == reader) {
                activeGesture = null;
            }
        }
    }

    /**
     * Checks whether the specified reader is currently in the START state.
     *
     * @param reader the reader to test
     * @return {@code true} if the reader is active
     */
    public static boolean isGestureStarted(GestureReader reader) {
        synchronized (LOCK) {
            return activeGesture != null && activeGesture.reader == reader;
        }
    }

    /**
     * Starts pulse measurement for the specified pulsemeter.
     *
     * @param pulsemeter the pulsemeter entering the START state
     * @param cameraId the associated camera ID
     * @throws DeviceException if another pulsemeter or gesture reader is in the
     *         START state, or if the native camera/code-reader function is
     *         active
     */
    public static void startPulsemeter(Pulsemeter pulsemeter, int cameraId) {
        validateCameraId(cameraId);
        synchronized (LOCK) {
            if (activePulsemeter != null) {
                if (activePulsemeter.pulsemeter == pulsemeter) {
                    return;
                }
                throw new DeviceException(DeviceException.BUSY_RESOURCE,
                        "Another Pulsemeter is already in the START state");
            }
            if (activeGesture != null) {
                throw new DeviceException(DeviceException.BUSY_RESOURCE,
                        "A GestureReader is already in the START state");
            }
            if (activeNative != null && activeNative.cameraId == cameraId) {
                throw new DeviceException(DeviceException.RACE_CONDITION,
                        "Native camera function is running for camera " + cameraId);
            }
            activePulsemeter = new PulsemeterRegistration(pulsemeter, cameraId);
        }
    }

    /**
     * Stops pulse measurement for the specified pulsemeter.
     *
     * @param pulsemeter the pulsemeter to stop
     */
    public static void stopPulsemeter(Pulsemeter pulsemeter) {
        synchronized (LOCK) {
            if (activePulsemeter != null && activePulsemeter.pulsemeter == pulsemeter) {
                activePulsemeter = null;
            }
        }
    }

    /**
     * Checks whether the specified pulsemeter is in the START state.
     *
     * @param pulsemeter the pulsemeter to test
     * @return {@code true} if the pulsemeter is active
     */
    public static boolean isPulsemeterStarted(Pulsemeter pulsemeter) {
        synchronized (LOCK) {
            return activePulsemeter != null && activePulsemeter.pulsemeter == pulsemeter;
        }
    }

    private static int[][] resolveCaptureSizes() {
        int width = 240;
        int height = 240;
        DoJaRuntime runtime = DoJaRuntime.current();
        if (runtime != null) {
            width = java.lang.Math.max(1, runtime.displayWidth());
            height = java.lang.Math.max(1, runtime.displayHeight());
        }
        int[][] sizes = {
                {width, height}
        };
        Arrays.sort(sizes, Comparator.comparingInt((int[] size) -> size[0] * size[1])
                .thenComparingInt(size -> size[0]));
        return sizes;
    }

    private static int[][] copySizes(int[][] source) {
        if (source == null) {
            return null;
        }
        int[][] copy = new int[source.length][];
        for (int i = 0; i < source.length; i++) {
            copy[i] = source[i].clone();
        }
        return copy;
    }

    private static void releaseNativeOperation(int cameraId, String operation) {
        synchronized (LOCK) {
            if (activeNative != null
                    && activeNative.cameraId == cameraId
                    && activeNative.operation.equals(operation)) {
                activeNative = null;
            }
        }
    }

    private record ActiveNativeOperation(int cameraId, String operation) {
    }

    private record GestureRegistration(GestureReader reader, int cameraId) {
    }

    private record PulsemeterRegistration(Pulsemeter pulsemeter, int cameraId) {
    }
}
