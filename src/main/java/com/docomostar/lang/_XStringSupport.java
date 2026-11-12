package com.docomostar.lang;

/** Internal adapters preserve native-managed strings without converting them to Java text. */
public final class _XStringSupport {
    private _XStringSupport() { }

    public static XString wrap(com.nttdocomo.lang.XString text) {
        return text == null ? null : new XString(text);
    }

    public static XString[] wrap(com.nttdocomo.lang.XString[] texts) {
        if (texts == null) return null;
        XString[] result = new XString[texts.length];
        for (int i = 0; i < texts.length; i++) result[i] = wrap(texts[i]);
        return result;
    }

    public static com.nttdocomo.lang.XString unwrap(XString text) {
        return text == null ? null : text.hostString();
    }
}
