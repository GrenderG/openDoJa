package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.util3d.Vector3D;

/**
 * Defines an infinite plane.
 */
public class Plane extends AbstractShape {
    private final Vector3D position = new Vector3D();
    private final Vector3D normal = new Vector3D(0f, 1f, 0f);
    private boolean hittingFromBackFaceEnabled;

    /**
     * Creates a plane from a position and normal vector.
     *
     * @param position a point on the plane
     * @param normal the plane normal
     */
    public Plane(Vector3D position, Vector3D normal) {
        super(TYPE_PLANE);
        set(position, normal);
    }

    /**
     * Creates a plane from three points on the plane.
     *
     * @param p0 the first point
     * @param p1 the second point
     * @param p2 the third point
     */
    public Plane(Vector3D p0, Vector3D p1, Vector3D p2) {
        super(TYPE_PLANE);
        set(p0, p1, p2);
    }

    /**
     * Resets this plane from a position and normal vector.
     *
     * @param position a point on the plane
     * @param normal the plane normal
     * @throws NullPointerException if either argument is {@code null}
     */
    public void set(Vector3D position, Vector3D normal) {
        if (position == null || normal == null) {
            throw new NullPointerException("vector");
        }
        this.position.set(position);
        this.normal.set(normal);
        this.normal.normalize();
    }

    /**
     * Resets this plane from three points on the plane.
     *
     * @param p0 the first point
     * @param p1 the second point
     * @param p2 the third point
     * @throws NullPointerException if any argument is {@code null}
     */
    public void set(Vector3D p0, Vector3D p1, Vector3D p2) {
        if (p0 == null || p1 == null || p2 == null) {
            throw new NullPointerException("vector");
        }
        this.position.set(p0);
        Vector3D ab = new Vector3D(p1);
        ab.add(-p0.getX(), -p0.getY(), -p0.getZ());
        Vector3D ac = new Vector3D(p2);
        ac.add(-p0.getX(), -p0.getY(), -p0.getZ());
        this.normal.cross(ab, ac);
        this.normal.normalize();
    }

    /**
     * Gets a point on the plane.
     *
     * @param transformed {@code true} to apply the current transform
     * @return the plane position
     */
    public Vector3D getPosition(boolean transformed) {
        return transformed ? transformPoint(position) : new Vector3D(position);
    }

    /**
     * Gets the plane normal.
     *
     * @param transformed {@code true} to apply the current transform
     * @return the normalized plane normal
     */
    public Vector3D getNormal(boolean transformed) {
        Vector3D out = transformed ? transformDirection(normal) : new Vector3D(normal);
        out.normalize();
        return out;
    }

    /**
     * Enables or disables hit testing from the back face.
     *
     * @param enabled {@code true} to enable back-face hit testing
     */
    public void setHittingFromBackFaceEnabled(boolean enabled) {
        hittingFromBackFaceEnabled = enabled;
    }

    /**
     * Gets whether hit testing from the back face is enabled.
     *
     * @return {@code true} if back-face hit testing is enabled
     */
    public boolean isHittingFromBackFaceEnabled() {
        return hittingFromBackFaceEnabled;
    }

    /**
     * Creates a mesh representation for this plane.
     *
     * @param slice the requested slice count
     * @param stack the requested stack count
     * @param scale the requested mesh scale
     */
    @Override
    public void createMesh(int slice, int stack, float scale) {
        super.createMesh(slice, stack, scale);
    }
}
