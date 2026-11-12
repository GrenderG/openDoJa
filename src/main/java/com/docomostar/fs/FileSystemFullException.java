package com.docomostar.fs;

import java.io.IOException;

/**
 * Indicates that writing failed because there was no free space in the folder
 * or file system that stores the file.
 */
public class FileSystemFullException extends IOException {
    /**
     * Creates an exception object without a detail message.
     */
    public FileSystemFullException() {
    }

    /**
     * Creates an exception object with a detail message.
     *
     * @param msg the detail message
     */
    public FileSystemFullException(String msg) {
        super(msg);
    }
}
