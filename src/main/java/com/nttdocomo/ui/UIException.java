package com.nttdocomo.ui;

public class UIException extends RuntimeException {
    public static final int UNDEFINED = 0;
    public static final int ILLEGAL_STATE = 1;
    public static final int NO_RESOURCES = 2;
    public static final int BUSY_RESOURCE = 3;
    public static final int UNSUPPORTED_FORMAT = 4;

    private final int status;

    public UIException() {
        this(UNDEFINED);
    }

    public UIException(int status) {
        this(status, null);
    }

    public UIException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
