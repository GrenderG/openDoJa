package com.nttdocomo.ui.graphics3d;

import com.nttdocomo.ui.util3d.Transform;

/**
 * Abstract base class for 3D objects that can be rendered and can
 * participate in collision testing.
 */
public abstract class DrawableObject3D extends Object3D {
    /**
     * Blend mode for normal drawing with no blending.
     */
    public static final int BLEND_NORMAL = 0;
    /**
     * Blend mode for alpha blending.
     */
    public static final int BLEND_ALPHA = 32;
    /**
     * Blend mode for additive blending.
     */
    public static final int BLEND_ADD = 64;

    private boolean perspectiveCorrectionEnabled;
    private int blendMode = BLEND_NORMAL;
    private float transparency = 1f;

    /**
     * Initializes a drawable 3D object with the specified object type.
     *
     * @param type the object type
     */
    protected DrawableObject3D(int type) {
        super(type);
    }

    /**
     * Tests this drawable object against another drawable object and returns
     * whether any polygons intersect.
     *
     * @param other the other drawable object
     * @param thisTransform the transform applied to this object
     * @param otherTransform the transform applied to the other object
     * @return {@code true} if the objects intersect, otherwise {@code false}
     */
    public boolean isCross(DrawableObject3D other, Transform thisTransform, Transform otherTransform) {
        return _DrawableCollisionSupport.isCross(this, other, thisTransform, otherTransform);
    }

    /**
     * Enables or disables perspective correction.
     *
     * @param enabled {@code true} to enable perspective correction
     */
    public abstract void setPerspectiveCorrectionEnabled(boolean enabled);

    /**
     * Sets the primitive blend mode.
     *
     * @param blendMode the blend mode
     */
    public abstract void setBlendMode(int blendMode);

    /**
     * Sets the transparency percentage.
     *
     * @param transparency the transparency value
     */
    public abstract void setTransparency(float transparency);

    final void setPerspectiveCorrectionEnabledInternal(boolean enabled) {
        this.perspectiveCorrectionEnabled = enabled;
    }

    final void setBlendModeInternal(int blendMode) {
        this.blendMode = blendMode;
    }

    final void setTransparencyInternal(float transparency) {
        float normalized = transparency;
        if (normalized > 1f) {
            // Handset content commonly drives UI 3D fades with 0..100 percentage values.
            normalized /= 100f;
        }
        this.transparency = java.lang.Math.max(0f, java.lang.Math.min(1f, normalized));
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
