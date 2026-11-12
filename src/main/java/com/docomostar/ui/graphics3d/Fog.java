package com.docomostar.ui.graphics3d;

public class Fog extends Object3D {
    public static final int EXPONENTIAL = 80;
    public static final int LINEAR = 81;
    public Fog() { this(new com.nttdocomo.ui.graphics3d.Fog()); }
    Fog(com.nttdocomo.ui.graphics3d.Fog delegate) { super(delegate); }
    private com.nttdocomo.ui.graphics3d.Fog fog() { return (com.nttdocomo.ui.graphics3d.Fog) delegate; }
    public void setMode(int mode) { fog().setMode(mode); }
    public void setLinear(float near, float far) { fog().setLinear(near, far); }
    public void setDensity(float density) { fog().setDensity(density); }
    public void setColor(int color) { fog().setColor(color); }
}
