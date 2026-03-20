package com.nttdocomo.util;

import java.security.NoSuchAlgorithmException;

public class MessageDigest {
    private final java.security.MessageDigest delegate;

    private MessageDigest(java.security.MessageDigest delegate) {
        this.delegate = delegate;
    }

    public static MessageDigest getInstance(String algorithm) {
        try {
            return new MessageDigest(java.security.MessageDigest.getInstance(algorithm));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalArgumentException("Unsupported digest algorithm: " + algorithm, e);
        }
    }

    public void update(byte input) {
        delegate.update(input);
    }

    public void update(byte[] input, int offset, int length) {
        delegate.update(input, offset, length);
    }

    public void update(byte[] input) {
        delegate.update(input);
    }

    public byte[] digest() {
        return delegate.digest();
    }

    public int digest(byte[] buffer, int offset, int length) {
        byte[] result = digest();
        int copyLength = Math.min(length, result.length);
        System.arraycopy(result, 0, buffer, offset, copyLength);
        return copyLength;
    }

    public byte[] digest(byte[] input) {
        return delegate.digest(input);
    }

    public void reset() {
        delegate.reset();
    }

    @Override
    public String toString() {
        return delegate.toString();
    }

    public final int getDigestLength() {
        return delegate.getDigestLength();
    }

    public final String getAlgorithm() {
        return delegate.getAlgorithm();
    }

    public static boolean isEqual(byte[] left, byte[] right) {
        return java.security.MessageDigest.isEqual(left, right);
    }
}
