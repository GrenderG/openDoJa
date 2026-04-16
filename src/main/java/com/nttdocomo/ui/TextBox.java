package com.nttdocomo.ui;

import com.nttdocomo.lang.IllegalStateException;
import com.nttdocomo.lang.XString;
import com.nttdocomo.lang._XStringSupport;

/**
 * Defines a text box.
 */
public final class TextBox extends Component implements Interactable {
    /** Indicates that the initial input mode is numeric input mode (=0). */
    public static final int ALPHA = 1;
    /** Indicates that the initial input mode is kana-kanji input mode (=2). */
    public static final int KANA = 2;
    /** Indicates that the initial input mode is numeric input mode (=0). */
    public static final int NUMBER = 0;
    /**
     * Indicates the display mode in which the display string is shown as-is
     * (=0).
     */
    public static final int DISPLAY_ANY = 0;
    /**
     * Indicates the display mode in which the string is hidden for password
     * entry (=1).
     */
    public static final int DISPLAY_PASSWORD = 1;
    /** Indicates that the input-character limit is unlimited (=0). */
    public static final int INPUTSIZE_UNLIMITED = 0;

    private final int columns;
    private final int rows;
    private final int displayMode;
    private String text = "";
    private XString xText;
    private boolean xTextMode;
    private int inputMode;
    private int inputSize = INPUTSIZE_UNLIMITED;
    private boolean editable = true;
    private boolean enabled = true;

    /**
     * Creates a text box with the specified initial XString text, column count,
     * row count, and display mode.
     * When created, it is visible and user-operable.
     * XString text is non-editable by default.
     *
     * @param text the initial XString text, or {@code null}
     * @param columns the number of columns in half-width characters
     * @param rows the number of rows
     * @param displayMode the display mode
     * @throws IllegalArgumentException if {@code columns} or {@code rows} is
     *         negative, or if {@code displayMode} is invalid
     */
    public TextBox(XString text, int columns, int rows, int displayMode) {
        validateGeometry(columns, rows);
        validateDisplayMode(displayMode);
        this.columns = columns;
        this.rows = rows;
        this.displayMode = displayMode;
        this.inputMode = defaultInputMode(displayMode);
        this.text = _XStringSupport.valueOrEmpty(text);
        this.xText = text;
        this.xTextMode = true;
        this.editable = false;
    }

    /**
     * Creates a text box with the specified initial text, column count, row
     * count, and display mode.
     * When created, it is visible and user-operable.
     *
     * @param text the initial text, or {@code null} for the empty string
     * @param columns the number of columns in half-width characters
     * @param rows the number of rows
     * @param displayMode the display mode
     * @throws IllegalArgumentException if {@code columns} or {@code rows} is
     *         negative, or if {@code displayMode} is invalid
     */
    public TextBox(String text, int columns, int rows, int displayMode) {
        validateGeometry(columns, rows);
        validateDisplayMode(displayMode);
        this.columns = columns;
        this.rows = rows;
        this.displayMode = displayMode;
        this.inputMode = defaultInputMode(displayMode);
        this.text = text == null ? "" : text;
    }

    /**
     * Gets the text string.
     *
     * @return the text string, or {@code null} while the current text is held
     *         as an {@link XString}
     */
    public String getText() {
        return xTextMode ? null : text;
    }

    /**
     * Gets the text string as an {@link XString}.
     *
     * @return the XString text, or {@code null} while the current text is a
     *         normal string
     */
    public XString getXText() {
        return xTextMode ? xText : null;
    }

    /**
     * Sets the text string from an {@link XString}.
     * The text can be set even while the component is non-editable.
     * Calling this method automatically makes the text non-editable.
     * The change fires a {@link ComponentListener#TEXT_CHANGED} event only
     * while the component is visible.
     *
     * @param text the XString text, or {@code null}
     * @throws UIException if the specified text exceeds the configured input
     *         size limit
     */
    public void setText(XString text) {
        String value = _XStringSupport.valueOrEmpty(text);
        ensureWithinInputSize(value);
        this.text = value;
        this.xText = text;
        this.xTextMode = true;
        this.editable = false;
        fireTextChangedIfVisible();
    }

