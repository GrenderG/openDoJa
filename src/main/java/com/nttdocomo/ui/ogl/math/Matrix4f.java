package com.nttdocomo.ui.ogl.math;

/**
 * Defines a 4x4 float matrix in OpenGL column-major order.
 */
public final class Matrix4f {
    /**
     * Constant for m.
     */
    public float[] m;

    /**
     * Creates an identity matrix.
     */
    public Matrix4f() {
        m = new float[16];
        setIdentity();
    }

    /**
     * Creates a copy of the specified matrix.
     *
     * @param matrix the matrix to copy
     */
    public Matrix4f(Matrix4f matrix) {
        if (matrix == null) {
            throw new NullPointerException("matrix");
        }
        m = matrix.m == null ? null : matrix.m.clone();
        if (m == null) {
            m = new float[16];
            setIdentity();
        }
    }

    /**
     * Post-multiplies this matrix by the specified matrix.
     *
     * @param matrix the matrix to multiply by
     */
    public void mul(Matrix4f matrix) {
        if (matrix == null) {
            throw new NullPointerException("matrix");
        }
        mul(this, matrix);
    }

    /**
     * Sets this matrix to the product of two matrices.
     *
     * @param left the left matrix
     * @param right the right matrix
     */
    public void mul(Matrix4f left, Matrix4f right) {
        if (left == null || right == null) {
            throw new NullPointerException("matrix");
        }
        ensureArray(left);
        ensureArray(right);
        float[] result = new float[16];
        for (int column = 0; column < 4; column++) {
            for (int row = 0; row < 4; row++) {
                float value = 0f;
                for (int k = 0; k < 4; k++) {
                    value += left.m[(k * 4) + row] * right.m[(column * 4) + k];
                }
                result[(column * 4) + row] = value;
            }
        }
        m = result;
    }

    /**
     * Transforms the specified tuple in place.
     *
     * @param tuple the tuple to transform
     */
    public void transform(Tuple4f tuple) {
        if (tuple == null) {
            throw new NullPointerException("tuple");
        }
        ensureArray(this);
        float x = tuple.x;
        float y = tuple.y;
        float z = tuple.z;
        float w = tuple.w;
        tuple.x = m[0] * x + m[4] * y + m[8] * z + m[12] * w;
        tuple.y = m[1] * x + m[5] * y + m[9] * z + m[13] * w;
        tuple.z = m[2] * x + m[6] * y + m[10] * z + m[14] * w;
        tuple.w = m[3] * x + m[7] * y + m[11] * z + m[15] * w;
    }

    /**
     * Inverts this matrix.
     */
    public void invert() {
        ensureArray(this);
        double[][] augmented = new double[4][8];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                augmented[row][column] = m[(column * 4) + row];
            }
            augmented[row][row + 4] = 1d;
        }
        for (int pivot = 0; pivot < 4; pivot++) {
            int swap = pivot;
            for (int row = pivot + 1; row < 4; row++) {
                if (java.lang.Math.abs(augmented[row][pivot]) > java.lang.Math.abs(augmented[swap][pivot])) {
                    swap = row;
                }
            }
            if (java.lang.Math.abs(augmented[swap][pivot]) < 1e-8d) {
                setIdentity();
                return;
            }
            if (swap != pivot) {
                double[] tmp = augmented[pivot];
                augmented[pivot] = augmented[swap];
                augmented[swap] = tmp;
            }
            double scale = augmented[pivot][pivot];
            for (int column = 0; column < 8; column++) {
                augmented[pivot][column] /= scale;
            }
            for (int row = 0; row < 4; row++) {
                if (row == pivot) {
                    continue;
                }
                double factor = augmented[row][pivot];
                for (int column = 0; column < 8; column++) {
                    augmented[row][column] -= factor * augmented[pivot][column];
                }
            }
        }
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                m[(column * 4) + row] = (float) augmented[row][column + 4];
            }
        }
    }

    /**
     * Sets this matrix to identity.
     */
    public void setIdentity() {
        ensureArray(this);
        for (int i = 0; i < 16; i++) {
            m[i] = 0f;
        }
        m[0] = 1f;
        m[5] = 1f;
        m[10] = 1f;
        m[15] = 1f;
    }

    /**
     * Applies a translation.
     *
     * @param x the x translation
     * @param y the y translation
     * @param z the z translation
     */
    public void translate(float x, float y, float z) {
        Matrix4f translation = new Matrix4f();
        translation.m[12] = x;
        translation.m[13] = y;
        translation.m[14] = z;
        mul(translation);
    }

    /**
     * Applies a rotation about the specified axis.
     *
     * @param angle the rotation angle in radians
     * @param x the x component of the axis
     * @param y the y component of the axis
     * @param z the z component of the axis
     */
    public void rotate(float angle, float x, float y, float z) {
        Vector3f axis = new Vector3f(x, y, z);
        axis.normalize();
        float c = (float) java.lang.Math.cos(angle);
        float s = (float) java.lang.Math.sin(angle);
        float t = 1f - c;
        Matrix4f rotation = new Matrix4f();
        rotation.m[0] = c + axis.x * axis.x * t;
        rotation.m[1] = axis.y * axis.x * t + axis.z * s;
        rotation.m[2] = axis.z * axis.x * t - axis.y * s;
        rotation.m[4] = axis.x * axis.y * t - axis.z * s;
        rotation.m[5] = c + axis.y * axis.y * t;
        rotation.m[6] = axis.z * axis.y * t + axis.x * s;
        rotation.m[8] = axis.x * axis.z * t + axis.y * s;
        rotation.m[9] = axis.y * axis.z * t - axis.x * s;
        rotation.m[10] = c + axis.z * axis.z * t;
        mul(rotation);
    }

    /**
     * Applies a scale.
     *
     * @param x the x scale
     * @param y the y scale
     * @param z the z scale
     */
    public void scale(float x, float y, float z) {
        Matrix4f scale = new Matrix4f();
        scale.m[0] = x;
        scale.m[5] = y;
        scale.m[10] = z;
        mul(scale);
    }

    /**
     * Sets this matrix to a look-at transform.
     *
     * @param eye the eye point
     * @param center the target point
     * @param up the up vector
     */
    public void lookAt(Point3f eye, Point3f center, Vector3f up) {
        if (eye == null || center == null || up == null) {
            throw new NullPointerException("vector");
        }
        Vector3f forward = new Vector3f(center.x - eye.x, center.y - eye.y, center.z - eye.z);
        forward.normalize();
        Vector3f side = new Vector3f();
        side.cross(forward, up);
        side.normalize();
        Vector3f actualUp = new Vector3f();
        actualUp.cross(side, forward);
        setIdentity();
        m[0] = side.x;
        m[1] = side.y;
        m[2] = side.z;
        m[4] = actualUp.x;
        m[5] = actualUp.y;
        m[6] = actualUp.z;
        m[8] = -forward.x;
        m[9] = -forward.y;
        m[10] = -forward.z;
        m[12] = -(side.x * eye.x + side.y * eye.y + side.z * eye.z);
        m[13] = -(actualUp.x * eye.x + actualUp.y * eye.y + actualUp.z * eye.z);
        m[14] = forward.x * eye.x + forward.y * eye.y + forward.z * eye.z;
    }

    private static void ensureArray(Matrix4f matrix) {
        if (matrix.m == null) {
            throw new NullPointerException("m");
        }
        if (matrix.m.length < 16) {
            throw new ArrayIndexOutOfBoundsException("m");
        }
    }
}
