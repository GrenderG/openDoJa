package com.nttdocomo.ui.ogl.math;

/**
 * Defines a 3-element float tuple.
 */
public abstract class Tuple3f {
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
     * Creates a tuple initialized to zero.
     */
    public Tuple3f() {
    }

    /**
     * Creates a tuple with the specified values.
     *
     * @param x the x component
     * @param y the y component
     * @param z the z component
     */
    public Tuple3f(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /**
     * Adds another tuple to this tuple.
     *
     * @param tuple the tuple to add
     */
    public void add(Tuple3f tuple) {
        if (tuple == null) {
            throw new NullPointerException("tuple");
        }
        x += tuple.x;
        y += tuple.y;
        z += tuple.z;
    }
}
