package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;

public final class Label extends Component {
    public static final int LEFT = 0;
    public static final int CENTER = 1;
    public static final int RIGHT = 2;

    private String text = "";
    private int alignment = LEFT;

    public Label() {
    }

    public Label(XString text) {
        this(text == null ? null : text.toString());
    }

    public Label(String text) {
        this(text, LEFT);
    }

    public Label(XString text, int alignment) {
        this(text == null ? null : text.toString(), alignment);
    }

    public Label(String text, int alignment) {
        this.text = text == null ? "" : text;
        this.alignment = alignment;
    }

    public void setText(XString text) {
        setText(text == null ? null : text.toString());
    }

    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    public void setAlignment(int alignment) {
        this.alignment = alignment;
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
    }
}
