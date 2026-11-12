package com.docomostar.ui;

public class UIException extends com.nttdocomo.ui.UIException {
    public UIException() { super(); }
    public UIException(int status) { super(status); }
    public UIException(int status, String message) { super(status, message); }

    public static UIException from(com.nttdocomo.ui.UIException failure) {
        if (failure instanceof UIException star) return star;
        UIException translated = new UIException(failure.getStatus(), failure.getMessage());
        translated.initCause(failure);
        return translated;
    }
}
