package com.docomostar.net;

/** Form decoding uses the same charset and parser as DoJa. */
public final class URLDecoder {
    public URLDecoder() { }
    public static String decode(String value) { return com.nttdocomo.net.URLDecoder.decode(value); }
}
