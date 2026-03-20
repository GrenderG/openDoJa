package com.nttdocomo.ui.graphics3d;

public class Fog extends Object3D {
    public static final int EXPONENTIAL = 80;
    public static final int LINEAR = 81;

    private int mode = LINEAR;
    private float linearNear;
    private float linearFar = 1f;
    private float density = 1f;
    private int color = 0xFFFFFFFF;

    public Fog() {
        super(TYPE_FOG);
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public void setLinear(float near, float far) {
        this.linearNear = near;
        this.linearFar = far;
    }

    public void setDensity(float density) {
        this.density = density;
    }

    public void setColor(int color) {
        this.color = color;
    }
}
