package com.docomostar.ui.graphics3d;

import com.docomostar.ui.util3d.Transform;
import java.util.IdentityHashMap;

public class Group extends DrawableObject3D {
    private final IdentityHashMap<com.nttdocomo.ui.graphics3d.Object3D, Object3D> elements = new IdentityHashMap<>();
    public Group() { this(new com.nttdocomo.ui.graphics3d.Group()); }
    Group(com.nttdocomo.ui.graphics3d.Group delegate) { super(delegate); }
    private com.nttdocomo.ui.graphics3d.Group group() { return (com.nttdocomo.ui.graphics3d.Group) delegate; }
    @Override public void dispose() { super.dispose(); elements.clear(); }
    public int getNumElements() { return group().getNumElements(); }
    public Object3D getElement(int index) { return elements.computeIfAbsent(group().getElement(index), Object3D::wrap); }
    public void removeElement(int index) {
        var removed = group().getElement(index);
        group().removeElement(index);
        for (int i = 0; i < group().getNumElements(); i++) {
            if (group().getElement(i) == removed) return;
        }
        elements.remove(removed);
    }
    public void addElement(Object3D object) {
        group().addElement(unwrap(object)); elements.put(unwrap(object), object);
    }
    public void setTransform(Transform transform) { group().setTransform(transform); }
    public void getTransform(Transform transform) { group().getTransform(transform); }
}
