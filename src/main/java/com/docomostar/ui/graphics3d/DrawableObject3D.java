package com.docomostar.ui.graphics3d;

import com.docomostar.ui.util3d.Transform;

public abstract class DrawableObject3D extends Object3D {
    public static final int BLEND_NORMAL = 0;
    public static final int BLEND_ALPHA = 32;
    public static final int BLEND_ADD = 64;
    DrawableObject3D(com.nttdocomo.ui.graphics3d.DrawableObject3D delegate) { super(delegate); }
    private com.nttdocomo.ui.graphics3d.DrawableObject3D drawable() {
        return (com.nttdocomo.ui.graphics3d.DrawableObject3D) delegate;
    }
    public boolean isCross(DrawableObject3D other, Transform transform, Transform otherTransform) {
        return drawable().isCross((com.nttdocomo.ui.graphics3d.DrawableObject3D) unwrap(other), transform, otherTransform);
    }
    public void setPerspectiveCorrectionEnabled(boolean enabled) { drawable().setPerspectiveCorrectionEnabled(enabled); }
    public void setBlendMode(int mode) { drawable().setBlendMode(mode); }
    public void setTransparency(float transparency) { drawable().setTransparency(transparency); }
}
