package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;

/**
 * Defines a ticker.
 */
public final class Ticker extends Component {
    private String text = "";

    /**
     * Creates a ticker whose displayed text is the empty string ({@code ""}).
     * When created, it is visible.
     */
    public Ticker() {
    }

    /**
     * Creates a ticker with the specified displayed text.
     * If {@code text} is {@code null}, the empty string is set.
     *
     * @param text the displayed text
     */
    public Ticker(String text) {
        setText(text);
    }

    /**
     * Creates a ticker whose displayed text is taken from the specified
     * {@link XString}.
     * If {@code xText} is {@code null}, the empty string is set.
     *
     * @param xText the displayed {@link XString}
     */
    public Ticker(XString xText) {
        setText(xText);
    }

    /**
     * Sets the displayed text.
     * If {@code text} is {@code null}, the empty string is set.
     *
     * @param text the displayed text
     */
    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    /**
     * Sets the displayed text from an {@link XString}.
     * If {@code xText} is {@code null}, the empty string is set.
     *
     * @param xText the displayed {@link XString}
     */
    public void setText(XString xText) {
        setText(_XStringSupport.valueOrNull(xText));
    }

    /**
     * Sets the font used by the ticker.
     *
     * @param font the font to set
     */
    @Override
    public void setFont(Font font) {
        super.setFont(font);
    }
}
