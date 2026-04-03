package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.util3d.Vector3D;

/**
 * Holds information about an intersection point.
 */
public class IntersectionAttribute {
    /**
     * Creates an empty intersection-attribute object.
     */
    public IntersectionAttribute() {
    }

    /**
     * Constant for distance.
     */
    public float distance;
    /**
     * Constant for texture U V.
     */
    public float[] textureUV;
    /**
     * Constant for normal.
     */
    public Vector3D normal;
    /**
     * Constant for color R G B A.
     */
    public int colorRGBA;
    /**
     * Constant for blend Mode.
     */
    public int blendMode;
}
