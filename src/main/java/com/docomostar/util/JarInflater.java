package com.docomostar.util;

import java.io.IOException;
import java.io.InputStream;

/** Reuses the host archive reader while retaining STAR exception descriptors. */
public class JarInflater {
    private final com.nttdocomo.util.JarInflater delegate;
    public JarInflater(InputStream stream) throws JarFormatException, IOException {
        try { delegate = new com.nttdocomo.util.JarInflater(stream); }
        catch (com.nttdocomo.util.JarFormatException failure) { throw JarFormatException.from(failure); }
    }
    public JarInflater(byte[] data) throws JarFormatException {
        try { delegate = new com.nttdocomo.util.JarInflater(data); }
        catch (com.nttdocomo.util.JarFormatException failure) { throw JarFormatException.from(failure); }
    }
    public void close() { delegate.close(); }
    public long getSize(String name) throws JarFormatException {
        try { return delegate.getSize(name); }
        catch (com.nttdocomo.util.JarFormatException failure) { throw JarFormatException.from(failure); }
        catch (java.lang.IllegalStateException failure) { throw closed(failure); }
    }
    public InputStream getInputStream(String name) throws JarFormatException {
        try { return delegate.getInputStream(name); }
        catch (com.nttdocomo.util.JarFormatException failure) { throw JarFormatException.from(failure); }
        catch (java.lang.IllegalStateException failure) { throw closed(failure); }
    }
    private static com.docomostar.lang.IllegalStateException closed(java.lang.IllegalStateException failure) {
        var translated = new com.docomostar.lang.IllegalStateException(failure.getMessage());
        translated.initCause(failure);
        return translated;
    }
}
