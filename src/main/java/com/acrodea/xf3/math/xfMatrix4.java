package com.acrodea.xf3.math;

public class xfMatrix4 {
    public float[][] m;

    public xfMatrix4() {
        m = new float[4][4];
        setIdentity();
    }

    public xfMatrix4(xfMatrix4 value) {
        this();
        set(value);
    }

    public void set(xfMatrix4 value) {
        if (value == null || value.m == null) {
            setIdentity();
            return;
        }
        ensureArray();
        for (int row = 0; row < 4; row++) {
            System.arraycopy(value.m[row], 0, m[row], 0, 4);
        }
    }

    public void mul(xfMatrix4 value) {
        if (value == null) {
            return;
        }
        ensureArray();
        float[][] result = new float[4][4];
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                float sum = 0f;
                for (int i = 0; i < 4; i++) {
                    sum += m[row][i] * value.m[i][column];
                }
                result[row][column] = sum;
            }
        }
        m = result;
    }

    public void setIdentity() {
        ensureArray();
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                m[row][column] = row == column ? 1f : 0f;
            }
        }
    }

    public xfMatrix4 transpose() {
        xfMatrix4 transposed = new xfMatrix4();
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                transposed.m[row][column] = m[column][row];
            }
        }
        return transposed;
    }

    public float[] toColumnMajorArray() {
        ensureArray();
        float[] values = new float[16];
        int index = 0;
        // XF3 matrices exposed to games are the transpose of the OpenGL-style
        // internal layout, so row-major flattening yields the expected
        // column-major upload order.
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 4; column++) {
                values[index++] = m[row][column];
            }
        }
        return values;
    }

    public static xfMatrix4 transposedCopy(xfMatrix4 value) {
        if (value == null) {
            return new xfMatrix4();
        }
        return value.transpose();
    }

    private void ensureArray() {
        if (m == null || m.length != 4) {
            m = new float[4][4];
        }
        for (int column = 0; column < 4; column++) {
            if (m[column] == null || m[column].length != 4) {
                m[column] = new float[4];
            }
        }
    }
}
