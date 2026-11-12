package com.docomostar.system;

/** STAR status and cause for failures reported by the shared managed-data store. */
public class StoreException extends Exception {
    public static final int UNDEFINED = 0;
    public static final int STORE_FULL = 1;
    public static final int NOT_FOUND = 2;
    public static final int USER_ABORT = 3;
    private final int status;

    public StoreException() { this(UNDEFINED); }
    public StoreException(int status) { this(status, null); }
    public StoreException(int status, String message) { super(message); this.status = status; }
    public int getStatus() { return status; }

    static StoreException from(com.nttdocomo.system.StoreException failure) {
        StoreException translated = new StoreException(failure.getStatus(), failure.getMessage());
        translated.initCause(failure);
        return translated;
    }
}
