package com.nttdocomo.opt.ui.j3d;

import opendoja.g3d.FixedPoint;

public class AffineTrans {
    public int m00 = FixedPoint.ONE;
    public int m01;
    public int m02;
    public int m03;
    public int m10;
    public int m11 = FixedPoint.ONE;
    public int m12;
    public int m13;
    public int m20;
    public int m21;
    public int m22 = FixedPoint.ONE;
    public int m23;

    public AffineTrans() {
    }

    public AffineTrans(int m00, int m01, int m02, int m03,
                       int m10, int m11, int m12, int m13,
                       int m20, int m21, int m22, int m23) {
        setElement(m00, m01, m02, m03, m10, m11, m12, m13, m20, m21, m22, m23);
    }

    public void setElement(int row, int column, int value) {
        switch (row * 4 + column) {
            case 0 -> m00 = value;
            case 1 -> m01 = value;
            case 2 -> m02 = value;
            case 3 -> m03 = value;
            case 4 -> m10 = value;
            case 5 -> m11 = value;
            case 6 -> m12 = value;
            case 7 -> m13 = value;
            case 8 -> m20 = value;
            case 9 -> m21 = value;
            case 10 -> m22 = value;
            case 11 -> m23 = value;
            default -> throw new ArrayIndexOutOfBoundsException();
        }
    }

    public void setElement(int m00, int m01, int m02, int m03,
                           int m10, int m11, int m12, int m13,
                           int m20, int m21, int m22, int m23) {
        this.m00 = m00;
        this.m01 = m01;
        this.m02 = m02;
        this.m03 = m03;
        this.m10 = m10;
        this.m11 = m11;
        this.m12 = m12;
        this.m13 = m13;
        this.m20 = m20;
        this.m21 = m21;
        this.m22 = m22;
        this.m23 = m23;
    }

    public void setRow(int row, int a, int b, int c, int d) {
        switch (row) {
            case 0 -> setElement(a, b, c, d, m10, m11, m12, m13, m20, m21, m22, m23);
            case 1 -> setElement(m00, m01, m02, m03, a, b, c, d, m20, m21, m22, m23);
            case 2 -> setElement(m00, m01, m02, m03, m10, m11, m12, m13, a, b, c, d);
            default -> throw new ArrayIndexOutOfBoundsException();
        }
    }

    public void setColumn(int column, int a, int b, int c) {
        switch (column) {
            case 0 -> {
                m00 = a;
                m10 = b;
                m20 = c;
            }
            case 1 -> {
                m01 = a;
                m11 = b;
                m21 = c;
            }
            case 2 -> {
                m02 = a;
                m12 = b;
                m22 = c;
            }
            case 3 -> {
                m03 = a;
                m13 = b;
                m23 = c;
            }
            default -> throw new ArrayIndexOutOfBoundsException();
        }
    }

    public void setIdentity() {
        m00 = FixedPoint.ONE;
        m01 = 0;
        m02 = 0;
        m03 = 0;
        m10 = 0;
        m11 = FixedPoint.ONE;
        m12 = 0;
        m13 = 0;
        m20 = 0;
        m21 = 0;
        m22 = FixedPoint.ONE;
        m23 = 0;
    }

    public void mul(AffineTrans other) {
        mul(this, other);
    }

    public void mul(AffineTrans left, AffineTrans right) {
        if (left == null || right == null) {
            throw new NullPointerException();
        }
        int rm00 = FixedPoint.mul(left.m00, right.m00) + FixedPoint.mul(left.m01, right.m10) + FixedPoint.mul(left.m02, right.m20);
        int rm01 = FixedPoint.mul(left.m00, right.m01) + FixedPoint.mul(left.m01, right.m11) + FixedPoint.mul(left.m02, right.m21);
        int rm02 = FixedPoint.mul(left.m00, right.m02) + FixedPoint.mul(left.m01, right.m12) + FixedPoint.mul(left.m02, right.m22);
        int rm03 = FixedPoint.mul(left.m00, right.m03) + FixedPoint.mul(left.m01, right.m13) + FixedPoint.mul(left.m02, right.m23) + left.m03;
        int rm10 = FixedPoint.mul(left.m10, right.m00) + FixedPoint.mul(left.m11, right.m10) + FixedPoint.mul(left.m12, right.m20);
        int rm11 = FixedPoint.mul(left.m10, right.m01) + FixedPoint.mul(left.m11, right.m11) + FixedPoint.mul(left.m12, right.m21);
        int rm12 = FixedPoint.mul(left.m10, right.m02) + FixedPoint.mul(left.m11, right.m12) + FixedPoint.mul(left.m12, right.m22);
        int rm13 = FixedPoint.mul(left.m10, right.m03) + FixedPoint.mul(left.m11, right.m13) + FixedPoint.mul(left.m12, right.m23) + left.m13;
        int rm20 = FixedPoint.mul(left.m20, right.m00) + FixedPoint.mul(left.m21, right.m10) + FixedPoint.mul(left.m22, right.m20);
        int rm21 = FixedPoint.mul(left.m20, right.m01) + FixedPoint.mul(left.m21, right.m11) + FixedPoint.mul(left.m22, right.m21);
        int rm22 = FixedPoint.mul(left.m20, right.m02) + FixedPoint.mul(left.m21, right.m12) + FixedPoint.mul(left.m22, right.m22);
        int rm23 = FixedPoint.mul(left.m20, right.m03) + FixedPoint.mul(left.m21, right.m13) + FixedPoint.mul(left.m22, right.m23) + left.m23;
        setElement(rm00, rm01, rm02, rm03, rm10, rm11, rm12, rm13, rm20, rm21, rm22, rm23);
    }

