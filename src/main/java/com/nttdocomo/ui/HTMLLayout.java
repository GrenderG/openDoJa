package com.nttdocomo.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;

/**
 * Defines a layout manager that performs HTML-like layout.
 */
public class HTMLLayout implements LayoutManager {
    /** Indicates that the specified range is treated as a paragraph ("P"). */
    public static final String P = "P";

    /** Indicates left alignment for the specified range ("LEFT"). */
    public static final String LEFT = "LEFT";

    /** Indicates center alignment for the specified range ("CENTER"). */
    public static final String CENTER = "CENTER";

    /** Indicates right alignment for the specified range ("RIGHT"). */
    public static final String RIGHT = "RIGHT";

    private final Deque<String> stack = new ArrayDeque<>();
    private int pendingBreaks;

    /**
     * Creates a layout manager that performs HTML-like layout.
     */
    public HTMLLayout() {
    }

    /**
     * Specifies that processing equivalent to the HTML {@code <br>} tag should
     * be performed.
     */
    public void br() {
        pendingBreaks++;
    }

    /**
     * Specifies the start of processing for tags such as
     * {@code <center>} ... {@code </center>}.
     * This method must always be used in pairs with {@link #end()}.
     * Uppercase and lowercase letters are not distinguished.
     *
     * @param tag the string indicating the processing
     * @throws NullPointerException if {@code tag} is {@code null}
     * @throws IllegalArgumentException if {@code tag} represents an
     *         unsupported processing
     */
    public void begin(String tag) {
        if (tag == null) {
            throw new NullPointerException("tag");
        }
        String normalized = tag.toUpperCase(Locale.ROOT);
        if (!P.equals(normalized) && !LEFT.equals(normalized)
                && !CENTER.equals(normalized) && !RIGHT.equals(normalized)) {
            throw new IllegalArgumentException("Unsupported HTMLLayout tag: " + tag);
        }
        stack.push(normalized);
        if (P.equals(normalized)) pendingBreaks += 2;
    }

    /**
     * Specifies the end of processing for tags such as
     * {@code <center>} ... {@code </center>}.
     * This method must always be used in pairs with {@link #begin(String)}.
     *
     * @throws UIException if there is no corresponding {@code begin}
     *         ({@link UIException#ILLEGAL_STATE})
     */
    public void end() {
        if (stack.isEmpty()) {
            throw new UIException(UIException.ILLEGAL_STATE, "HTMLLayout.end() without a matching begin()");
        }
        if (P.equals(stack.pop())) pendingBreaks += 2;
    }

    int consumePendingBreaks() {
        int breaks = pendingBreaks;
        pendingBreaks = 0;
        return breaks;
    }

    String currentTag() {
        for (String tag : stack) if (!P.equals(tag)) return tag;
        return LEFT;
    }
}
