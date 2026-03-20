package com.nttdocomo.ui.graphics3d;

import com.nttdocomo.ui.util3d.Transform;

public abstract class DrawableObject3D extends Object3D {
    public static final int BLEND_NORMAL = 0;
    public static final int BLEND_ALPHA = 32;
    public static final int BLEND_ADD = 64;

    private boolean perspectiveCorrectionEnabled;
    private int blendMode = BLEND_NORMAL;
    private float transparency = 1f;

    protected DrawableObject3D(int type) {
        super(type);
    }

    public boolean isCross(DrawableObject3D other, Transform thisTransform, Transform otherTransform) {
        return false;
    }

    public abstract void setPerspectiveCorrectionEnabled(boolean enabled);

    public abstract void setBlendMode(int blendMode);

    public abstract void setTransparency(float transparency);

    final void setPerspectiveCorrectionEnabledInternal(boolean enabled) {
        this.perspectiveCorrectionEnabled = enabled;
    }

    final void setBlendModeInternal(int blendMode) {
        this.blendMode = blendMode;
    }

    final void setTransparencyInternal(float transparency) {
        this.transparency = java.lang.Math.max(0f, java.lang.Math.min(1f, transparency));
    }

    boolean perspectiveCorrectionEnabledValue() {
        return perspectiveCorrectionEnabled;
    }

    int blendModeValue() {
        return blendMode;
    }

    float transparencyValue() {
        return transparency;
    }
}
