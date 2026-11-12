package com.docomostar.system;

public class ContentsException extends Exception {
    public static final int UNDEFINED = 0;
    public static final int RESPONSE_ERROR = 1;
    public static final int NETWORK_ERROR = 2;
    public static final int USER_ABORT = 3;
    public static final int MYMENU_UNREGISTERED = 4;
    public static final int ILLEGAL_CONTENT = 5;
    public static final int NO_RESOURCES = 6;
    public static final int AUTHENTICATION_ERROR = 7;
    private final int status;
    private final String responseBody;
    public ContentsException() { this(UNDEFINED); }
    public ContentsException(String message) { this(UNDEFINED, message); }
    public ContentsException(int status) { this(status, null); }
    public ContentsException(int status, String message) { this(status, message, null); }
    public ContentsException(int status, String message, String responseBody) {
        super(message); this.status = status; this.responseBody = responseBody;
    }
    public int getStatus() { return status; }
    public String getResponseBody() { return responseBody; }
}
