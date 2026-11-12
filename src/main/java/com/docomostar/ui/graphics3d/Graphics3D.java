package com.docomostar.ui.graphics3d;

import com.docomostar.ui.util3d.Transform;

public interface Graphics3D {
    void setClipRectFor3D(int x, int y, int width, int height);
    void setParallelView(int width, int height);
    void setPerspectiveView(float near, float far, int width, int height);
    void setPerspectiveView(float near, float far, float angle);
    void flushBuffer();
    void setTransform(Transform transform);
    void addLight(Light light, Transform transform);
    void resetLights();
    void setFog(Fog fog);
    void renderObject3D(DrawableObject3D object, Transform transform);
}
