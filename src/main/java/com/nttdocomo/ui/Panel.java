package com.nttdocomo.ui;

import java.util.ArrayList;
import java.util.List;

public class Panel extends Frame {
    private final List<Component> components = new ArrayList<>();
    private FocusManager focusManager;
    private ComponentListener componentListener;
    private KeyListener keyListener;
    private LayoutManager layoutManager;
    private SoftKeyListener softKeyListener;
    private String title;

    public Panel() {
    }

    public void add(Component component) {
        if (component != null) {
            components.add(component);
        }
    }

    public FocusManager getFocusManager() {
        return focusManager;
    }

    public void setFocusManager(FocusManager focusManager) {
        this.focusManager = focusManager;
    }

    @Override
    public void setBackground(int color) {
        super.setBackground(color);
    }

    public void setComponentListener(ComponentListener componentListener) {
        this.componentListener = componentListener;
    }

    public void setKeyListener(KeyListener keyListener) {
        this.keyListener = keyListener;
    }

    public void setLayoutManager(LayoutManager layoutManager) {
        this.layoutManager = layoutManager;
    }

    @Override
    public void setSoftLabelVisible(boolean visible) {
        super.setSoftLabelVisible(visible);
    }

    public void setSoftKeyListener(SoftKeyListener softKeyListener) {
        this.softKeyListener = softKeyListener;
    }

    public void setTitle(String title) {
        this.title = title;
    }
}
