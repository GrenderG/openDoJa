package com.docomostar.ui.util3d;

/** Float vectors use the shared DoJa math and mutable components. */
public class Vector3D extends com.nttdocomo.ui.util3d.Vector3D {
    public Vector3D() { }
    public Vector3D(Vector3D vector) { super(vector); }
    public Vector3D(float x, float y, float z) { super(x, y, z); }
    /** Host integration; getters of host objects return component copies. */
    public Vector3D(com.nttdocomo.ui.util3d.Vector3D vector) { super(vector); }
    public void set(Vector3D vector) { super.set(vector); }
    public void add(Vector3D vector) { super.add(vector); }
    public float dot(Vector3D vector) { return super.dot(vector); }
    public static float dot(Vector3D first, Vector3D second) {
        return com.nttdocomo.ui.util3d.Vector3D.dot(first, second);
    }
    public void cross(Vector3D vector) { super.cross(vector); }
    public void cross(Vector3D first, Vector3D second) { super.cross(first, second); }
}
