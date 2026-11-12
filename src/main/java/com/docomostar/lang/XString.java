package com.docomostar.lang;

/** STAR view of a DoJa native-managed string; stringification never exposes its contents. */
public final class XString extends XObject {
    public XString(String value) {
        this(new com.nttdocomo.lang.XString(value));
    }

    XString(com.nttdocomo.lang.XString value) {
        super(java.util.Objects.requireNonNull(value, "value"));
    }

    com.nttdocomo.lang.XString hostString() {
        return (com.nttdocomo.lang.XString) value();
    }

    public int length() {
        return hostString().length();
    }

    public XString concat(XString other) {
        return new XString(hostString().concat(_XStringSupport.unwrap(other)));
    }
}
