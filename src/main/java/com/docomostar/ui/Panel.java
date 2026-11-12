package com.docomostar.ui;

public class Panel extends Frame {
    private final HostPanel panel = new HostPanel();
    private final java.util.Map<com.nttdocomo.ui.Component, Component> components = new java.util.IdentityHashMap<>();
    public Panel() { hostFrame = panel; }
    public void add(Component component) {
        java.util.Objects.requireNonNull(component, "component");
        try { panel.add(component.delegate); components.put(component.delegate, component); }
        catch (com.nttdocomo.ui.UIException failure) { throw UIException.from(failure); }
    }
    public void setComponentListener(ComponentListener listener) {
        panel.setComponentListener(listener == null ? null :
                (component, type, param) -> listener.componentAction(components.get(component), type, param));
    }
    public void setSoftKeyListener(SoftKeyListener listener) {
        panel.setSoftKeyListener(listener == null ? null : new com.nttdocomo.ui.SoftKeyListener() {
            public void softKeyPressed(int key) { listener.softKeyPressed(key); }
            public void softKeyReleased(int key) { listener.softKeyReleased(key); }
        });
    }
    public void setTitle(String title) { panel.setTitle(title); }
    final class HostPanel extends com.nttdocomo.ui.Panel implements Frame.Host {
        @Override public Frame starFrame() { return Panel.this; }
        Panel owner() { return Panel.this; }
    }
}
