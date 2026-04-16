package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;

/**
 * Defines a component that displays text.
 * The {@code Label} class is one of the UI parts used by the high-level API,
 * and is a component for displaying a string.
 * Only single-line text display is supported.
 * To display multi-line text, use {@code TextBox} in a non-editable state.
 *
 * <p>If alignment is not specified, {@link #CENTER} is used.
 * If the text does not fit in the component size, the part that cannot be
 * displayed is clipped.</p>
 *
 * <p>Since DoJa-3.0 (505i), the label string can be either a normal string or
 * an {@link XString}. These operate exclusively. Setting an {@code XString}
 * replaces the normal string, and setting a normal string replaces the
 * {@code XString}.</p>
 */
public final class Label extends Component {
    /**
     * Specifies that the label string is displayed left-aligned ({@code =0}).
     */
    public static final int LEFT = 0;
    /**
     * Specifies that the label string is displayed centered ({@code =1}).
     */
    public static final int CENTER = 1;
    /**
     * Specifies that the label string is displayed right-aligned ({@code =2}).
     */
    public static final int RIGHT = 2;

    private String text = "";
    private int alignment = CENTER;

    /**
     * Creates a label whose label string is the empty string ({@code ""}).
     * When created, the label is visible.
     * Use {@link Component#setVisible(boolean)} to set it visible or
     * invisible.
     */
    public Label() {
    }

    /**
     * Creates a label whose label string is the string contained in the
     * specified {@code XString}.
     * When created, the label is visible.
     * Use {@link Component#setVisible(boolean)} to set it visible or
     * invisible.
     * The alignment is treated as centered.
     *
     * @param text the {@code XString} label string;
     *             if {@code null}, the empty string ({@code ""}) is set
     */
    public Label(XString text) {
        this(_XStringSupport.valueOrNull(text));
    }

    /**
     * Creates a label with the specified label string.
     * When created, the label is visible.
     * Use {@link Component#setVisible(boolean)} to set it visible or
     * invisible.
     * The alignment is treated as centered.
     *
     * @param text the label string;
     *             if {@code null}, the empty string ({@code ""}) is set
     */
    public Label(String text) {
        this(text, CENTER);
    }

    /**
     * Creates a label with an {@code XString} label string and alignment.
     * When created, the label is visible.
     * Use {@link Component#setVisible(boolean)} to set it visible or
     * invisible.
     *
     * @param text the {@code XString} label string;
     *             if {@code null}, the empty string ({@code ""}) is set
     * @param alignment the alignment
     * @throws IllegalArgumentException if {@code alignment} is invalid
     */
    public Label(XString text, int alignment) {
        this(_XStringSupport.valueOrNull(text), alignment);
    }

    /**
     * Creates a label with the specified label string and alignment.
     * When created, the label is visible.
     * Use {@link Component#setVisible(boolean)} to set it visible or
     * invisible.
     *
     * @param text the label string;
     *             if {@code null}, the empty string ({@code ""}) is set
     * @param alignment the alignment
     * @throws IllegalArgumentException if {@code alignment} is invalid
     */
    public Label(String text, int alignment) {
        this.text = text == null ? "" : text;
        this.alignment = validateAlignment(alignment);
    }

    /**
     * Sets the component label string as an {@code XString}.
     *
     * @param text the label string;
     *             if {@code null}, the empty string ({@code ""}) is set
     */
    public void setText(XString text) {
        setText(_XStringSupport.valueOrNull(text));
    }

    /**
     * Sets the component label string.
     *
     * @param text the label string;
     *             if {@code null}, the empty string ({@code ""}) is set
     */
    public void setText(String text) {
        this.text = text == null ? "" : text;
    }

    /**
     * Sets the alignment of the label string.
     * How it is actually displayed is device-dependent.
     *
     * @param alignment the alignment
     * @throws IllegalArgumentException if {@code alignment} is invalid
     */
    public void setAlignment(int alignment) {
        this.alignment = validateAlignment(alignment);
    }

    /** {@inheritDoc} */
    @Override
    public void setFont(Font font) {
        super.setFont(font);
    }

    private static int validateAlignment(int alignment) {
        if (alignment != LEFT && alignment != CENTER && alignment != RIGHT) {
            throw new IllegalArgumentException("alignment");
        }
        return alignment;
    }
}
