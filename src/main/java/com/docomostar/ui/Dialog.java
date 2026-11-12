package com.docomostar.ui;

/** Uses the same modal dialog service as DoJa applications. */
public final class Dialog extends Frame {
    public static final int DIALOG_INFO = 0;
    public static final int DIALOG_WARNING = 1;
    public static final int DIALOG_ERROR = 2;
    public static final int DIALOG_YESNO = 3;
    public static final int DIALOG_YESNOCANCEL = 4;
    public static final int BUTTON_OK = 1;
    public static final int BUTTON_CANCEL = 2;
    public static final int BUTTON_YES = 4;
    public static final int BUTTON_NO = 8;
    private final com.nttdocomo.ui.Dialog delegate;
    public Dialog(int type, String title) {
        delegate = new com.nttdocomo.ui.Dialog(type, title);
        hostFrame = delegate;
        delegate.setFont(Font.getDefaultFont().delegate);
    }
    public void setFont(Font font) { delegate.setFont(java.util.Objects.requireNonNull(font).delegate); }
    public void setText(String text) { delegate.setText(text); }
    public int show() {
        try { return delegate.show(); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
}
