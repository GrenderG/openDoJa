package com.nttdocomo.lang;

public class IterationAbortedException extends Exception {
    private final int abortedIndex;

    public IterationAbortedException(int abortedIndex, Throwable cause) {
        super(cause);
        this.abortedIndex = abortedIndex;
    }

    public IterationAbortedException(int abortedIndex, Throwable cause, String message) {
        super(message, cause);
        this.abortedIndex = abortedIndex;
    }

    public int getAbortedIndex() {
        return abortedIndex;
    }
}
