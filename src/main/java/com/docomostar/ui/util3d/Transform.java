package com.docomostar.ui.util3d;

/** STAR descriptors over the shared float matrix implementation. */
public class Transform extends com.nttdocomo.ui.util3d.Transform {
    public Transform() { }
    public Transform(Transform transform) { super(transform); }
    public void set(Transform transform) { super.set(transform); }
    public void multiply(Transform transform) { super.multiply(transform); }
    public void scale(Vector3D vector) { super.scale(vector); }
    public void rotate(Vector3D vector, float angle) { super.rotate(vector, angle); }
    public void rotateQuat(Vector3D vector, float w) { super.rotateQuat(vector, w); }
    public void translate(Vector3D vector) { super.translate(vector); }
    public void lookAt(Vector3D position, Vector3D look, Vector3D up) { super.lookAt(position, look, up); }
    public void transVector(Vector3D vector, Vector3D result) { super.transVector(vector, result); }
}
