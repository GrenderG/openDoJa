package com.docomostar.ui.ogl.math;

/** Maintains STAR's public matrix array while using the shared matrix operations. */
public final class Matrix4f {
    private final com.nttdocomo.ui.ogl.math.Matrix4f host;
    public float[] m;
    public Matrix4f() { host = new com.nttdocomo.ui.ogl.math.Matrix4f(); m = host.m; }
    public Matrix4f(Matrix4f source) { host = new com.nttdocomo.ui.ogl.math.Matrix4f(unwrap(source)); m = host.m; }
    public static com.nttdocomo.ui.ogl.math.Matrix4f unwrap(Matrix4f matrix) {
        java.util.Objects.requireNonNull(matrix, "matrix");
        matrix.host.m = matrix.m;
        return matrix.host;
    }
    public void mul(Matrix4f matrix) { unwrap(this).mul(unwrap(matrix)); m = host.m; }
    public void mul(Matrix4f left, Matrix4f right) { unwrap(this).mul(unwrap(left), unwrap(right)); m = host.m; }
    public void transform(Tuple4f tuple) { unwrap(this).transform(tuple); m = host.m; }
    public void invert() { unwrap(this).invert(); m = host.m; }
    public void setIdentity() { unwrap(this).setIdentity(); m = host.m; }
    public void translate(float x, float y, float z) { unwrap(this).translate(x, y, z); m = host.m; }
    public void rotate(float angle, float x, float y, float z) { unwrap(this).rotate(angle, x, y, z); m = host.m; }
    public void scale(float x, float y, float z) { unwrap(this).scale(x, y, z); m = host.m; }
    public void lookAt(Point3f eye, Point3f center, Vector3f up) {
        unwrap(this).lookAt(new com.nttdocomo.ui.ogl.math.Point3f(eye.x, eye.y, eye.z),
                new com.nttdocomo.ui.ogl.math.Point3f(center.x, center.y, center.z), up.hostVector());
        m = host.m;
    }
}
