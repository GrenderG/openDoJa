package com.docomostar.fs;

import java.io.IOException;

/**
 * Indicates that the system detected that the media on which a file is stored
 * does not exist.
 */
public class MediaNotFoundException extends IOException {
    /**
     * Creates an exception object without a detail message.
     */
    public MediaNotFoundException() {
    }

    /**
     * Creates an exception object with a detail message.
     *
     * @param msg the detail message
     */
    public MediaNotFoundException(String msg) {
        super(msg);
    }
}
