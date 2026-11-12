package com.docomostar.opt.ui.j3d;

/** Inherits the mutable matrix storage used directly by the shared renderer. */
public class AffineTrans extends com.nttdocomo.opt.ui.j3d.AffineTrans {
    public AffineTrans() { }
    public AffineTrans(int a00, int a01, int a02, int a03, int a10, int a11, int a12, int a13,
                       int a20, int a21, int a22, int a23) {
        super(a00, a01, a02, a03, a10, a11, a12, a13, a20, a21, a22, a23);
    }
    public void lookAt(Vector3D position, Vector3D look, Vector3D up) { super.lookAt(position, look, up); }
    public void mul(AffineTrans transform) { super.mul(transform); }
    public void mul(AffineTrans a, AffineTrans b) { super.mul(a, b); }
    public void setRotateV(Vector3D axis, int angle) { super.setRotateV(axis, angle); }
    public void transform(Vector3D point, Vector3D result) { super.transform(point, result); }
}
