package com.nttdocomo.ui.util3d;

/**
 * Defines a three-dimensional vector made up of {@code float} x, y, and z
 * components.
 */
public class Vector3D {
    private float x;
    private float y;
    private float z;

    /**
     * Creates a vector whose components are all {@code 0}.
     */
    public Vector3D() {
    }

    /**
     * Creates a new vector by copying all components from another vector.
     *
     * @param other the source vector
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public Vector3D(Vector3D other) {
        set(other);
    }

    /**
     * Creates a vector with the specified components.
     *
     * @param x the x component
     * @param y the y component
     * @param z the z component
     */
    public Vector3D(float x, float y, float z) {
        set(x, y, z);
    }

    /**
     * Sets all components of this vector.
     *
     * @param x the x component
     * @param y the y component
     * @param z the z component
     */
    public void set(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Replaces this vector with a full copy of another vector.
     *
     * @param other the source vector
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public void set(Vector3D other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        set(other.x, other.y, other.z);
    }

    /**
     * Adds the specified component values to this vector.
     *
     * @param x the x component to add
     * @param y the y component to add
     * @param z the z component to add
     */
    public void add(float x, float y, float z) {
        this.x += x;
        this.y += y;
        this.z += z;
    }

    /**
     * Adds another vector to this vector.
     *
     * @param other the vector to add
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public void add(Vector3D other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        add(other.x, other.y, other.z);
    }

    /**
     * Sets the x component.
     *
     * @param x the x component value
     */
    public void setX(float x) {
        this.x = x;
    }

    /**
     * Sets the y component.
     *
     * @param y the y component value
     */
    public void setY(float y) {
        this.y = y;
    }

    /**
     * Sets the z component.
     *
     * @param z the z component value
     */
    public void setZ(float z) {
        this.z = z;
    }

    /**
     * Gets the x component.
     *
     * @return the x component value
     */
    public float getX() {
        return x;
    }

    /**
     * Gets the y component.
     *
     * @return the y component value
     */
    public float getY() {
        return y;
    }

    /**
     * Gets the z component.
     *
     * @return the z component value
     */
    public float getZ() {
        return z;
    }

    /**
     * Normalizes this vector to unit length.
     */
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

    /**
     * Calculates the dot product of this vector and another vector.
     *
     * @param other the other vector
     * @return the dot-product value
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public float dot(Vector3D other) {
        if (other == null) {
            throw new NullPointerException("other");
        }
        return x * other.x + y * other.y + z * other.z;
    }

    /**
     * Calculates the dot product of two vectors.
     *
     * @param left the first vector
     * @param right the second vector
     * @return the dot-product value
     * @throws NullPointerException if either argument is {@code null}
     */
    public static float dot(Vector3D left, Vector3D right) {
        return left.dot(right);
    }

    /**
     * Calculates the cross product of this vector and another vector and stores
     * the result in this object.
     *
     * @param other the other vector
     * @throws NullPointerException if {@code other} is {@code null}
     */
    public void cross(Vector3D other) {
        cross(this, other);
    }

    /**
     * Calculates the cross product {@code left x right} and stores the result
     * in this object.
     *
     * @param left the first vector
     * @param right the second vector
     * @throws NullPointerException if either argument is {@code null}
     */
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
