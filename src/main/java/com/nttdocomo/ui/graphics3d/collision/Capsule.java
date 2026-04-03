package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.util3d.Vector3D;

/**
 * Defines a capsule-shaped bounding volume.
 */
public class Capsule extends AbstractBV {
    private float radius;
    private float height;

    /**
     * Creates a capsule instance.
     */
    public Capsule(float radius, float height) {
        super(TYPE_CAPSULE);
        set(radius, height);
    }

    /**
     * Sets set.
     */
    public void set(float radius, float height) {
        this.radius = java.lang.Math.max(0f, radius);
        this.height = java.lang.Math.max(0f, height);
    }

    /**
     * Gets radius.
     */
    public float getRadius() {
        return radius;
    }

    /**
     * Gets height.
     */
    public float getHeight() {
        return height;
    }

    /**
     * Gets effective Radius.
     */
    @Override
    public float getEffectiveRadius(Vector3D direction) {
        if (direction == null) {
            throw new NullPointerException("direction");
        }
        Vector3D axis = axisVector();
        float len = (float) java.lang.Math.sqrt(direction.getX() * direction.getX() + direction.getY() * direction.getY() + direction.getZ() * direction.getZ());
        if (len == 0f) {
            return 0f;
        }
        float axial = java.lang.Math.abs(direction.dot(axis));
        return (radius * getScale()) + ((height * getScale() * 0.5f) * axial / len);
    }

    /**
     * Creates mesh.
     */
    @Override
    public void createMesh(int slice, int stack, float scale) {
        super.createMesh(slice, stack, scale);
    }
}
