package com.docomostar.ui;

import com.docomostar.lang.XString;
import com.docomostar.lang._XStringSupport;

public final class TextBox extends Component implements Interactable {
    public static final int NUMBER = 0, ALPHA = 1, KANA = 2,
            DISPLAY_ANY = 0, DISPLAY_PASSWORD = 1, INPUTSIZE_UNLIMITED = 0;
    private final com.nttdocomo.ui.TextBox textBox;
    public TextBox(String text, int columns, int rows, int displayMode) {
        this(new com.nttdocomo.ui.TextBox(text, columns, rows, displayMode));
    }
    public TextBox(XString text, int columns, int rows, int displayMode) {
        this(new com.nttdocomo.ui.TextBox(_XStringSupport.unwrap(text), columns, rows, displayMode));
    }
    private TextBox(com.nttdocomo.ui.TextBox host) { super(host); textBox = host; }
    public String getText() { return textBox.getText(); }
    public XString getXText() { return _XStringSupport.wrap(textBox.getXText()); }
    public void setText(String text) { textBox.setText(text); }
    public void setText(XString text) { textBox.setText(_XStringSupport.unwrap(text)); }
    public void setEditable(boolean editable) { textBox.setEditable(editable); }
    public void setInputMode(int mode) { textBox.setInputMode(mode); }
    public void setInputSize(int size) { textBox.setInputSize(size); }
    public void setEnabled(boolean enabled) { textBox.setEnabled(enabled); }
    public void requestFocus() { textBox.requestFocus(); }
}
