package com.nttdocomo.ui.graphics3d;

import com.nttdocomo.ui.util3d.Vector3D;

public class Light extends Object3D {
    public static final int AMBIENT = 128;
    public static final int DIRECTIONAL = 129;
    public static final int OMNI = 130;
    public static final int SPOT = 131;

    private Vector3D position = new Vector3D();
    private Vector3D vector = new Vector3D(0f, 0f, 1f);
    private int mode = AMBIENT;
    private float intensity = 1f;
    private int color = 0xFFFFFFFF;
    private float spotAngle;
    private float spotExponent;
    private float attenuationConstant = 1f;
    private float attenuationLinear;
    private float attenuationQuadratic;

    public Light() {
        super(TYPE_LIGHT);
    }

    public void setPosition(Vector3D position) {
        this.position = position == null ? new Vector3D() : new Vector3D(position);
    }

    public Vector3D getPosition() {
        return new Vector3D(position);
    }

    public void setVector(Vector3D vector) {
        this.vector = vector == null ? new Vector3D(0f, 0f, 1f) : new Vector3D(vector);
    }

    public Vector3D getVector() {
        return new Vector3D(vector);
    }

    public void setMode(int mode) {
        this.mode = mode;
    }

    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setSpotAngle(float spotAngle) {
        this.spotAngle = spotAngle;
    }

    public void setSpotExponent(float spotExponent) {
        this.spotExponent = spotExponent;
    }

    public void setAttenuation(float constant, float linear, float quadratic) {
        this.attenuationConstant = constant;
        this.attenuationLinear = linear;
        this.attenuationQuadratic = quadratic;
    }

    public static int getMaxLights() {
        return 8;
    }

    int mode() {
        return mode;
    }

    float intensity() {
        return intensity;
    }

    int color() {
        return color;
    }
}
