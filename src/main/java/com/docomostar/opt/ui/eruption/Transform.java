package com.docomostar.opt.ui.eruption;

import com.nttdocomo.ui.ogl.math.Matrix4f;
import com.nttdocomo.ui.ogl.math.Vector4f;

/** Converts Eruption's public row-major arrays to the shared GLES column-major math. */
public final class Transform {
    public static final int MCE_ROTATE_EULER_XYZ = 0, MCE_ROTATE_EULER_XZY = 1,
            MCE_ROTATE_EULER_YXZ = 2, MCE_ROTATE_EULER_YZX = 3,
            MCE_ROTATE_EULER_ZXY = 4, MCE_ROTATE_EULER_ZYX = 5;
    final Matrix4f matrix = new Matrix4f();
    public Transform() { matrix.setIdentity(); }
    public Transform(float[] values) { set(values); }
    public void set(float[] values) {
        if (values.length < 16) throw new IllegalArgumentException("16 matrix elements required");
        for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++)
            matrix.m[col * 4 + row] = values[row * 4 + col];
    }
    public void get(float[] values) {
        if (values.length < 16) throw new IllegalArgumentException("16 matrix elements required");
        for (int row = 0; row < 4; row++) for (int col = 0; col < 4; col++)
            values[row * 4 + col] = matrix.m[col * 4 + row];
    }
    public void copy(Transform source) { System.arraycopy(source.matrix.m, 0, matrix.m, 0, 16); }
    public void setIdentity() { matrix.setIdentity(); }
    public void multiply(Transform right) { matrix.mul(right.matrix); }
    public void multiply(Transform left, Transform right) { matrix.mul(left.matrix, right.matrix); }
    public void setTranslate(float x, float y, float z) { setIdentity(); matrix.translate(x, y, z); }
    public void setTranslate(Vector3D v) { setTranslate(v.getX(), v.getY(), v.getZ()); }
    public void setScale(float x, float y, float z) { setIdentity(); matrix.scale(x, y, z); }
    public void setScale(Vector3D v) { setScale(v.getX(), v.getY(), v.getZ()); }
    public void setRotate(Vector3D axis, float turns) {
        setIdentity(); matrix.rotate(turns * 360, axis.getX(), axis.getY(), axis.getZ());
    }
    public void setRotateEuler(int order, Vector3D angles) {
        if (order < 0 || order > 5) throw new IllegalArgumentException("Euler order");
        setIdentity();
        String axes = new String[]{"XYZ", "XZY", "YXZ", "YZX", "ZXY", "ZYX"}[order];
        for (int i = 2; i >= 0; i--) {
            char axis = axes.charAt(i);
            float angle = axis == 'X' ? angles.getX() : axis == 'Y' ? angles.getY() : angles.getZ();
            matrix.rotate(angle * 360, axis == 'X' ? 1 : 0, axis == 'Y' ? 1 : 0, axis == 'Z' ? 1 : 0);
        }
    }
    public void setInvert(Transform source) { copy(source); matrix.invert(); }
    public void setTranspose(Transform source) {
        float[] values = source.matrix.m.clone();
        for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++) matrix.m[c * 4 + r] = values[r * 4 + c];
    }
    public void transPosition(Vector3D vector) { transPosition(vector, vector); }
    public void transPosition(Vector3D destination, Vector3D source) {
        Vector4f v = new Vector4f(source.getX(), source.getY(), source.getZ(), 1);
        matrix.transform(v); destination.set(v.x, v.y, v.z);
    }
    static Transform fromColumnMajor(float[] values) {
        Transform result = new Transform();
        System.arraycopy(values, 0, result.matrix.m, 0, 16);
        return result;
    }
    Transform snapshot() { return fromColumnMajor(matrix.m); }
    public void dispose() { }
}
