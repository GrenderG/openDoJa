package com.docomostar.media;

/** Failure reported by STAR media resources, with the host status preserved. */
public class MediaException extends RuntimeException {
    public static final int UNDEFINED = 0;
    public static final int ILLEGAL_STATE = 1;
    public static final int NO_RESOURCES = 2;
    public static final int BUSY_RESOURCE = 3;
    public static final int UNSUPPORTED_FORMAT = 4;
    private final int status;

    public MediaException() { this(UNDEFINED); }
    public MediaException(int status) { this(status, null); }
    public MediaException(int status, String message) { super(message); this.status = status; }
    public int getStatus() { return status; }
    public static MediaException from(com.nttdocomo.ui.UIException failure) {
        var translated = new MediaException(failure.getStatus(), failure.getMessage());
        translated.initCause(failure);
        return translated;
    }
}
