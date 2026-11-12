package com.docomostar.util;

public class JarFormatException extends Exception {
    public JarFormatException() { }
    public JarFormatException(String message) { super(message); }
    static JarFormatException from(com.nttdocomo.util.JarFormatException failure) {
        JarFormatException translated = new JarFormatException(failure.getMessage());
        translated.initCause(failure);
        return translated;
    }
}
