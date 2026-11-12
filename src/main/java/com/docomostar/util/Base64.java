package com.docomostar.util;

public final class Base64 {
    private Base64() { }
    public static byte[] decode(String value) { return com.nttdocomo.util.Base64.decode(value); }
    public static byte[] decode(byte[] value) { return com.nttdocomo.util.Base64.decode(value); }
    public static byte[] decode(byte[] value, int offset, int length) { return com.nttdocomo.util.Base64.decode(value, offset, length); }
    public static String encode(String value) { return com.nttdocomo.util.Base64.encode(value); }
    public static String encode(byte[] value) { return com.nttdocomo.util.Base64.encode(value); }
    public static String encode(byte[] value, int offset, int length) { return com.nttdocomo.util.Base64.encode(value, offset, length); }
}
