package com.docomostar.opt.ui.eruption;

/** Float vector using the shared DoJa vector operations. */
public final class Vector3D {
    final com.nttdocomo.ui.ogl.math.Vector3f value;
    public Vector3D() { this(0, 0, 0); }
    public Vector3D(float x, float y, float z) { value = new com.nttdocomo.ui.ogl.math.Vector3f(x, y, z); }
    public float getX() { return value.x; }
    public float getY() { return value.y; }
    public float getZ() { return value.z; }
    public void set(float x, float y, float z) { value.x = x; value.y = y; value.z = z; }
    public void copy(Vector3D source) { set(source.getX(), source.getY(), source.getZ()); }
    public void dispose() { }
}
