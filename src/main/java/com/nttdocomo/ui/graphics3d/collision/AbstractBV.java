package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.util3d.Transform;
import com.nttdocomo.ui.util3d.Vector3D;

/**
 * Base implementation of {@link BoundingVolume}.
 */
public abstract class AbstractBV extends AbstractShape implements BoundingVolume {
    private final Vector3D center = new Vector3D();
    private int rotate;
    private boolean hittingFromBackFaceEnabled;

    /**
     * Initializes a bounding volume with the specified shape type.
     *
     * @param shapeType the shape type
     */
    protected AbstractBV(int shapeType) {
        super(shapeType);
    }

    /**
     * Sets the center position.
     *
     * @param center the center position
     * @throws NullPointerException if {@code center} is {@code null}
     */
    @Override
    public final void setCenter(Vector3D center) {
        if (center == null) {
            throw new NullPointerException("center");
        }
        this.center.set(center);
    }

    /**
     * Gets the center position.
     *
     * @param transformed {@code true} to apply the current transform
     * @return the center position
     */
    @Override
    public final Vector3D getCenter(boolean transformed) {
        return transformed ? transformPoint(center) : new Vector3D(center);
    }

    /**
     * Sets the axis-rotation selector used by this bounding volume.
     *
     * @param rotate the rotation selector
     */
    @Override
    public final void setRotate(int rotate) {
        if (rotate < ROTATE_NONE || rotate > ROTATE_YX) {
            throw new IllegalArgumentException("rotate");
        }
        this.rotate = rotate;
    }

    /**
     * Gets the axis-rotation selector used by this bounding volume.
     *
     * @return the rotation selector
     */
    @Override
    public final int getRotate() {
        return rotate;
    }

    /**
     * Gets the mesh transform, including the center offset.
     *
     * @param destination the destination transform, or {@code null} to create a new one
     * @return the mesh transform
     */
    @Override
    public Transform getMeshTransform(Transform destination) {
        Transform out = super.getMeshTransform(destination);
        out.translate(center);
        return out;
    }

    /**
     * Enables or disables hit testing from the back face.
     *
     * @param enabled {@code true} to enable back-face hit testing
     */
    @Override
    public final void setHittingFromBackFaceEnabled(boolean enabled) {
        hittingFromBackFaceEnabled = enabled;
    }

    /**
     * Gets whether hit testing from the back face is enabled.
     *
     * @return {@code true} if back-face hit testing is enabled
     */
    @Override
    public final boolean isHittingFromBackFaceEnabled() {
        return hittingFromBackFaceEnabled;
    }

    /**
     * Gets the unit axis vector that corresponds to the current rotate mode.
     *
     * @return the axis vector
     */
    protected Vector3D axisVector() {
        return switch (rotate) {
            case ROTATE_X -> new Vector3D(1f, 0f, 0f);
            case ROTATE_Z -> new Vector3D(0f, 0f, 1f);
            default -> new Vector3D(0f, 1f, 0f);
        };
    }
}
