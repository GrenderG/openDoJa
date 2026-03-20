package com.nttdocomo.opt.ui.j3d;

import opendoja.g3d.FixedPoint;

public class Vector3D {
    public int x;
    public int y;
    public int z;

    public Vector3D() {
    }

    public Vector3D(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void normalize() {
        int length = FixedPoint.sqrt(x * x + y * y + z * z);
        if (length == 0) {
            x = 0;
            y = 0;
            z = FixedPoint.ONE;
            return;
        }
        x = (x << 12) / length;
        y = (y << 12) / length;
        z = (z << 12) / length;
    }

    public int dot(Vector3D other) {
        return x * other.x + y * other.y + z * other.z;
    }

    public static int dot(Vector3D left, Vector3D right) {
        return left.dot(right);
    }

    public void cross(Vector3D other) {
        cross(this, other);
    }

    public void cross(Vector3D left, Vector3D right) {
        int nextX = left.y * right.z - left.z * right.y;
        int nextY = left.z * right.x - left.x * right.z;
        int nextZ = left.x * right.y - left.y * right.x;
        x = nextX;
        y = nextY;
        z = nextZ;
    }
}
