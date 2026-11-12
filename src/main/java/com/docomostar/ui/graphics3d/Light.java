package com.docomostar.ui.graphics3d;

import com.docomostar.ui.util3d.Vector3D;

public class Light extends Object3D {
    public static final int AMBIENT = 128;
    public static final int DIRECTIONAL = 129;
    public static final int OMNI = 130;
    public static final int SPOT = 131;
    public Light() { this(new com.nttdocomo.ui.graphics3d.Light()); }
    Light(com.nttdocomo.ui.graphics3d.Light delegate) { super(delegate); }
    private com.nttdocomo.ui.graphics3d.Light light() { return (com.nttdocomo.ui.graphics3d.Light) delegate; }
    public void setPosition(Vector3D vector) { light().setPosition(vector); }
    public Vector3D getPosition() { return new Vector3D(light().getPosition()); }
    public void setVector(Vector3D vector) { light().setVector(vector); }
    public Vector3D getVector() { return new Vector3D(light().getVector()); }
    public void setMode(int mode) { light().setMode(mode); }
    public void setIntensity(float value) { light().setIntensity(value); }
    public void setColor(int color) { light().setColor(color); }
    public void setSpotAngle(float value) { light().setSpotAngle(value); }
    public void setSpotExponent(float value) { light().setSpotExponent(value); }
    public void setAttenuation(float constant, float linear, float quadratic) { light().setAttenuation(constant, linear, quadratic); }
    public static int getMaxLights() { return com.nttdocomo.ui.graphics3d.Light.getMaxLights(); }
}
