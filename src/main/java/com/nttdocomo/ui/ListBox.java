package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Defines a list box.
 */
public final class ListBox extends Component implements Interactable {
    /** A list in which only one item can be selected (=0). */
    public static final int SINGLE_SELECT = 0;
    /** A radio button list in which one item is selected exclusively (=1). */
    public static final int RADIO_BUTTON = 1;
    /** A check-box list in which each item can be selected or deselected (=2). */
    public static final int CHECK_BOX = 2;
    /**
     * A numbered list in which items can be selected directly by the key that
     * corresponds to the item number (=3).
     */
    public static final int NUMBERED_LIST = 3;
    /** A list in which multiple items can be selected (=4). */
    public static final int MULTIPLE_SELECT = 4;
    /** An option menu (=5). */
    public static final int CHOICE = 5;

    private final List<Item> items = new ArrayList<>();
    private final TreeSet<Integer> selected = new TreeSet<>();
    private final int type;
    private final int rows;
    private boolean enabled = true;

    /**
     * Creates an empty list box with the specified type.
     * When created, it is visible and user-operable.
     *
     * @param type the list-box type
     * @throws IllegalArgumentException if {@code type} is invalid
     */
    public ListBox(int type) {
        validateType(type);
        this.type = type;
        this.rows = 1;
    }

    /**
     * Creates an empty list box with the specified type and visible row count.
     * When created, it is visible and user-operable.
     *
     * @param type the list-box type
     * @param visibleRows the number of rows to display
     * @throws IllegalArgumentException if {@code type} is invalid or
     *         {@code visibleRows} is negative
     */
    public ListBox(int type, int visibleRows) {
        validateType(type);
        if (visibleRows < 0) {
            throw new IllegalArgumentException("visibleRows");
        }
        this.type = type;
        this.rows = type == CHOICE ? 1 : Math.max(1, visibleRows);
    }

    /**
     * Appends an XString item to the end of the list.
     *
     * @param item the item to append
     * @throws NullPointerException if {@code item} is {@code null}
     */
    public void append(XString item) {
        if (item == null) {
            throw new NullPointerException("item");
        }
        items.add(Item.ofXString(item));
        hostChanged();
    }

    /**
     * Appends an item to the end of the list.
     *
     * @param item the item to append
     * @throws NullPointerException if {@code item} is {@code null}
     */
    public void append(String item) {
        if (item == null) {
            throw new NullPointerException("item");
        }
        items.add(Item.ofString(item));
        hostChanged();
    }

    /**
     * Replaces all list items with the specified string array.
     *
     * @param items the items to set
     * @throws NullPointerException if {@code items} or one of its elements is
     *         {@code null}
     */
    public void setItems(String[] items) {
        if (items == null) {
            throw new NullPointerException("items");
        }
        this.items.clear();
        selected.clear();
        hostChanged();
        for (String item : items) {
            append(item);
        }
    }

    /**
     * Replaces all list items with the specified XString array.
     *
     * @param items the items to set
     * @throws NullPointerException if {@code items} or one of its elements is
     *         {@code null}
     */
    public void setItems(XString[] items) {
        if (items == null) {
            throw new NullPointerException("items");
        }
        this.items.clear();
        selected.clear();
        hostChanged();
        for (XString item : items) {
            append(item);
        }
    }

    /**
     * Deselects the item at the specified position.
     *
     * @param index the item position to deselect
     * @throws UIException if this list box is of type {@link #CHOICE}
     * @throws ArrayIndexOutOfBoundsException if {@code index} is out of range
     */
    public void deselect(int index) {
        requireIndex(index);
        if (type == CHOICE) {
            throw new UIException(UIException.ILLEGAL_STATE,
                    "CHOICE list boxes do not support deselect(int)");
        }
        if (selected.remove(index)) {
            fireSelectionChangedIfVisible();
        }
    }

    /**
     * Gets the item at the specified position.
     *
     * @param index the item position to obtain
     * @return the string item, or {@code null} if the item was set as an
     *         {@link XString}
     * @throws ArrayIndexOutOfBoundsException if {@code index} is out of range
     */
    public String getItem(int index) {
        return requireItem(index).text();
    }

    /**
     * Gets the XString item at the specified position.
     *
     * @param index the item position to obtain
     * @return the XString item, or {@code null} if the item was set as a normal
     *         string
     * @throws ArrayIndexOutOfBoundsException if {@code index} is out of range
     */
    public XString getXItem(int index) {
        return requireItem(index).xText();
    }

    /**
     * Gets the number of items in the list.
     *
     * @return the number of items
     */
    public int getItemCount() {
        return items.size();
    }

    /**
     * Gets the position of the selected item.
     * When multiple items are selected, the smallest selected index is
     * returned.
     *
     * @return the selected item position, or {@code -1} if nothing is selected
     */
    public int getSelectedIndex() {
        return selected.isEmpty() ? -1 : selected.first();
    }

    /**
     * Tests whether the item at the specified position is selected.
     *
     * @param index the item position to inspect
     * @return {@code true} if the item is selected, or {@code false} otherwise
     * @throws ArrayIndexOutOfBoundsException if {@code index} is out of range
     */
    public boolean isIndexSelected(int index) {
        requireIndex(index);
        return selected.contains(index);
    }

    /**
     * Removes all items from the list.
     */
    public void removeAll() {
        items.clear();
        selected.clear();
        hostChanged();
    }

    /**
     * Selects the item at the specified position.
     * Single-selection list types keep only the specified selection.
     *
     * @param index the item position to select
     * @throws ArrayIndexOutOfBoundsException if {@code index} is out of range
     */
    public void select(int index) {
        requireIndex(index);
        boolean changed;
        if (type == CHECK_BOX || type == MULTIPLE_SELECT) {
            changed = selected.add(index);
        } else {
            changed = selected.size() != 1 || !selected.contains(index);
            selected.clear();
            selected.add(index);
        }
        if (changed) {
            fireSelectionChangedIfVisible();
        }
    }

    /**
     * Sets the font used to draw the list items.
     *
     * @param font the font to use
     */
    @Override
    public void setFont(Font font) {
        super.setFont(font);
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
        if (!enabled && ownerPanel() != null) ownerPanel().componentBecameUnavailable(this);
        hostChanged();
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

    private void fireSelectionChangedIfVisible() {
        hostChanged();
        Panel panel = ownerPanel();
        if (panel != null && visible()) {
            panel.fireComponentAction(this, ComponentListener.SELECTION_CHANGED, 0);
        }
    }

    private void requireIndex(int index) {
        if (index < 0 || index >= items.size()) {
            throw new ArrayIndexOutOfBoundsException(index);
        }
    }

    private Item requireItem(int index) {
        requireIndex(index);
        return items.get(index);
    }

    private static void validateType(int type) {
        if (type < SINGLE_SELECT || type > CHOICE) {
            throw new IllegalArgumentException("type");
        }
    }

    private record Item(String text, XString xText) {
        private static Item ofString(String value) {
            return new Item(value, null);
        }

        private static Item ofXString(XString value) {
            return new Item(null, value);
        }
    }

    int hostRows() { return rows; }
    int hostType() { return type; }
    boolean hostEnabled() { return enabled; }
}
