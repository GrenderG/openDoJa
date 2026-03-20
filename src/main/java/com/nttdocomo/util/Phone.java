package com.nttdocomo.util;

import com.nttdocomo.lang.XString;
import opendoja.host.DoJaRuntime;

public final class Phone {
    public static final String TERMINAL_ID = "terminal-id";
    public static final String USER_ID = "user-id";
    public static final String TEL_AV = "tel-av:";
    public static final String UIM_VERSION = "uim-version";

    private Phone() {
    }

    public static void call(String destination) {
        // Desktop host does not place real calls.
    }

    public static void call(XString destination) {
        call(destination == null ? null : destination.toString());
    }

    public static void call(String destination, XString subAddress) {
        call(destination);
    }

    public static String getProperty(String key) {
        DoJaRuntime runtime = DoJaRuntime.current();
        if (TERMINAL_ID.equals(key)) {
            return "opendoja-desktop";
        }
        if (USER_ID.equals(key)) {
            return System.getProperty("user.name", "desktop-user");
        }
        if (UIM_VERSION.equals(key)) {
            return "desktop";
        }
        return runtime == null ? null : runtime.parameters().get(key);
    }
}
