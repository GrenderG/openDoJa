package com.docomostar.ui.ogl.math;

public final class Vector3f extends Tuple3f {
    private final com.nttdocomo.ui.ogl.math.Vector3f host = new com.nttdocomo.ui.ogl.math.Vector3f();
    public Vector3f() { }
    public Vector3f(float x, float y, float z) { super(x, y, z); }
    com.nttdocomo.ui.ogl.math.Vector3f hostVector() {
        host.x = x; host.y = y; host.z = z;
        return host;
    }
    public void normalize() {
        var vector = hostVector(); vector.normalize(); x = vector.x; y = vector.y; z = vector.z;
    }
    public float dot(Vector3f vector) { return hostVector().dot(vector.hostVector()); }
    public void cross(Vector3f left, Vector3f right) {
        var vector = hostVector(); vector.cross(left.hostVector(), right.hostVector());
        x = vector.x; y = vector.y; z = vector.z;
    }
}
