package com.nttdocomo.ui;

import com.nttdocomo.lang.XString;

import java.util.ArrayList;
import java.util.List;

public final class ListBox extends Component implements Interactable {
    public static final int SINGLE_SELECT = 0;
    public static final int RADIO_BUTTON = 1;
    public static final int CHECK_BOX = 2;
    public static final int NUMBERED_LIST = 3;
    public static final int MULTIPLE_SELECT = 4;
    public static final int CHOICE = 5;

    private final List<String> items = new ArrayList<>();
    private final List<Integer> selected = new ArrayList<>();
    private boolean enabled = true;

    public ListBox(int type) {
    }

    public ListBox(int type, int visibleRows) {
    }

    public void append(XString item) {
        append(item == null ? null : item.toString());
    }

    public void append(String item) {
        items.add(item == null ? "" : item);
    }

    public void setItems(String[] items) {
        this.items.clear();
        if (items != null) {
            for (String item : items) {
                append(item);
            }
        }
    }

    public void setItems(XString[] items) {
        this.items.clear();
        if (items != null) {
            for (XString item : items) {
                append(item);
            }
        }
    }

    public void deselect(int index) {
        selected.remove(Integer.valueOf(index));
    }

    public String getItem(int index) {
        return items.get(index);
    }

    public XString getXItem(int index) {
        return new XString(getItem(index));
    }

    public int getItemCount() {
        return items.size();
    }

    public int getSelectedIndex() {
        return selected.isEmpty() ? -1 : selected.get(0);
    }

    public boolean isIndexSelected(int index) {
        return selected.contains(index);
    }

    public void removeAll() {
        items.clear();
        selected.clear();
    }

    public void select(int index) {
        if (!selected.contains(index)) {
            selected.add(index);
        }
    }

    @Override
    public void setFont(Font font) {
        super.setFont(font);
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    public void requestFocus() {
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
    }

    @Override
    public void setLocation(int x, int y) {
        super.setLocation(x, y);
    }
}
