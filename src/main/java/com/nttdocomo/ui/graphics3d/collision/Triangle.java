package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.util3d.Vector3D;

/**
 * Defines a triangle shape.
 */
public class Triangle extends AbstractShape {
    private final Vector3D[] vertices = {new Vector3D(), new Vector3D(), new Vector3D()};
    private boolean hittingFromBackFaceEnabled;

    /**
     * Creates a triangle instance.
     */
    public Triangle(Vector3D v0, Vector3D v1, Vector3D v2) {
        super(TYPE_TRIANGLE);
        set(v0, v1, v2);
    }

    /**
     * Sets set.
     */
    public void set(Vector3D v0, Vector3D v1, Vector3D v2) {
        if (v0 == null || v1 == null || v2 == null) {
            throw new NullPointerException("vertex");
        }
        vertices[0].set(v0);
        vertices[1].set(v1);
        vertices[2].set(v2);
    }

    /**
     * Gets vertices.
     */
    public Vector3D[] getVertices(boolean transformed) {
        Vector3D[] result = new Vector3D[3];
        for (int i = 0; i < 3; i++) {
            result[i] = transformed ? transformPoint(vertices[i]) : new Vector3D(vertices[i]);
        }
        return result;
    }

    /**
     * Gets normal.
     */
    public Vector3D getNormal(boolean transformed) {
        Vector3D[] values = getVertices(transformed);
        Vector3D ab = new Vector3D(values[1]);
        ab.add(-values[0].getX(), -values[0].getY(), -values[0].getZ());
        Vector3D ac = new Vector3D(values[2]);
        ac.add(-values[0].getX(), -values[0].getY(), -values[0].getZ());
        Vector3D normal = new Vector3D();
        normal.cross(ab, ac);
        normal.normalize();
        return normal;
    }

    /**
     * Sets hitting From Back Face Enabled.
     */
    public void setHittingFromBackFaceEnabled(boolean enabled) {
        hittingFromBackFaceEnabled = enabled;
    }

    /**
     * Returns whether hitting From Back Face Enabled.
     */
    public boolean isHittingFromBackFaceEnabled() {
        return hittingFromBackFaceEnabled;
    }

    /**
     * Creates mesh.
     */
    @Override
    public void createMesh(int slice, int stack, float scale) {
        super.createMesh(slice, stack, scale);
    }
}
