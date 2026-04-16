package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;

/**
 * Defines an anchor button.
 */
public final class AnchorButton extends Component implements Interactable {
    private Image image;
    private String text = "";
    private boolean enabled = true;

    /**
     * Creates an empty anchor button.
     * Both the text string and the label image are treated as {@code null}.
     */
    public AnchorButton() {
    }

    /**
     * Creates an anchor button with the specified text string.
     * The label image is treated as {@code null}.
     *
     * @param text the text string
     */
    public AnchorButton(String text) {
        setText(text);
    }

    /**
     * Creates an anchor button with the specified label image.
     * The text string is treated as {@code null}.
     *
     * @param image the label image
     */
    public AnchorButton(Image image) {
        setImage(image);
    }

    /**
     * Creates an anchor button with the specified label image and text string.
     *
     * @param image the label image
     * @param text the text string
     */
    public AnchorButton(Image image, String text) {
        this(image);
        setText(text);
    }

    /**
     * Creates an anchor button whose text string is the string held by the
     * specified {@link XString}.
     * The label image is treated as {@code null}.
     *
     * @param xText the {@link XString} text string
     */
    public AnchorButton(XString xText) {
        setText(xText);
    }

    /**
     * Creates an anchor button with the specified label image and with the text
     * string taken from the specified {@link XString}.
     *
     * @param image the label image
     * @param xText the {@link XString} text string
     */
    public AnchorButton(Image image, XString xText) {
        this(image);
        setText(xText);
    }

    /**
     * Sets the text string of the anchor button.
     *
     * @param text the text string
     */
    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    /**
     * Sets the text string of the anchor button from an {@link XString}.
     *
     * @param xText the {@link XString} text string
     */
    public void setText(XString xText) {
        setText(_XStringSupport.valueOrNull(xText));
    }

    /**
     * Sets the label image of the anchor button.
     *
     * @param image the label image
     */
    public void setImage(Image image) {
        this.image = image;
    }

    /**
     * Sets the component to the enabled state or the disabled state.
     *
     * @param b {@code true} to enable, or {@code false} to disable
     */
    @Override
    public void setEnabled(boolean b) {
        this.enabled = b;
    }

    /**
     * Requests that focus be set to this component.
     */
    @Override
    public void requestFocus() {
        requestFocusFromOwnerPanel();
    }

    /**
     * Sets the font used by the anchor button.
     *
     * @param font the font to set
     */
    @Override
    public void setFont(Font font) {
        super.setFont(font);
    }

    @Override
    boolean acceptsFocusOn(Panel panel) {
        return enabled && super.acceptsFocusOn(panel);
    }
}
