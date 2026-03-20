package com.nttdocomo.lang;

public final class XString extends XObject {
    public XString(String value) {
        super(value == null ? "" : value);
    }

    public int length() {
        return stringValue().length();
    }

    public XString concat(XString other) {
        return new XString(stringValue() + (other == null ? "" : other.toString()));
    }

    private String stringValue() {
        return (String) value();
    }
}
