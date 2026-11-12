package com.docomostar.fs;

import java.io.IOException;

/** Translates host exceptions so STAR catch clauses retain their meaning. */
public final class _StorageExceptions {
    private _StorageExceptions() { }

    public static IOException translate(IOException failure) {
        IOException translated;
        if (failure instanceof com.nttdocomo.fs.FileNotAccessibleException file) {
            translated = new FileNotAccessibleException(file.getStatus(), file.getMessage());
        } else if (failure instanceof com.nttdocomo.fs.MediaNotFoundException) {
            translated = new MediaNotFoundException(failure.getMessage());
        } else if (failure instanceof com.nttdocomo.fs.FileSystemFullException) {
            translated = new FileSystemFullException(failure.getMessage());
        } else {
            return failure;
        }
        translated.initCause(failure);
        return translated;
    }
}
