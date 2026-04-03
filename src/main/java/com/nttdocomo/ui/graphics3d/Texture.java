package com.nttdocomo.ui.graphics3d;

import opendoja.g3d.SoftwareTexture;

/**
 * Defines the texture object used by graphics3d figures and primitives.
 */
public class Texture extends Object3D {
    /**
     * Blend mode that modulates the texture with the primitive color.
     */
    public static final int MODULATE = 0;
    /**
     * Blend mode that replaces the primitive color with the texture color.
     */
    public static final int REPLACE = 1;

    private final SoftwareTexture handle;
    private int blendMode = MODULATE;
    private boolean environmentMapEnabled;
    private Texture environmentMapTexture;

    Texture(SoftwareTexture handle) {
        super(TYPE_TEXTURE);
        this.handle = handle;
    }

    /**
     * Sets the texture blend mode.
     *
     * @param blendMode the blend mode
     */
    public void setBlendMode(int blendMode) {
        this.blendMode = blendMode;
    }

    /**
     * Enables or disables environment mapping for this texture.
     *
     * @param enabled {@code true} to enable environment mapping
     */
    public void setEnvironmentMapEnabled(boolean enabled) {
        this.environmentMapEnabled = enabled;
    }

    /**
     * Sets the texture used for environment mapping.
     *
     * @param texture the environment-map texture
     */
    public void setEnvironmentMapTexture(Texture texture) {
        this.environmentMapTexture = texture;
    }

    SoftwareTexture handle() {
        return handle;
    }
}
