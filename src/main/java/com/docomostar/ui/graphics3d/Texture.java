package com.docomostar.ui.graphics3d;

public class Texture extends Object3D {
    public static final int MODULATE = 0;
    public static final int REPLACE = 1;
    Texture(com.nttdocomo.ui.graphics3d.Texture delegate) { super(delegate); }
    private com.nttdocomo.ui.graphics3d.Texture texture() { return (com.nttdocomo.ui.graphics3d.Texture) delegate; }
    public void setBlendMode(int mode) { texture().setBlendMode(mode); }
    public void setEnvironmentMapEnabled(boolean enabled) { texture().setEnvironmentMapEnabled(enabled); }
    public void setEnvironmentMapTexture(Texture texture) {
        texture().setEnvironmentMapTexture((com.nttdocomo.ui.graphics3d.Texture) unwrap(texture));
    }
}
