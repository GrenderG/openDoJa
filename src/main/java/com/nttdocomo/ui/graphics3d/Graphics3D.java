package com.nttdocomo.ui.graphics3d;

import com.nttdocomo.ui.util3d.Transform;

/**
 * Defines the graphics3 D type used by the graphics3d API.
 */
public interface Graphics3D {
    void setClipRectFor3D(int x, int y, int width, int height);

    void setParallelView(int width, int height);

    void setPerspectiveView(float a, float b, int c, int d);

    void setPerspectiveView(float a, float b, float c);

    void flushBuffer();

    void setTransform(Transform transform);

    void addLight(Light light, Transform transform);

    void resetLights();

    void setFog(Fog fog);

    void renderObject3D(DrawableObject3D object, Transform transform);
}
