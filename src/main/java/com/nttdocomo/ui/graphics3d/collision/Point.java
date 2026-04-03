package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.util3d.Vector3D;

/**
 * Defines a point shape.
 */
public class Point extends AbstractShape {
    private final Vector3D position = new Vector3D();

    /**
     * Creates a point instance.
     */
    public Point(Vector3D position) {
        super(TYPE_POINT);
        set(position);
    }

    /**
     * Sets set.
     */
    public void set(Vector3D position) {
        if (position == null) {
            throw new NullPointerException("position");
        }
        this.position.set(position);
    }

    /**
     * Gets position.
     */
    public Vector3D getPosition(boolean transformed) {
        return transformed ? transformPoint(position) : new Vector3D(position);
    }

    /**
     * Creates mesh.
     */
    @Override
    public void createMesh(int slice, int stack, float scale) {
        super.createMesh(slice, stack, scale);
    }
}
