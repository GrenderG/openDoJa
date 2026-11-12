package com.docomostar.lang;

/** STAR native-managed objects retain the shared DoJa identity semantics. */
public abstract class XObject extends com.nttdocomo.lang.XObject {
    protected XObject(Object value) {
        super(value);
    }
}