    public void setRotateX(int angle) {
        int cos = Math.cos(angle);
        int sin = Math.sin(angle);
        setElement(FixedPoint.ONE, 0, 0, 0, 0, cos, -sin, 0, 0, sin, cos, 0);
    }

    public void setRotateY(int angle) {
        int cos = Math.cos(angle);
        int sin = Math.sin(angle);
        setElement(cos, 0, sin, 0, 0, FixedPoint.ONE, 0, 0, -sin, 0, cos, 0);
    }

    public void setRotateZ(int angle) {
        int cos = Math.cos(angle);
        int sin = Math.sin(angle);
        setElement(cos, -sin, 0, 0, sin, cos, 0, 0, 0, 0, FixedPoint.ONE, 0);
    }

    public void setRotateV(Vector3D vector, int angle) {
        if (vector == null) {
            throw new NullPointerException();
        }
        Vector3D axis = new Vector3D(vector.x, vector.y, vector.z);
        axis.normalize();
        int cos = Math.cos(angle);
        int sin = Math.sin(angle);
        int nc = FixedPoint.ONE - cos;
        int x = axis.x;
        int y = axis.y;
        int z = axis.z;
        setElement(
                cos + FixedPoint.mul(FixedPoint.mul(x, x), nc), FixedPoint.mul(FixedPoint.mul(x, y), nc) - FixedPoint.mul(z, sin), FixedPoint.mul(FixedPoint.mul(x, z), nc) + FixedPoint.mul(y, sin), 0,
                FixedPoint.mul(FixedPoint.mul(y, x), nc) + FixedPoint.mul(z, sin), cos + FixedPoint.mul(FixedPoint.mul(y, y), nc), FixedPoint.mul(FixedPoint.mul(y, z), nc) - FixedPoint.mul(x, sin), 0,
                FixedPoint.mul(FixedPoint.mul(z, x), nc) - FixedPoint.mul(y, sin), FixedPoint.mul(FixedPoint.mul(z, y), nc) + FixedPoint.mul(x, sin), cos + FixedPoint.mul(FixedPoint.mul(z, z), nc), 0
        );
    }

    public void lookAt(Vector3D eye, Vector3D center, Vector3D up) {
        if (eye == null || center == null || up == null) {
            throw new NullPointerException();
        }
        Vector3D forward = new Vector3D(center.x - eye.x, center.y - eye.y, center.z - eye.z);
        forward.normalize();
        Vector3D side = new Vector3D();
        side.cross(forward, up);
        side.normalize();
        Vector3D actualUp = new Vector3D();
        actualUp.cross(side, forward);
        setElement(
                side.x, side.y, side.z, -side.dot(eye),
                actualUp.x, actualUp.y, actualUp.z, -actualUp.dot(eye),
                forward.x, forward.y, forward.z, -forward.dot(eye)
        );
    }

    public void transform(Vector3D source, Vector3D destination) {
        if (source == null || destination == null) {
            throw new NullPointerException();
        }
        destination.x = FixedPoint.mul(source.x, m00) + FixedPoint.mul(source.y, m01) + FixedPoint.mul(source.z, m02) + m03;
        destination.y = FixedPoint.mul(source.x, m10) + FixedPoint.mul(source.y, m11) + FixedPoint.mul(source.z, m12) + m13;
        destination.z = FixedPoint.mul(source.x, m20) + FixedPoint.mul(source.y, m21) + FixedPoint.mul(source.z, m22) + m23;
    }

    float[] toFloatMatrix() {
        return new float[]{
                m00 / 4096f, m01 / 4096f, m02 / 4096f, m03,
                m10 / 4096f, m11 / 4096f, m12 / 4096f, m13,
                m20 / 4096f, m21 / 4096f, m22 / 4096f, m23,
                0f, 0f, 0f, 1f
        };
    }
}
