package com.nttdocomo.ui.graphics3d;

/**
 * Defines the data that controls a fog effect.
 */
public class Fog extends Object3D {
    /**
     * Exponential fog mode.
     */
    public static final int EXPONENTIAL = 80;
    /**
     * Linear fog mode.
     */
    public static final int LINEAR = 81;

    private int mode = LINEAR;
    private float linearNear;
    private float linearFar = 1f;
    private float density = 1f;
    private int color = 0xFFFFFFFF;

    /**
     * Creates a fog object.
     */
    public Fog() {
        super(TYPE_FOG);
    }

    /**
     * Sets the fog mode.
     *
     * @param mode the fog mode, either {@link #EXPONENTIAL} or {@link #LINEAR}
     */
    public void setMode(int mode) {
        this.mode = mode;
    }

    /**
     * Sets the near and far distances used by linear fog mode.
     *
     * @param near the start distance
     * @param far the end distance
     */
    public void setLinear(float near, float far) {
        this.linearNear = near;
        this.linearFar = far;
    }

    /**
     * Sets the density used by exponential fog mode.
     *
     * @param density the fog density
     */
    public void setDensity(float density) {
        this.density = density;
    }

    /**
     * Sets the fog color in {@code 0x00RRGGBB} form.
     *
     * @param color the fog color
     */
    public void setColor(int color) {
        this.color = color;
    }

    int mode() {
        return mode;
    }

    float linearNear() {
        return linearNear;
    }

    float linearFar() {
        return linearFar;
    }

    float density() {
        return density;
    }

    int color() {
        return color;
    }
}
