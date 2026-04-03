package com.nttdocomo.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Defines a display surface for the high-level API.
 * A panel is the parent object used to place components.
 * The title is displayed at the upper end of the panel and does not scroll
 * together with the component area.
 */
public class Panel extends Frame {
    private static final FocusManager SYSTEM_FOCUS_MANAGER = new SystemFocusManager();

    private final List<Component> components = new ArrayList<>();
    private FocusManager focusManager = SYSTEM_FOCUS_MANAGER;
    private ComponentListener componentListener;
    private Component focusedComponent;
    private KeyListener keyListener;
    private LayoutManager layoutManager;
    private SoftKeyListener softKeyListener;
    private String title;

    /**
     * Creates an empty panel.
     */
    public Panel() {
    }

    /**
     * Adds a component to the panel.
     *
     * @param component the component to add
     * @throws NullPointerException if {@code component} is {@code null}
     * @throws UIException if the component has already been added to a panel
     */
    public void add(Component component) {
        if (component == null) {
            throw new NullPointerException("component");
        }
        if (component.ownerPanel() != null) {
            throw new UIException(UIException.ILLEGAL_STATE,
                    "Component is already attached to a panel");
        }
        components.add(component);
        component.attachToPanel(this);
    }

    /**
     * Gets the focus-management object that controls focus movement among
     * components on this panel.
     *
     * @return the focus-management object
     */
    public FocusManager getFocusManager() {
        return focusManager;
    }

    /**
     * Sets the focus-management object that controls focus movement among
     * components on this panel.
     * By default, a terminal-dependent system focus manager is set.
     *
     * @param focusManager the focus-management object
     * @throws NullPointerException if {@code focusManager} is {@code null}
     * @throws IllegalArgumentException if the specified object is not a
     *         system-provided focus manager
     */
    public void setFocusManager(FocusManager focusManager) {
        if (focusManager == null) {
            throw new NullPointerException("focusManager");
        }
        if (!(focusManager instanceof SystemFocusManager)) {
            throw new IllegalArgumentException("Only system-provided focus managers are accepted");
        }
        this.focusManager = focusManager;
    }

    /**
     * Sets the background color.
     * The background color changes immediately when this method is called.
     *
     * @param color the color value that represents the background color
     */
    @Override
    public void setBackground(int color) {
        super.setBackground(color);
    }

    /**
     * Registers a component listener.
     * Only one listener can be registered with a panel.
     * Passing {@code null} unregisters the listener.
     *
     * @param componentListener the listener object to register
     */
    public void setComponentListener(ComponentListener componentListener) {
        this.componentListener = componentListener;
    }

    /**
     * Registers a key listener.
     * Only one listener can be registered with a panel.
     * Passing {@code null} unregisters the listener.
     *
     * @param keyListener the listener object to register
     */
    public void setKeyListener(KeyListener keyListener) {
        this.keyListener = keyListener;
    }

    /**
     * Sets the layout-management object that controls component placement on
     * the panel.
     * Passing {@code null} disables layout management.
     *
     * @param layoutManager the layout-management object
     */
    public void setLayoutManager(LayoutManager layoutManager) {
        this.layoutManager = layoutManager;
    }

    /**
     * Sets whether the soft labels are visible.
     *
     * @param visible {@code true} to show the soft labels, or {@code false} to
     *                hide them
     */
    @Override
    public void setSoftLabelVisible(boolean visible) {
        super.setSoftLabelVisible(visible);
    }

    /**
     * Registers a soft-key listener.
     * Only one listener can be registered with a panel.
     * Passing {@code null} unregisters the listener.
     *
     * @param softKeyListener the listener object to register
     */
    public void setSoftKeyListener(SoftKeyListener softKeyListener) {
        this.softKeyListener = softKeyListener;
    }

    /**
     * Performs process Soft Key Event.
     */
    @Override
    public void processSoftKeyEvent(int type, int key) {
        if (softKeyListener == null) {
            return;
        }
        if (type == Display.KEY_PRESSED_EVENT) {
            softKeyListener.softKeyPressed(key);
        } else if (type == Display.KEY_RELEASED_EVENT) {
            softKeyListener.softKeyReleased(key);
        }
    }

    /**
     * Sets the frame title string.
     *
     * @param title the title string
     */
    public void setTitle(String title) {
        this.title = title;
    }

    final void requestFocus(Component component) {
        if (component == null || !component.acceptsFocusOn(this)) {
            return;
        }
        if (focusedComponent == component) {
            return;
        }
        if (focusedComponent != null) {
            focusedComponent.setFocused(false);
        }
        focusedComponent = component;
        component.setFocused(true);
    }

    final void componentBecameUnavailable(Component component) {
        if (focusedComponent == component) {
            focusedComponent.setFocused(false);
            focusedComponent = null;
        }
    }

    final void fireComponentAction(Component component, int type, int param) {
        if (componentListener != null) {
            componentListener.componentAction(component, type, param);
        }
    }

    private static final class SystemFocusManager implements FocusManager {
    }
}
