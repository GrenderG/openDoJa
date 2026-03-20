package com.nttdocomo.net;

import java.nio.charset.StandardCharsets;

public final class URLEncoder {
    private URLEncoder() {
    }

    public static String encode(String value) {
        if (value == null) {
            return null;
        }
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