    /**
     * Sets the text string.
     * The text can be set even while the component is non-editable.
     * The change fires a {@link ComponentListener#TEXT_CHANGED} event only
     * while the component is visible.
     *
     * @param text the text string, or {@code null} for the empty string
     * @throws UIException if the specified text exceeds the configured input
     *         size limit
     */
    public void setText(String text) {
        String value = text == null ? "" : text;
        ensureWithinInputSize(value);
        this.text = value;
        this.xText = null;
        this.xTextMode = false;
        fireTextChangedIfVisible();
    }

    /**
     * Sets whether the text string can be edited.
     * When the current text is held as an {@link XString}, this method throws
     * {@link IllegalStateException}.
     *
     * @param editable {@code true} to make the text editable, or
     *                 {@code false} otherwise
     * @throws IllegalStateException if the current text is held as an
     *         {@link XString}
     */
    public void setEditable(boolean editable) {
        if (xTextMode) {
            throw new IllegalStateException("XString text cannot be made editable");
        }
        this.editable = editable;
    }

    /**
     * Sets the initial input mode.
     *
     * @param inputMode the initial input mode
     * @throws IllegalArgumentException if {@code inputMode} is invalid
     */
    public void setInputMode(int inputMode) {
        validateInputMode(inputMode);
        this.inputMode = inputMode;
    }

    /**
     * Sets the limit on the number of characters that can be entered.
     * The limit is unlimited by default.
     *
     * @param inputSize the input-size limit, or {@link #INPUTSIZE_UNLIMITED}
     * @throws IllegalArgumentException if {@code inputSize} is negative
     * @throws UIException if the existing text is longer than the requested
     *         limit
     */
    public void setInputSize(int inputSize) {
        if (inputSize < 0) {
            throw new IllegalArgumentException("inputSize");
        }
        if (inputSize != INPUTSIZE_UNLIMITED && currentTextLength() > inputSize) {
            throw new UIException(UIException.ILLEGAL_STATE,
                    "Existing text exceeds the requested input-size limit");
        }
        this.inputSize = inputSize;
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
     * Sets whether the component is visible.
     *
     * @param visible {@code true} to make the component visible, or
     *                {@code false} to make it invisible
     */
    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
    }

    /**
     * Sets the font used to draw the text.
     *
     * @param font the font to use
     */
    @Override
    public void setFont(Font font) {
        super.setFont(font);
    }

    /**
     * Sets the component position.
     *
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    @Override
    public void setLocation(int x, int y) {
        super.setLocation(x, y);
    }

    @Override
    boolean acceptsFocusOn(Panel panel) {
        return enabled && super.acceptsFocusOn(panel);
    }

    private void fireTextChangedIfVisible() {
        Panel panel = ownerPanel();
        if (panel != null && visible()) {
            panel.fireComponentAction(this, ComponentListener.TEXT_CHANGED, 0);
        }
    }

    private int currentTextLength() {
        return text.length();
    }

    private void ensureWithinInputSize(String value) {
        if (inputSize != INPUTSIZE_UNLIMITED && value.length() > inputSize) {
            throw new UIException(UIException.ILLEGAL_STATE,
                    "Text exceeds the configured input-size limit");
        }
    }

    private static void validateGeometry(int columns, int rows) {
        if (columns < 0 || rows < 0) {
            throw new IllegalArgumentException("columns/rows");
        }
    }

    private static void validateDisplayMode(int displayMode) {
        if (displayMode != DISPLAY_ANY && displayMode != DISPLAY_PASSWORD) {
            throw new IllegalArgumentException("displayMode");
        }
    }

    private static void validateInputMode(int inputMode) {
        if (inputMode != NUMBER && inputMode != ALPHA && inputMode != KANA) {
            throw new IllegalArgumentException("inputMode");
        }
    }

    private static int defaultInputMode(int displayMode) {
        return displayMode == DISPLAY_PASSWORD ? NUMBER : KANA;
    }
}
