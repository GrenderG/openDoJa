package com.nttdocomo.lang;

import java.util.Objects;

public abstract class XObject {
    private final Object value;

    protected XObject(Object value) {
        this.value = value;
    }

    protected final Object value() {
        return value;
    }

    @Override
    public final boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof XObject xObject) {
            return Objects.equals(value, xObject.value);
        }
        return Objects.equals(value, obj);
    }

    @Override
    public final int hashCode() {
        return Objects.hashCode(value);
    }

    @Override
    public final String toString() {
        return String.valueOf(value);
    }
}
