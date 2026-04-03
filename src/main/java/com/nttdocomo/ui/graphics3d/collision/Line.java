package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.util3d.Vector3D;

/**
 * Defines a line segment shape.
 */
public class Line extends AbstractShape {
    private final Vector3D start = new Vector3D();
    private final Vector3D end = new Vector3D();

    /**
     * Creates a line instance.
     */
    public Line(Vector3D start, Vector3D end) {
        super(TYPE_LINE);
        if (start == null || end == null) {
            throw new NullPointerException("point");
        }
        this.start.set(start);
        this.end.set(end);
    }

    /**
     * Sets set.
     */
    public void set(Vector3D start, Vector3D end) {
        if (start == null || end == null) {
            throw new NullPointerException("point");
        }
        this.start.set(start);
        this.end.set(end);
    }

    /**
     * Gets start Position.
     */
    public Vector3D getStartPosition(boolean transformed) {
        return transformed ? transformPoint(start) : new Vector3D(start);
    }

    /**
     * Gets end Position.
     */
    public Vector3D getEndPosition(boolean transformed) {
        return transformed ? transformPoint(end) : new Vector3D(end);
    }

    /**
     * Creates mesh.
     */
    @Override
    public void createMesh(int slice, int stack, float scale) {
        super.createMesh(slice, stack, scale);
    }
}
