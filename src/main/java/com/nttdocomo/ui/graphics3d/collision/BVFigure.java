package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.graphics3d.ActionTable;
import com.nttdocomo.ui.util3d.Transform;

import java.util.HashMap;
import java.util.Map;

/**
 * Associates bounding volumes with a figure or its bones.
 */
public class BVFigure {
    /**
     * Bone identifier for the whole figure.
     */
    public static final int ID_WHOLE_FIGURE = -1;
    /**
     * Bone identifier used when the target is not a figure.
     */
    public static final int ID_NOT_FIGURE = -2;

    private final Transform transform = new Transform();
    private final Map<Integer, BoundingVolume> volumes = new HashMap<>();
    private final Map<Integer, Boolean> hittingEnabled = new HashMap<>();
    private final int numBones;
    private ActionTable actionTable;
    private int action;
    private int time;

    BVFigure() {
        this(0);
    }

    BVFigure(int numBones) {
        this.numBones = java.lang.Math.max(0, numBones);
    }

    /**
     * Sets the figure transform.
     *
     * @param transform the figure transform
     * @throws NullPointerException if {@code transform} is {@code null}
     */
    public void setTransform(Transform transform) {
        if (transform == null) {
            throw new NullPointerException("transform");
        }
        this.transform.set(transform);
    }

    /**
     * Gets the figure transform.
     *
     * @param destination the destination transform, or {@code null} to create a new one
     * @return the figure transform
     */
    public Transform getTransform(Transform destination) {
        Transform out = destination == null ? new Transform() : destination;
        out.set(transform);
        return out;
    }

    /**
     * Associates an action table and action index with this bounding-volume
     * figure.
     *
     * @param actionTable the action table
     * @param action the action index
     */
    public void setAction(ActionTable actionTable, int action) {
        this.actionTable = actionTable;
        this.action = action;
    }

    /**
     * Sets the current time.
     *
     * @param time the current time
     */
    public void setTime(int time) {
        this.time = time;
    }

    /**
     * Gets the number of bones known to this figure.
     *
     * @return the number of bones
     */
    public int getNumBones() {
        return numBones;
    }

    /**
     * Sets the bounding volume for the whole figure.
     *
     * @param volume the bounding volume, or {@code null} to remove it
     */
    public void setBV(BoundingVolume volume) {
        setBV(volume, ID_WHOLE_FIGURE);
    }

    /**
     * Sets the bounding volume associated with the specified bone.
     *
     * @param volume the bounding volume, or {@code null} to remove it
     * @param boneId the bone identifier
     */
    public void setBV(BoundingVolume volume, int boneId) {
        if (volume == null) {
            volumes.remove(boneId);
        } else {
            volumes.put(boneId, volume);
        }
    }

    /**
     * Gets the bounding volume for the whole figure.
     *
     * @return the bounding volume, or {@code null} if none is set
     */
    public BoundingVolume getBV() {
        return getBV(ID_WHOLE_FIGURE);
    }

    /**
     * Gets the bounding volume associated with the specified bone.
     *
     * @param boneId the bone identifier
     * @return the bounding volume, or {@code null} if none is set
     */
    public BoundingVolume getBV(int boneId) {
        return volumes.get(boneId);
    }

    /**
     * Calculates and installs a whole-figure bounding volume when one is not
     * already present.
     *
     * @param type the bounding-volume type
     * @param expand the expansion value
     */
    public void calculateBV(int type, float expand) {
        if (!volumes.containsKey(ID_WHOLE_FIGURE)) {
            volumes.put(ID_WHOLE_FIGURE, BVBuilder.createShape(type, expand));
        }
    }

    /**
     * Enables or disables hit testing for the specified bone.
     *
     * @param boneId the bone identifier
     * @param enabled {@code true} to enable hit testing
     */
    public void setHittingEnabled(int boneId, boolean enabled) {
        hittingEnabled.put(boneId, enabled);
    }

    /**
     * Gets whether hit testing is enabled for the specified bone.
     *
     * @param boneId the bone identifier
     * @return {@code true} if hit testing is enabled
     */
    public boolean isHittingEnabled(int boneId) {
        return hittingEnabled.getOrDefault(boneId, true);
    }

    Map<Integer, BoundingVolume> volumes() {
        return volumes;
    }
}
