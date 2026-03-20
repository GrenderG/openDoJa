package com.nttdocomo.util;

import java.nio.charset.StandardCharsets;

public final class Base64 {
    private Base64() {
    }

    public static byte[] decode(String value) {
        return java.util.Base64.getDecoder().decode(value);
    }

    public static byte[] decode(byte[] value) {
        return java.util.Base64.getDecoder().decode(value);
    }

    public static byte[] decode(byte[] value, int offset, int length) {
        byte[] slice = new byte[length];
        System.arraycopy(value, offset, slice, 0, length);
        return decode(slice);
    }

    public static String encode(String value) {
        return encode(value.getBytes(StandardCharsets.UTF_8));
    }

    public static String encode(byte[] value) {
        return java.util.Base64.getEncoder().encodeToString(value);
    }

    public static String encode(byte[] value, int offset, int length) {
        byte[] slice = new byte[length];
        System.arraycopy(value, offset, slice, 0, length);
        return encode(slice);
    }
}
