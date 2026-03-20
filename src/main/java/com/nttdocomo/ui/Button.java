package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;

public final class Button extends Component implements Interactable {
    private String label = "";
    private boolean enabled = true;

    public Button() {
    }

    public Button(String label) {
        this.label = label == null ? "" : label;
    }

    public Button(XString label) {
        this(label == null ? null : label.toString());
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }

    public void setLabel(XString label) {
        setLabel(label == null ? null : label.toString());
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void requestFocus() {
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
    }
}
