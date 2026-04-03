package com.nttdocomo.ui.graphics3d;

import com.nttdocomo.ui.util3d.Vector3D;

/**
 * Defines a light source used by the graphics3d renderer.
 */
public class Light extends Object3D {
    /**
     * Ambient-light mode.
     */
    public static final int AMBIENT = 128;
    /**
     * Directional-light mode.
     */
    public static final int DIRECTIONAL = 129;
    /**
     * Omni-directional point-light mode.
     */
    public static final int OMNI = 130;
    /**
     * Spot-light mode.
     */
    public static final int SPOT = 131;
    private static final int MAX_LIGHTS = 8;

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

    /**
     * Creates a light object.
     */
    public Light() {
        super(TYPE_LIGHT);
    }

    /**
     * Sets the light position.
     *
     * @param position the light position
     */
    public void setPosition(Vector3D position) {
        this.position = position == null ? new Vector3D() : new Vector3D(position);
    }

    /**
     * Gets the light position.
     *
     * @return a copy of the light position
     */
    public Vector3D getPosition() {
        return new Vector3D(position);
    }

    /**
     * Sets the light direction vector.
     *
     * @param vector the direction vector
     */
    public void setVector(Vector3D vector) {
        this.vector = vector == null ? new Vector3D(0f, 0f, 1f) : new Vector3D(vector);
    }

    /**
     * Gets the light direction vector.
     *
     * @return a copy of the light direction vector
     */
    public Vector3D getVector() {
        return new Vector3D(vector);
    }

    /**
     * Sets the light mode.
     *
     * @param mode the light mode
     */
    public void setMode(int mode) {
        this.mode = mode;
    }

    /**
     * Sets the light intensity.
     *
     * @param intensity the light intensity
     */
    public void setIntensity(float intensity) {
        this.intensity = intensity;
    }

    /**
     * Sets the light color.
     *
     * @param color the light color
     */
    public void setColor(int color) {
        this.color = color;
    }

    /**
     * Sets the spotlight angle.
     *
     * @param spotAngle the spotlight angle
     */
    public void setSpotAngle(float spotAngle) {
        this.spotAngle = spotAngle;
    }

    /**
     * Sets the spotlight exponent.
     *
     * @param spotExponent the spotlight exponent
     */
    public void setSpotExponent(float spotExponent) {
        this.spotExponent = spotExponent;
    }

    /**
     * Sets the attenuation coefficients.
     *
     * @param constant the constant term
     * @param linear the linear term
     * @param quadratic the quadratic term
     */
    public void setAttenuation(float constant, float linear, float quadratic) {
        this.attenuationConstant = constant;
        this.attenuationLinear = linear;
        this.attenuationQuadratic = quadratic;
    }

    /**
     * Gets the maximum number of simultaneously supported lights.
     *
     * @return the maximum light count
     */
    public static int getMaxLights() {
        return MAX_LIGHTS;
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
