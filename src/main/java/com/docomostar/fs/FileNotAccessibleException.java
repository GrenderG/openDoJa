package com.docomostar.fs;

import java.io.IOException;

/**
 * Indicates that a file or folder could not be accessed.
 */
public class FileNotAccessibleException extends IOException {
    /** Indicates that the status is undefined (=0). */
    public static final int UNDEFINED = 0;

    /** Indicates that the file does not exist (=1). */
    public static final int NOT_FOUND = 1;

    /** Indicates that a file or folder with the same name already exists (=2). */
    public static final int ALREADY_EXISTS = 2;

    /** Indicates that the file is already in use (=3). */
    public static final int IN_USE = 3;

    /** Indicates that the name violates the naming rules (=4). */
    public static final int ILLEGAL_NAME = 4;

    /** Indicates that access was denied by security settings or a similar reason (=5). */
    public static final int ACCESS_DENIED = 5;

    private final int status;

    /**
     * Creates an exception object without a detail message.
     * The detail message is set to {@code null} and the status is set to
     * {@link #UNDEFINED}.
     */
    public FileNotAccessibleException() {
        this(UNDEFINED, null);
    }

    /**
     * Creates an exception object with a detail message.
     * The status is set to {@link #UNDEFINED}.
     *
     * @param msg the detail message
     */
    public FileNotAccessibleException(String msg) {
        this(UNDEFINED, msg);
    }

    /**
     * Creates an exception object with a status value indicating the cause.
     * The detail message is set to {@code null}.
     *
     * @param status the status value indicating the cause
     */
    public FileNotAccessibleException(int status) {
        this(status, null);
    }

    /**
     * Creates an exception object with a status value indicating the cause and
     * a detail message.
     *
     * @param status the status value indicating the cause
     * @param msg the detail message
     */
    public FileNotAccessibleException(int status, String msg) {
        super(msg);
        this.status = status;
    }

    /**
     * Gets the status value indicating the cause of the exception.
     *
     * @return the status value
     */
    public int getStatus() {
        return status;
    }
}
