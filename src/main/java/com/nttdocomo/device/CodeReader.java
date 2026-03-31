package com.nttdocomo.device;

import com.nttdocomo.system.InterruptedOperationException;
import opendoja.host.DoJaEncoding;
import opendoja.host.device.DoJaCameraSupport;

import java.util.HashMap;
import java.util.Map;

/**
 * Defines the code-recognition function.
 * This function calls the handset's native code-reader feature to read
 * barcodes, two-dimensional codes, or OCR content.
 */
public class CodeReader {
    /** Code type meaning automatic recognition (=0). */
    public static final int CODE_AUTO = 0;

    /** Code type meaning JAN-8 (=1). */
    public static final int CODE_JAN8 = 1;

    /** Code type meaning JAN-13 (=2). */
    public static final int CODE_JAN13 = 2;

    /** Code type meaning QR Code (=3). */
    public static final int CODE_QR = 3;

    /** Code type meaning OCR (=4). */
    public static final int CODE_OCR = 4;

    /** Code type meaning Micro QR Code (=6). */
    public static final int CODE_MICRO_QR = 6;

    /** Code type meaning NW-7 (=7). */
    public static final int CODE_NW7 = 7;

    /** Code type meaning Code 39 (=8). */
    public static final int CODE_39 = 8;

    /** Value meaning that the recognized code type is unknown (=-1). */
    public static final int CODE_UNKNOWN = -1;

    /** Value meaning that the code type is unsupported (=-2). */
    public static final int CODE_UNSUPPORTED = -2;

    /** Result type meaning binary data (=0). */
    public static final int TYPE_BINARY = 0;

    /** Result type meaning numeric data (=1). */
    public static final int TYPE_NUMBER = 1;

    /** Result type meaning ASCII data (=2). */
    public static final int TYPE_ASCII = 2;

    /** Result type meaning string data (=3). */
    public static final int TYPE_STRING = 3;

    /** Value meaning that the result type is unknown (=-1). */
    public static final int TYPE_UNKNOWN = -1;

    private static final Map<Integer, CodeReader> READERS = new HashMap<>();

    private final int id;
    private int code = CODE_AUTO;
    private int focusMode = Camera.FOCUS_NORMAL_MODE;
    private CodeResult result = CodeResult.empty();

    /**
     * Applications cannot create code-reader objects directly with this
     * constructor.
     */
    protected CodeReader() {
        this(-1);
    }

    CodeReader(int id) {
        this.id = id;
    }

    /**
     * Gets the code-reader object for the specified camera ID.
     * When this method is called for that camera ID for the first time, a
     * code-reader object is created and returned.
     * After that, a reference to the same object is always returned for the
     * same camera ID.
     *
     * @param id the camera ID
     * @return the code-reader object
     * @throws IllegalArgumentException if {@code id} is negative or is greater
     *         than or equal to the number of camera devices controllable from
     *         Java
     * @throws DeviceException if the camera device cannot be secured
     */
    public static CodeReader getCodeReader(int id) {
        DoJaCameraSupport.validateCameraId(id);
        synchronized (READERS) {
            return READERS.computeIfAbsent(id, CodeReader::new);
        }
    }

    /**
     * Gets the kinds of codes that can be recognized on this host.
     *
     * @return a copy of the supported code-type list
     */
    public int[] getAvailableCodes() {
        return DoJaCameraSupport.getAvailableCodes();
    }

    /**
     * Sets the code kind to be recognized.
     *
     * @param code the code kind to recognize
     * @throws IllegalArgumentException if {@code code} is invalid or is not
     *         supported by this implementation
     */
    public void setCode(int code) {
        if (code == CODE_UNKNOWN || code == CODE_UNSUPPORTED) {
            throw new IllegalArgumentException("Unsupported code kind: " + code);
        }
        int[] availableCodes = getAvailableCodes();
        for (int availableCode : availableCodes) {
            if (availableCode == code) {
                this.code = code;
                return;
            }
        }
        throw new IllegalArgumentException("Unsupported code kind: " + code);
    }

    /**
     * Uses the camera device to perform code recognition.
     * On this desktop host, the operation returns synchronously and clears any
     * previously held recognition result.
     *
     * @throws InterruptedOperationException never thrown by this desktop host
     * @throws DeviceException if the shared camera device is busy
     */
    public void read() throws InterruptedOperationException {
        result = CodeResult.empty();
        Runnable release = DoJaCameraSupport.beginNativeOperation(id, "read");
        try {
            Camera.clearImagesForId(id);
        } finally {
            release.run();
        }
    }

    /**
     * Gets the kind of code that was successfully recognized.
     *
     * @return the recognized code kind, or {@link #CODE_UNKNOWN} if no result
     *         is held
     */
    public int getResultCode() {
        return result.code;
    }

    /**
     * Gets the type of the recognized content.
     *
     * @return the recognized content type, or {@link #TYPE_UNKNOWN} if no
     *         result is held
     */
    public int getResultType() {
        return result.type;
    }

    /**
     * Gets the raw recognition result as a byte array.
     *
     * @return a copy of the recognized bytes, or {@code null} if no result is
     *         held
     */
    public byte[] getBytes() {
        return result.bytes == null ? null : result.bytes.clone();
    }

    /**
     * Gets the recognition result as a string.
     * The byte sequence is converted with the default DoJa character
     * encoding.
     *
     * @return the recognition result as a string, or {@code null} if no result
     *         is held
     */
    public String getString() {
        byte[] bytes = result.bytes;
        return bytes == null ? null : new String(bytes, DoJaEncoding.DEFAULT_CHARSET);
    }

    /**
     * Gets the focus modes that can be set.
     *
     * @return a copy of the supported focus-mode list
     */
    public int[] getAvailableFocusModes() {
        return DoJaCameraSupport.getAvailableFocusModes();
    }

    /**
     * Sets the focus mode.
     *
     * @param mode the focus mode
     * @throws IllegalArgumentException if {@code mode} is invalid
     */
    public void setFocusMode(int mode) {
        for (int availableMode : getAvailableFocusModes()) {
            if (availableMode == mode) {
                if (mode != Camera.FOCUS_HARDWARE_SWITCH) {
                    focusMode = mode;
                }
                return;
            }
        }
        throw new IllegalArgumentException("Unsupported focus mode: " + mode);
    }

    /**
     * Gets the current focus-mode setting.
     *
     * @return the current focus mode
     */
    public int getFocusMode() {
        return focusMode;
    }

    static void clearResultsForId(int id) {
        synchronized (READERS) {
            CodeReader reader = READERS.get(id);
            if (reader != null) {
                reader.result = CodeResult.empty();
            }
        }
    }

    private record CodeResult(int code, int type, byte[] bytes) {
        private static CodeResult empty() {
            return new CodeResult(CODE_UNKNOWN, TYPE_UNKNOWN, null);
        }

        @Override
        public byte[] bytes() {
            return bytes == null ? null : bytes.clone();
        }
    }
}
