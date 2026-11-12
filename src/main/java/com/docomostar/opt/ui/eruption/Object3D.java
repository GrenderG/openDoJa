package com.docomostar.opt.ui.eruption;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Java-owned Eruption object identity and reference graph. */
public abstract class Object3D {
    public static final int MCE_CLASS_TYPE_OBJECT3D = 1, MCE_CLASS_TYPE_GRAPHICS3D = 2,
            MCE_CLASS_TYPE_VERTEXBUFFER = 3, MCE_CLASS_TYPE_INDEXBUFFER = 4,
            MCE_CLASS_TYPE_TRANSFORMTREE = 5, MCE_CLASS_TYPE_BONEDEFORMER = 7,
            MCE_CLASS_TYPE_NODEDEFORMER = 8, MCE_CLASS_TYPE_MORPHDEFORMER = 9,
            MCE_CLASS_TYPE_FIGURE = 10, MCE_CLASS_TYPE_TEXTURE = 11,
            MCE_CLASS_TYPE_TEXTURETABLE = 12, MCE_CLASS_TYPE_ACTION = 13,
            MCE_CLASS_TYPE_ACTIONTABLE = 14, MCE_CLASS_TYPE_APPEARANCE = 15,
            MCE_CLASS_TYPE_CAMERA = 16, MCE_CLASS_TYPE_LIGHT = 17,
            MCE_CLASS_TYPE_ACTIONCONTROLLER = 18, MCE_CLASS_TYPE_BONE = 19;
    private final int classType;
    private int userId = -1;
    private boolean disposed, renderEnable = true;

    public Object3D() { this(MCE_CLASS_TYPE_OBJECT3D); }
    Object3D(int type) { classType = type; }
    public final int getClassType() { return classType; }
    public final int getUserId() { return userId; }
    public final void setUserId(int id) { requireLive(); userId = id; }
    public final void setRenderEnable(boolean enabled) { requireLive(); renderEnable = enabled; }
    public final boolean isRenderEnable() { return renderEnable; }
    public void dispose() { disposed = true; }
    public boolean isDisposed() { return disposed; }
    @Override public boolean equals(Object other) { return this == other; }
    @Override public int hashCode() { return System.identityHashCode(this); }
    final void requireLive() { if (disposed) throw new IllegalStateException("Disposed Eruption object"); }
    List<? extends Object3D> references() { return List.of(); }

    public final Object3D findObject3D(int type, int id) {
        var found = find(type, id);
        return found.isEmpty() ? null : found.getFirst();
    }
    public final int findObject3D(int type, int id, Object3D[] result) {
        var found = find(type, id);
        if (result != null) {
            if (result.length < found.size()) throw new IllegalArgumentException("Object array too small");
            for (int i = 0; i < found.size(); i++) result[i] = found.get(i);
        }
        return found.size();
    }
    private List<Object3D> find(int type, int id) {
        requireLive();
        List<Object3D> result = new ArrayList<>();
        collect(this, type, id, Collections.newSetFromMap(new IdentityHashMap<>()), result);
        return result;
    }
    private static void collect(Object3D object, int type, int id, Set<Object3D> seen, List<Object3D> result) {
        if (object == null || !seen.add(object)) return;
        if ((type == MCE_CLASS_TYPE_OBJECT3D || type == object.classType) && (id == -1 || id == object.userId))
            result.add(object);
        for (Object3D reference : object.references()) collect(reference, type, id, seen, result);
    }
}
