package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.UIException;
import com.nttdocomo.ui.util3d.Transform;
import com.nttdocomo.ui.util3d.Vector3D;

/**
 * Tests whether volumes are inside a configured view volume.
 */
public class ViewVolume {
    private boolean perspective;
    private float near;
    private float far;
    private float width;
    private float height;
    private final Transform transform = new Transform();
    private boolean configured;

    /**
     * Creates a view Volume instance.
     */
    public ViewVolume() {
    }

    /**
     * Sets parallel View.
     */
    public void setParallelView(int width, int height) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("size");
        }
        perspective = false;
        near = 0f;
        far = 32767f;
        this.width = width;
        this.height = height;
        configured = true;
    }

    /**
     * Sets perspective View.
     */
    public void setPerspectiveView(float near, float far, int width, int height) {
        if (!(0f < near && near < far && far < 32768f) || width <= 0 || height <= 0) {
            throw new IllegalArgumentException();
        }
        perspective = true;
        this.near = near;
        this.far = far;
        this.width = width;
        this.height = height;
        configured = true;
    }

    /**
     * Sets perspective View.
     */
    public void setPerspectiveView(float near, float far, float angle) {
        if (!(0f < near && near < far && far < 32768f) || !(0f < angle && angle < 180f)) {
            throw new IllegalArgumentException();
        }
        perspective = true;
        this.near = near;
        this.far = far;
        float size = (float) (2d * near * java.lang.Math.tan(java.lang.Math.toRadians(angle) * 0.5d));
        this.width = size;
        this.height = size;
        configured = true;
    }

    /**
     * Returns whether viewable.
     */
    public boolean isViewable(BoundingVolume bv) {
        if (bv == null) {
            throw new NullPointerException("bv");
        }
        ensureConfigured();
        Vector3D center = bv.getCenter(true);
        Vector3D viewCenter = new Vector3D();
        transform.transVector(center, viewCenter);
        float radius = bv.getEffectiveRadius(new Vector3D(0f, 0f, 1f));
        return isViewable(viewCenter, radius);
    }

    /**
     * Returns whether viewable.
     */
    public boolean isViewable(BVFigure bvFig) {
        if (bvFig == null) {
            throw new NullPointerException("bvFig");
        }
        ensureConfigured();
        for (BoundingVolume bv : bvFig.volumes().values()) {
            if (bv != null && isViewable(bv)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sets transform.
     */
    public void setTransform(Transform trans) {
        if (trans == null) {
            throw new NullPointerException("trans");
        }
        transform.set(trans);
    }

    private boolean isViewable(Vector3D center, float radius) {
        float z = center.getZ();
        if (z + radius < near || z - radius > far) {
            return false;
        }
        float halfWidth;
        float halfHeight;
        if (perspective) {
            float scale = java.lang.Math.max(z, near) / near;
            halfWidth = (width * scale) * 0.5f;
            halfHeight = (height * scale) * 0.5f;
        } else {
            halfWidth = width * 0.5f;
            halfHeight = height * 0.5f;
        }
        return java.lang.Math.abs(center.getX()) <= halfWidth + radius
                && java.lang.Math.abs(center.getY()) <= halfHeight + radius;
    }

    private void ensureConfigured() {
        if (!configured) {
            throw new UIException(UIException.ILLEGAL_STATE);
        }
    }
}
