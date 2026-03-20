package com.nttdocomo.ui.util3d;

public class Vector3D {
    private float x;
    private float y;
    private float z;

    public Vector3D() {
    }

    public Vector3D(Vector3D other) {
        set(other);
    }

    public Vector3D(float x, float y, float z) {
        set(x, y, z);
    }

    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public void set(Vector3D other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        set(other.x, other.y, other.z);
    }

    public void add(float x, float y, float z) {
        this.x += x;
        this.y += y;
        this.z += z;
    }

    public void add(Vector3D other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        add(other.x, other.y, other.z);
    }

    public void setX(float x) {
        this.x = x;
    }

    public void setY(float y) {
        this.y = y;
    }

    public void setZ(float z) {
        this.z = z;
    }

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public void normalize() {
        float length = (float) java.lang.Math.sqrt(x * x + y * y + z * z);
        if (length <= 0f) {
            x = 0f;
            y = 0f;
            z = 1f;
            return;
        }
        x /= length;
        y /= length;
        z /= length;
    }

    public float dot(Vector3D other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return x * other.x + y * other.y + z * other.z;
    }

    public static float dot(Vector3D left, Vector3D right) {
        return left.dot(right);
    }

    public void cross(Vector3D other) {
        cross(this, other);
    }

    public void cross(Vector3D left, Vector3D right) {
        if (left == null || right == null) {
            throw new NullPointerException("vector");
        }
        float nextX = left.y * right.z - left.z * right.y;
        float nextY = left.z * right.x - left.x * right.z;
        float nextZ = left.x * right.y - left.y * right.x;
        x = nextX;
        y = nextY;
        z = nextZ;
    }
}
