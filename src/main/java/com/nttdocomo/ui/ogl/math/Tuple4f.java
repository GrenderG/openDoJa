package com.nttdocomo.ui.ogl.math;

/**
 * Defines a 4-element float tuple.
 */
public abstract class Tuple4f {
    /**
     * Constant for x.
     */
    public float x;
    /**
     * Constant for y.
     */
    public float y;
    /**
     * Constant for z.
     */
    public float z;
    /**
     * Constant for w.
     */
    public float w;

    /**
     * Creates a tuple initialized to zero.
     */
    public Tuple4f() {
    }

    /**
     * Creates a tuple with the specified values.
     *
     * @param x the x component
     * @param y the y component
     * @param z the z component
     * @param w the w component
     */
    public Tuple4f(float x, float y, float z, float w) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.w = w;
    }

    /**
     * Adds another tuple to this tuple.
     *
     * @param tuple the tuple to add
     */
    public void add(Tuple4f tuple) {
        if (tuple == null) {
            throw new NullPointerException("tuple");
        }
        x += tuple.x;
        y += tuple.y;
        z += tuple.z;
        w += tuple.w;
    }

    /**
     * Multiplies this tuple by the specified matrix.
     *
     * @param matrix the matrix to apply
     */
    public void mul(Matrix4f matrix) {
        if (matrix == null) {
            throw new NullPointerException("matrix");
        }
        matrix.transform(this);
    }
}
