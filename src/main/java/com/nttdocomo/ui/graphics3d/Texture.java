package com.nttdocomo.ui.graphics3d;

import opendoja.g3d.SoftwareTexture;

public class Texture extends Object3D {
    public static final int MODULATE = 0;
    public static final int REPLACE = 1;

    private final SoftwareTexture handle;
    private int blendMode = MODULATE;
    private boolean environmentMapEnabled;
    private Texture environmentMapTexture;

    Texture(SoftwareTexture handle) {
        super(TYPE_TEXTURE);
        this.handle = handle;
    }

    public void setBlendMode(int blendMode) {
        this.blendMode = blendMode;
    }

    public void setEnvironmentMapEnabled(boolean enabled) {
        this.environmentMapEnabled = enabled;
    }

    public void setEnvironmentMapTexture(Texture texture) {
        this.environmentMapTexture = texture;
    }

    SoftwareTexture handle() {
        return handle;
    }
}
