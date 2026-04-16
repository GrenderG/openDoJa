package com.nttdocomo.lang;

/**
 * Internal bridge for APIs that are explicitly documented to consume an
 * {@link XString} as text while preserving {@link XObject#toString()} identity
 * semantics for ordinary Java stringification.
 */
public final class _XStringSupport {
    private _XStringSupport() {
    }

    public static String value(XString text, String name) {
        if (text == null) {
            throw new NullPointerException(name);
        }
        return (String) text.value();
    }

    public static String valueOrNull(XString text) {
        return text == null ? null : (String) text.value();
    }

    public static String valueOrEmpty(XString text) {
        return text == null ? "" : (String) text.value();
    }
}
