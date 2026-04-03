package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.graphics3d.Primitive;
import com.nttdocomo.ui.util3d.Vector3D;
import opendoja.g3d.FixedPoint;

/**
 * Defines a cylindrical bounding volume.
 */
public class Cylinder extends AbstractBV {
    private float radius;
    private float height;

    /**
     * Creates a cylinder instance.
     */
    public Cylinder(float radius, float height) {
        super(TYPE_CYLINDER);
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
        float perp = (float) java.lang.Math.sqrt(java.lang.Math.max(0f, len * len - axial * axial));
        return ((height * getScale() * 0.5f) * axial + (radius * getScale()) * perp) / len;
    }

    /**
     * Creates mesh Body.
     */
    protected Primitive createMeshBody() {
        Primitive primitive = new Primitive(Primitive.PRIMITIVE_POINTS, Primitive.COLOR_NONE, 1);
        primitive.getVertexArray()[0] = 0;
        primitive.getVertexArray()[1] = 0;
        primitive.getVertexArray()[2] = 0;
        meshScaleX = radius * FixedPoint.ONE;
        meshScaleY = height * FixedPoint.ONE;
        meshScaleZ = radius * FixedPoint.ONE;
        return primitive;
    }

    /**
     * Creates mesh.
     */
    @Override
    public void createMesh(int slice, int stack, float scale) {
        super.createMesh(slice, stack, scale);
    }
}
