package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;

public final class TextBox extends Component implements Interactable {
    public static final int ALPHA = 1;
    public static final int KANA = 2;
    public static final int NUMBER = 0;
    public static final int DISPLAY_ANY = 0;
    public static final int DISPLAY_PASSWORD = 1;
    public static final int INPUTSIZE_UNLIMITED = 0;

    private String text;
    private int inputMode;
    private int inputSize;
    private boolean editable = true;
    private boolean enabled = true;

    public TextBox(XString text, int inputMode, int inputSize, int displayMode) {
        this(text == null ? null : text.toString(), inputMode, inputSize, displayMode);
    }

    public TextBox(String text, int inputMode, int inputSize, int displayMode) {
        this.text = text == null ? "" : text;
        this.inputMode = inputMode;
        this.inputSize = inputSize;
    }

    public String getText() {
        return text;
    }

    public XString getXText() {
        return new XString(text);
    }

    public void setText(XString text) {
        setText(text == null ? null : text.toString());
    }

    public void setText(String text) {
        if (inputSize > 0 && inputSize != INPUTSIZE_UNLIMITED && text != null && text.length() > inputSize) {
            this.text = text.substring(0, inputSize);
        } else {
            this.text = text == null ? "" : text;
        }
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
    }

    public void setInputMode(int inputMode) {
        this.inputMode = inputMode;
    }

    public void setInputSize(int inputSize) {
        this.inputSize = inputSize;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void requestFocus() {
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
    }

    @Override
    public void setLocation(int x, int y) {
        super.setLocation(x, y);
    }
}
