package com.docomostar.opt.ui.j3d;

public class Vector3D extends com.nttdocomo.opt.ui.j3d.Vector3D {
    public Vector3D() { }
    public Vector3D(int x, int y, int z) { super(x, y, z); }
    public int dot(Vector3D vector) { return super.dot(vector); }
    public static int dot(Vector3D a, Vector3D b) { return com.nttdocomo.opt.ui.j3d.Vector3D.dot(a, b); }
    public void cross(Vector3D vector) { super.cross(vector); }
    public void cross(Vector3D a, Vector3D b) { super.cross(a, b); }
}
