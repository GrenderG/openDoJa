package com.nttdocomo.ui;

/**
 * Defines a component.
 * This abstract class represents a high-level API component.
 */
public abstract class Component {
    private int x;
    private int y;
    private int width;
    private int height;
    private int background = Graphics.getColorOfName(Graphics.WHITE);
    private int foreground = Graphics.getColorOfName(Graphics.BLACK);
    private Font font = Font.getDefaultFont();
    private boolean visible = true;
    private Panel ownerPanel;
    private boolean focused;
    private boolean explicitSize;

    /**
     * Applications cannot directly create instances of this class.
     * This constructor makes the component visible.
     * Use {@link #setVisible(boolean)} to change the visible state.
     */
    public Component() {
    }

    /**
     * Gets the height of the component.
     *
     * @return the component height
     */
    public final int getHeight() {
        return height;
    }

    /**
     * Gets the width of the component.
     *
     * @return the component width
     */
    public final int getWidth() {
        return width;
    }

    /**
     * Gets the X coordinate of the component.
     *
     * @return the X coordinate
     */
    public int getX() {
        return x;
    }

    /**
     * Gets the Y coordinate of the component.
     *
     * @return the Y coordinate
     */
    public int getY() {
        return y;
    }

    /**
     * Sets the background color of the component.
     * The result is reflected immediately if the component is visible.
     *
     * @param color the background color
     */
    public void setBackground(int color) {
        this.background = color;
        hostChanged();
    }

    /**
     * Sets the foreground color of the component.
     * The result is reflected immediately if the component is visible.
     *
     * @param color the foreground color
     */
    public void setForeground(int color) {
        this.foreground = color;
        hostChanged();
    }

    /**
     * Sets the component position.
     *
     * @param x the X coordinate
     * @param y the Y coordinate
     */
    public void setLocation(int x, int y) {
        this.x = x;
        this.y = y;
        hostChanged();
    }

    /**
     * Sets the size of the component.
     *
     * @param width the width
     * @param height the height
     */
    public void setSize(int width, int height) {
        this.width = width;
        this.height = height;
        explicitSize = true;
        hostChanged();
    }

    /**
     * Sets the font used to draw text in this component.
     *
     * @param font the font to use
     */
    public void setFont(Font font) {
        if (font != null) {
            this.font = font;
            hostChanged();
        }
    }

    /**
     * Sets whether the component is visible.
     * The component is visible by default.
     * When a focused component becomes invisible, it releases the acquired
     * focus and no component remains focused.
     *
     * @param visible {@code true} to make the component visible, or
     *                {@code false} to make it invisible
     */
    public void setVisible(boolean visible) {
        this.visible = visible;
        if (!visible && ownerPanel != null) {
            ownerPanel.componentBecameUnavailable(this);
        }
        hostChanged();
    }

    int backgroundColor() {
        return background;
    }

    int foregroundColor() {
        return foreground;
    }

    Font font() {
        return font;
    }

    boolean visible() {
        return visible;
    }

    final void attachToPanel(Panel panel) {
        this.ownerPanel = panel;
    }

    final Panel ownerPanel() {
        return ownerPanel;
    }

    final void setFocused(boolean focused) {
        this.focused = focused;
    }

    final void hostChanged() {
        if (ownerPanel != null) ownerPanel.invalidateForHost();
    }

    final boolean hostExplicitSize() { return explicitSize; }

    final void hostBounds(int x, int y, int width, int height) {
        this.x = x; this.y = y; this.width = width; this.height = height;
    }

    final boolean focused() {
        return focused;
    }

    boolean acceptsFocusOn(Panel panel) {
        if (ownerPanel != panel || !visible) {
            return false;
        }
        int start = getX();
        int end = getX() + Math.max(getWidth(), 1);
        return end > 0 && start < panel.getWidth();
    }

    final void requestFocusFromOwnerPanel() {
        Panel panel = ownerPanel;
        if (panel != null) {
            panel.requestFocus(this);
        }
    }
}
