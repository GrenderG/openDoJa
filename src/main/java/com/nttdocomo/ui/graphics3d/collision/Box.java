package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.util3d.Vector3D;

/**
 * Defines a box-shaped bounding volume.
 */
public class Box extends AbstractBV {
    private final Vector3D size = new Vector3D();

    /**
     * Creates a box instance.
     */
    public Box(Vector3D size) {
        super(TYPE_BOX);
        set(size);
    }

    /**
     * Sets set.
     */
    public void set(Vector3D size) {
        if (size == null) {
            throw new NullPointerException("size");
        }
        this.size.set(java.lang.Math.max(0f, size.getX()), java.lang.Math.max(0f, size.getY()), java.lang.Math.max(0f, size.getZ()));
    }

    /**
     * Gets size.
     */
    public Vector3D getSize() {
        return new Vector3D(size);
    }

    /**
     * Gets effective Radius.
     */
    @Override
    public float getEffectiveRadius(Vector3D direction) {
        if (direction == null) {
            throw new NullPointerException("direction");
        }
        float len = (float) java.lang.Math.sqrt(direction.getX() * direction.getX() + direction.getY() * direction.getY() + direction.getZ() * direction.getZ());
        if (len == 0f) {
            return 0f;
        }
        return (java.lang.Math.abs(direction.getX()) * (size.getX() * getScale() * 0.5f)
                + java.lang.Math.abs(direction.getY()) * (size.getY() * getScale() * 0.5f)
                + java.lang.Math.abs(direction.getZ()) * (size.getZ() * getScale() * 0.5f)) / len;
    }

    /**
     * Creates mesh.
     */
    @Override
    public void createMesh(int slice, int stack, float scale) {
        super.createMesh(slice, stack, scale);
    }
}
