package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.graphics3d.Primitive;
import com.nttdocomo.ui.util3d.Transform;
import com.nttdocomo.ui.util3d.Vector3D;

/**
 * Base implementation of {@link Shape}.
 */
public abstract class AbstractShape implements Shape {
    private final int shapeType;
    private final Transform transform = new Transform();
    private Object attribute;
    private Primitive mesh;
    /**
     * X-axis scale used when building this shape's mesh.
     */
    protected float meshScaleX = 1f;
    /**
     * Y-axis scale used when building this shape's mesh.
     */
    protected float meshScaleY = 1f;
    /**
     * Z-axis scale used when building this shape's mesh.
     */
    protected float meshScaleZ = 1f;

    /**
     * Initializes a shape with the specified shape type.
     *
     * @param shapeType the shape type
     */
    protected AbstractShape(int shapeType) {
        this.shapeType = shapeType;
    }

    /**
     * Gets the shape type.
     *
     * @return the shape type
     */
    @Override
    public final int getShapeType() {
        return shapeType;
    }

    /**
     * Gets the mesh associated with this shape.
     *
     * @return the mesh, or {@code null} if none is assigned
     */
    @Override
    public Primitive getMesh() {
        return mesh;
    }

    /**
     * Gets the transform applied to this shape's mesh.
     *
     * @param destination the destination transform, or {@code null} to create a new one
     * @return the mesh transform
     */
    @Override
    public Transform getMeshTransform(Transform destination) {
        return getTransform(TRANS_SHAPE_WORLD, destination);
    }

    /**
     * Deletes the mesh associated with this shape.
     */
    @Override
    public final void deleteMesh() {
        mesh = null;
    }

    /**
     * Sets the transform for this shape.
     *
     * @param transform the shape transform
     * @throws NullPointerException if {@code transform} is {@code null}
     */
    @Override
    public final void setTransform(Transform transform) {
        if (transform == null) {
            throw new NullPointerException("transform");
        }
        this.transform.set(transform);
    }

    /**
     * Gets the requested transform for this shape.
     *
     * @param type the transform type
     * @param destination the destination transform, or {@code null} to create a new one
     * @return the transform
     */
    @Override
    public Transform getTransform(int type, Transform destination) {
        Transform out = destination == null ? new Transform() : destination;
        out.set(transform);
        return out;
    }

    /**
     * Gets the average scale factor derived from the shape transform.
     *
     * @return the average scale factor
     */
    @Override
    public float getScale() {
        float sx = scaleOfColumn(0, 1, 2);
        float sy = scaleOfColumn(4, 5, 6);
        float sz = scaleOfColumn(8, 9, 10);
        return (sx + sy + sz) / 3f;
    }

    /**
     * Sets an arbitrary attribute object on this shape.
     *
     * @param attribute the attribute object
     */
    @Override
    public final void setAttribute(Object attribute) {
        this.attribute = attribute;
    }

    /**
     * Gets the arbitrary attribute object associated with this shape.
     *
     * @return the attribute object, or {@code null}
     */
    @Override
    public final Object getAttribute() {
        return attribute;
    }

    /**
     * Creates a mesh representation for this shape.
     *
     * @param slice the requested slice count
     * @param stack the requested stack count
     * @param scale the requested mesh scale
     */
    @Override
    public void createMesh(int slice, int stack, float scale) {
        meshScaleX = scale;
        meshScaleY = scale;
        meshScaleZ = scale;
        mesh = createMeshBody();
    }

    /**
     * Creates the mesh body used for this shape.
     *
     * @return the created mesh body
     */
    protected Primitive createMeshBody() {
        return new Primitive(Primitive.PRIMITIVE_POINTS, Primitive.NORMAL_NONE, 0);
    }

    /**
     * Gets the base transform stored by this shape.
     *
     * @return the base transform
     */
    protected Transform baseTransform() {
        return transform;
    }

    /**
     * Applies the shape transform to a point.
     *
     * @param point the source point
     * @return the transformed point
     */
    protected Vector3D transformPoint(Vector3D point) {
        Vector3D out = new Vector3D();
        transform.transVector(point, out);
        return out;
    }

    /**
     * Applies the rotational part of the shape transform to a direction
     * vector.
     *
     * @param direction the source direction
     * @return the transformed direction
     */
    protected Vector3D transformDirection(Vector3D direction) {
        Vector3D out = new Vector3D(
                baseTransform().get(0) * direction.getX() + baseTransform().get(1) * direction.getY() + baseTransform().get(2) * direction.getZ(),
                baseTransform().get(4) * direction.getX() + baseTransform().get(5) * direction.getY() + baseTransform().get(6) * direction.getZ(),
                baseTransform().get(8) * direction.getX() + baseTransform().get(9) * direction.getY() + baseTransform().get(10) * direction.getZ()
        );
        return out;
    }

    private float scaleOfColumn(int a, int b, int c) {
        float x = transform.get(a);
        float y = transform.get(b);
        float z = transform.get(c);
        return (float) java.lang.Math.sqrt(x * x + y * y + z * z);
    }
}
