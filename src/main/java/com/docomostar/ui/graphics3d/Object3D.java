package com.docomostar.ui.graphics3d;

import java.io.IOException;
import java.io.InputStream;

/** STAR object hierarchy backed by the existing decoded DoJa objects. */
public abstract class Object3D {
    public static final int TYPE_NONE = 0;
    public static final int TYPE_ACTION_TABLE = 1;
    public static final int TYPE_FIGURE = 2;
    public static final int TYPE_TEXTURE = 3;
    public static final int TYPE_FOG = 4;
    public static final int TYPE_LIGHT = 5;
    public static final int TYPE_PRIMITIVE = 6;
    public static final int TYPE_GROUP = 7;
    public static final int TYPE_GROUP_MESH = 8;
    protected final com.nttdocomo.ui.graphics3d.Object3D delegate;
    Object3D(com.nttdocomo.ui.graphics3d.Object3D delegate) { this.delegate = java.util.Objects.requireNonNull(delegate); }
    public static Object3D createInstance(InputStream stream) throws IOException {
        return wrap(com.nttdocomo.ui.graphics3d.Object3D.createInstance(stream));
    }
    public static Object3D createInstance(byte[] data) {
        return wrap(com.nttdocomo.ui.graphics3d.Object3D.createInstance(data));
    }
    static Object3D wrap(com.nttdocomo.ui.graphics3d.Object3D object) {
        if (object == null) return null;
        if (object instanceof com.nttdocomo.ui.graphics3d.Figure figure) return new Figure(figure);
        if (object instanceof com.nttdocomo.ui.graphics3d.ActionTable action) return new ActionTable(action);
        if (object instanceof com.nttdocomo.ui.graphics3d.Texture texture) return new Texture(texture);
        if (object instanceof com.nttdocomo.ui.graphics3d.Primitive primitive) return new Primitive(primitive);
        if (object instanceof com.nttdocomo.ui.graphics3d.Group group) return new Group(group);
        if (object instanceof com.nttdocomo.ui.graphics3d.Light light) return new Light(light);
        if (object instanceof com.nttdocomo.ui.graphics3d.Fog fog) return new Fog(fog);
        throw new IllegalArgumentException("Unsupported host object: " + object.getClass().getName());
    }
    /** Host integration: renderer calls consume the original object and its live buffers. */
    public static com.nttdocomo.ui.graphics3d.Object3D unwrap(Object3D object) {
        return object == null ? null : object.delegate;
    }
    public void dispose() { delegate.dispose(); }
    public int getType() { return delegate.getType(); }
    public void setTime(int time) { delegate.setTime(time); }
    public int getTime() { return delegate.getTime(); }
}
