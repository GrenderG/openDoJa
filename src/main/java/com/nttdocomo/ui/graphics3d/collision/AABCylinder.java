package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.util3d.Transform;

/**
 * Defines an axis-aligned cylinder.
 */
public class AABCylinder extends Cylinder implements AxisAlignedBV {
    /**
     * Creates a a A B Cylinder instance.
     */
    public AABCylinder(float radius, float height) {
        super(radius, height);
    }

    /**
     * Gets radius.
     */
    public float getRadius(boolean transformed) {
        return transformed ? getRadius() * getScale() : getRadius();
    }

    /**
     * Gets height.
     */
    public float getHeight(boolean transformed) {
        return transformed ? getHeight() * getScale() : getHeight();
    }

    /**
     * Gets mesh Transform.
     */
    @Override
    public Transform getMeshTransform(Transform transform) {
        return super.getMeshTransform(transform);
    }

    /**
     * Gets effective Radius.
     */
    @Override
    public float getEffectiveRadius(com.nttdocomo.ui.util3d.Vector3D direction) {
        return super.getEffectiveRadius(direction);
    }

    /**
     * Creates mesh.
     */
    @Override
    public void createMesh(int slice, int stack, float scale) {
        super.createMesh(slice, stack, scale);
    }

    /**
     * Gets mesh.
     */
    @Override
    public com.nttdocomo.ui.graphics3d.Primitive getMesh() {
        return super.getMesh();
    }
}
