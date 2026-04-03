package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.graphics3d.Primitive;
import opendoja.g3d.FixedPoint;

/**
 * Defines a spherical bounding volume.
 */
public class Sphere extends AbstractBV {
    private float radius;

    /**
     * Creates a sphere instance.
     */
    public Sphere(float radius) {
        super(TYPE_SPHERE);
        set(radius);
    }

    /**
     * Sets set.
     */
    public void set(float radius) {
        this.radius = java.lang.Math.max(0f, radius);
    }

    /**
     * Gets radius.
     */
    public float getRadius() {
        return radius;
    }

    /**
     * Gets effective Radius.
     */
    @Override
    public float getEffectiveRadius(com.nttdocomo.ui.util3d.Vector3D direction) {
        return radius * getScale();
    }

    /**
     * Creates mesh Body.
     */
    public Primitive createMeshBody() {
        Primitive primitive = new Primitive(Primitive.PRIMITIVE_POINTS, Primitive.COLOR_NONE, 1);
        primitive.getVertexArray()[0] = 0;
        primitive.getVertexArray()[1] = 0;
        primitive.getVertexArray()[2] = 0;
        meshScaleX = radius * FixedPoint.ONE;
        meshScaleY = radius * FixedPoint.ONE;
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
