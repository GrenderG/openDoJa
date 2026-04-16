package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;

/**
 * Defines a button.
 */
public final class Button extends Component implements Interactable {
    private String label = "";
    private boolean enabled = true;

    /**
     * Creates a button whose label string is the empty string ({@code ""}).
     * When created, it is visible and user-operable.
     */
    public Button() {
    }

    /**
     * Creates a button with the specified label string.
     * When created, it is visible and user-operable.
     *
     * @param label the label string, or {@code null} for the empty string
     */
    public Button(String label) {
        this.label = label == null ? "" : label;
    }

    /**
     * Creates a button whose label string comes from the specified
     * {@link XString}.
     * When created, it is visible and user-operable.
     *
     * @param label the {@link XString} label string, or {@code null} for the
     *              empty string
     */
    public Button(XString label) {
        this(_XStringSupport.valueOrNull(label));
    }

    /**
     * Sets the label string of the button.
     *
     * @param label the label string, or {@code null} for the empty string
     */
    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }

    /**
     * Sets the label string of the button from an {@link XString}.
     *
     * @param label the {@link XString} label string, or {@code null} for the
     *              empty string
     */
    public void setLabel(XString label) {
        setLabel(_XStringSupport.valueOrNull(label));
    }

    /**
     * Sets the component to the enabled state or the disabled state.
     *
     * @param enabled {@code true} to enable the component, or {@code false} to
     *                disable it
     */
    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Requests that focus be set to the component.
     * Requests issued before the component is added to a panel are ignored.
     * Focus is not set while the component is invisible, disabled, or placed
     * entirely outside the panel horizontally.
     */
    @Override
    public void requestFocus() {
        requestFocusFromOwnerPanel();
    }

    /**
     * Sets the font used to draw the label string of the button.
     *
     * @param font the font to use
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
