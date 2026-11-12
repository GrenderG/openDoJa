package com.docomostar.media;

import java.io.InputStream;

/** Encoded bytes and decoded media produced by the shared image encoder. */
public class EncodedImage {
    private final com.nttdocomo.ui.EncodedImage delegate;
    private MediaImage image;

    public EncodedImage(com.nttdocomo.ui.EncodedImage delegate) {
        this.delegate = java.util.Objects.requireNonNull(delegate, "delegate");
    }
    public int size() { return delegate.size(); }
    public InputStream getInputStream() { return delegate.getInputStream(); }
    public synchronized MediaImage getImage() {
        if (image == null) image = MediaManager.wrapImage(delegate.getImage());
        return image;
    }
}
