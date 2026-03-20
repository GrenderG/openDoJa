package com.nttdocomo.ui.util3d;

import opendoja.g3d.Software3DContext;

public class Transform {
    private final float[] matrix = Software3DContext.identity();

    public Transform() {
    }

    public Transform(Transform other) {
        set(other);
    }

    public void setIdentity() {
        System.arraycopy(Software3DContext.identity(), 0, matrix, 0, 16);
    }

    public void set(Transform other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        System.arraycopy(other.matrix, 0, matrix, 0, 16);
    }

    public void set(float[] values) {
        if (values == null || values.length < 16) {
            throw new IllegalArgumentException("values");
        }
        System.arraycopy(values, 0, matrix, 0, 16);
    }

    public void get(float[] values) {
        if (values == null || values.length < 16) {
            throw new IllegalArgumentException("values");
        }
        System.arraycopy(matrix, 0, values, 0, 16);
    }

    public void set(int index, float value) {
        matrix[index] = value;
    }

    public float get(int index) {
        return matrix[index];
    }

    public void invert() {
        float[] inverse = invertMatrix(matrix);
        System.arraycopy(inverse, 0, matrix, 0, 16);
    }

    public void transpose() {
        swap(1, 4);
        swap(2, 8);
        swap(3, 12);
        swap(6, 9);
        swap(7, 13);
        swap(11, 14);
    }

    public void multiply(Transform other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        float[] result = Software3DContext.multiply(matrix, other.matrix);
        System.arraycopy(result, 0, matrix, 0, 16);
    }

    public void scale(float x, float y, float z) {
        float[] scale = Software3DContext.identity();
        scale[0] = x;
        scale[5] = y;
        scale[10] = z;
        multiply(scale);
    }

    public void scale(Vector3D vector) {
        scale(vector.getX(), vector.getY(), vector.getZ());
    }

    public void rotate(float x, float y, float z, float angle) {
        Vector3D axis = new Vector3D(x, y, z);
        rotate(axis, angle);
    }

    public void rotate(Vector3D axis, float angle) {
        if (axis == null) {
            throw new NullPointerException("axis");
        }
        Vector3D normalized = new Vector3D(axis);
        normalized.normalize();
        float c = (float) java.lang.Math.cos(angle);
        float s = (float) java.lang.Math.sin(angle);
        float t = 1f - c;
        float x = normalized.getX();
        float y = normalized.getY();
        float z = normalized.getZ();
        float[] rotation = Software3DContext.identity();
        rotation[0] = c + x * x * t;
        rotation[1] = x * y * t - z * s;
        rotation[2] = x * z * t + y * s;
        rotation[4] = y * x * t + z * s;
        rotation[5] = c + y * y * t;
        rotation[6] = y * z * t - x * s;
        rotation[8] = z * x * t - y * s;
        rotation[9] = z * y * t + x * s;
        rotation[10] = c + z * z * t;
        multiply(rotation);
    }

    public void rotateQuat(float x, float y, float z, float w) {
        rotateQuat(new Vector3D(x, y, z), w);
    }

    public void rotateQuat(Vector3D axis, float w) {
        if (axis == null) {
            throw new NullPointerException("axis");
        }
        float angle = 2f * (float) java.lang.Math.acos(w);
        rotate(axis, angle);
    }

    public void translate(float x, float y, float z) {
        float[] translation = Software3DContext.identity();
        translation[3] = x;
        translation[7] = y;
        translation[11] = z;
        multiply(translation);
    }

    public void translate(Vector3D vector) {
        translate(vector.getX(), vector.getY(), vector.getZ());
    }

    public void lookAt(Vector3D eye, Vector3D center, Vector3D up) {
        if (eye == null || center == null || up == null) {
            throw new NullPointerException("vector");
        }
        Vector3D forward = new Vector3D(center);
        forward.add(-eye.getX(), -eye.getY(), -eye.getZ());
        forward.normalize();
        Vector3D side = new Vector3D();
        side.cross(forward, up);
        side.normalize();
        Vector3D actualUp = new Vector3D();
        actualUp.cross(side, forward);
        setIdentity();
        matrix[0] = side.getX();
        matrix[1] = side.getY();
        matrix[2] = side.getZ();
        matrix[4] = actualUp.getX();
        matrix[5] = actualUp.getY();
        matrix[6] = actualUp.getZ();
        matrix[8] = forward.getX();
        matrix[9] = forward.getY();
        matrix[10] = forward.getZ();
        matrix[3] = -side.dot(eye);
        matrix[7] = -actualUp.dot(eye);
        matrix[11] = -forward.dot(eye);
    }

    public void transVector(Vector3D source, Vector3D destination) {
        if (source == null || destination == null) {
            throw new NullPointerException("vector");
        }
        destination.set(
                matrix[0] * source.getX() + matrix[1] * source.getY() + matrix[2] * source.getZ() + matrix[3],
                matrix[4] * source.getX() + matrix[5] * source.getY() + matrix[6] * source.getZ() + matrix[7],
                matrix[8] * source.getX() + matrix[9] * source.getY() + matrix[10] * source.getZ() + matrix[11]
        );
    }

    float[] raw() {
        return matrix.clone();
    }

    private void multiply(float[] other) {
        float[] result = Software3DContext.multiply(matrix, other);
        System.arraycopy(result, 0, matrix, 0, 16);
    }

    private void swap(int left, int right) {
        float value = matrix[left];
        matrix[left] = matrix[right];
        matrix[right] = value;
    }

    private static float[] invertMatrix(float[] matrix) {
        float[] inverse = new float[16];
        float det;
        inverse[0] = matrix[5] * matrix[10] * matrix[15] - matrix[5] * matrix[11] * matrix[14] - matrix[9] * matrix[6] * matrix[15] + matrix[9] * matrix[7] * matrix[14] + matrix[13] * matrix[6] * matrix[11] - matrix[13] * matrix[7] * matrix[10];
        inverse[4] = -matrix[4] * matrix[10] * matrix[15] + matrix[4] * matrix[11] * matrix[14] + matrix[8] * matrix[6] * matrix[15] - matrix[8] * matrix[7] * matrix[14] - matrix[12] * matrix[6] * matrix[11] + matrix[12] * matrix[7] * matrix[10];
        inverse[8] = matrix[4] * matrix[9] * matrix[15] - matrix[4] * matrix[11] * matrix[13] - matrix[8] * matrix[5] * matrix[15] + matrix[8] * matrix[7] * matrix[13] + matrix[12] * matrix[5] * matrix[11] - matrix[12] * matrix[7] * matrix[9];
        inverse[12] = -matrix[4] * matrix[9] * matrix[14] + matrix[4] * matrix[10] * matrix[13] + matrix[8] * matrix[5] * matrix[14] - matrix[8] * matrix[6] * matrix[13] - matrix[12] * matrix[5] * matrix[10] + matrix[12] * matrix[6] * matrix[9];
        inverse[1] = -matrix[1] * matrix[10] * matrix[15] + matrix[1] * matrix[11] * matrix[14] + matrix[9] * matrix[2] * matrix[15] - matrix[9] * matrix[3] * matrix[14] - matrix[13] * matrix[2] * matrix[11] + matrix[13] * matrix[3] * matrix[10];
        inverse[5] = matrix[0] * matrix[10] * matrix[15] - matrix[0] * matrix[11] * matrix[14] - matrix[8] * matrix[2] * matrix[15] + matrix[8] * matrix[3] * matrix[14] + matrix[12] * matrix[2] * matrix[11] - matrix[12] * matrix[3] * matrix[10];
        inverse[9] = -matrix[0] * matrix[9] * matrix[15] + matrix[0] * matrix[11] * matrix[13] + matrix[8] * matrix[1] * matrix[15] - matrix[8] * matrix[3] * matrix[13] - matrix[12] * matrix[1] * matrix[11] + matrix[12] * matrix[3] * matrix[9];
        inverse[13] = matrix[0] * matrix[9] * matrix[14] - matrix[0] * matrix[10] * matrix[13] - matrix[8] * matrix[1] * matrix[14] + matrix[8] * matrix[2] * matrix[13] + matrix[12] * matrix[1] * matrix[10] - matrix[12] * matrix[2] * matrix[9];
        inverse[2] = matrix[1] * matrix[6] * matrix[15] - matrix[1] * matrix[7] * matrix[14] - matrix[5] * matrix[2] * matrix[15] + matrix[5] * matrix[3] * matrix[14] + matrix[13] * matrix[2] * matrix[7] - matrix[13] * matrix[3] * matrix[6];
        inverse[6] = -matrix[0] * matrix[6] * matrix[15] + matrix[0] * matrix[7] * matrix[14] + matrix[4] * matrix[2] * matrix[15] - matrix[4] * matrix[3] * matrix[14] - matrix[12] * matrix[2] * matrix[7] + matrix[12] * matrix[3] * matrix[6];
        inverse[10] = matrix[0] * matrix[5] * matrix[15] - matrix[0] * matrix[7] * matrix[13] - matrix[4] * matrix[1] * matrix[15] + matrix[4] * matrix[3] * matrix[13] + matrix[12] * matrix[1] * matrix[7] - matrix[12] * matrix[3] * matrix[5];
        inverse[14] = -matrix[0] * matrix[5] * matrix[14] + matrix[0] * matrix[6] * matrix[13] + matrix[4] * matrix[1] * matrix[14] - matrix[4] * matrix[2] * matrix[13] - matrix[12] * matrix[1] * matrix[6] + matrix[12] * matrix[2] * matrix[5];
        inverse[3] = -matrix[1] * matrix[6] * matrix[11] + matrix[1] * matrix[7] * matrix[10] + matrix[5] * matrix[2] * matrix[11] - matrix[5] * matrix[3] * matrix[10] - matrix[9] * matrix[2] * matrix[7] + matrix[9] * matrix[3] * matrix[6];
        inverse[7] = matrix[0] * matrix[6] * matrix[11] - matrix[0] * matrix[7] * matrix[10] - matrix[4] * matrix[2] * matrix[11] + matrix[4] * matrix[3] * matrix[10] + matrix[8] * matrix[2] * matrix[7] - matrix[8] * matrix[3] * matrix[6];
        inverse[11] = -matrix[0] * matrix[5] * matrix[11] + matrix[0] * matrix[7] * matrix[9] + matrix[4] * matrix[1] * matrix[11] - matrix[4] * matrix[3] * matrix[9] - matrix[8] * matrix[1] * matrix[7] + matrix[8] * matrix[3] * matrix[5];
        inverse[15] = matrix[0] * matrix[5] * matrix[10] - matrix[0] * matrix[6] * matrix[9] - matrix[4] * matrix[1] * matrix[10] + matrix[4] * matrix[2] * matrix[9] + matrix[8] * matrix[1] * matrix[6] - matrix[8] * matrix[2] * matrix[5];
        det = matrix[0] * inverse[0] + matrix[1] * inverse[4] + matrix[2] * inverse[8] + matrix[3] * inverse[12];
        if (det == 0f) {
            return Software3DContext.identity();
        }
        det = 1f / det;
        for (int i = 0; i < inverse.length; i++) {
            inverse[i] *= det;
        }
        return inverse;
    }
}
