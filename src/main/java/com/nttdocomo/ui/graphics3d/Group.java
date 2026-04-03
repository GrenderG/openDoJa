package com.nttdocomo.ui.graphics3d;

import com.nttdocomo.ui.util3d.Transform;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a drawable 3D group.
 */
public class Group extends DrawableObject3D {
    private final List<Object3D> elements = new ArrayList<>();
    private final Transform transform = new Transform();

    /**
     * Creates an empty group.
     */
    public Group() {
        super(TYPE_GROUP);
    }

    /**
     * Disposes this object and releases its resources.
     */
    @Override
    public void dispose() {
        elements.clear();
        super.dispose();
    }

    /**
     * Returns the number of elements held by this group.
     *
     * @return the number of elements
     */
    public int getNumElements() {
        return elements.size();
    }

    /**
     * Returns the element at the specified index.
     *
     * @param index the element index
     * @return the element
     */
    public Object3D getElement(int index) {
        return elements.get(index);
    }

    /**
     * Removes the element at the specified index.
     *
     * @param index the element index
     */
    public void removeElement(int index) {
        elements.remove(index);
    }

    /**
     * Adds an element to the group.
     *
     * @param object the object to add
     */
    public void addElement(Object3D object) {
        if (object == null) {
            throw new NullPointerException("object");
        }
        elements.add(object);
    }

    /**
     * Sets the group transform.
     *
     * @param transform the transform to set
     */
    public void setTransform(Transform transform) {
        if (transform == null) {
            throw new NullPointerException("transform");
        }
        this.transform.set(transform);
    }

    /**
     * Copies the group transform into the supplied object.
     *
     * @param transform the destination transform
     */
    public void getTransform(Transform transform) {
        if (transform == null) {
            throw new NullPointerException("transform");
        }
        transform.set(this.transform);
    }

    /**
     * Sets time.
     */
    @Override
    public void setTime(int time) {
        super.setTime(time);
        for (Object3D element : elements) {
            element.setTime(time);
        }
    }

    /**
     * Sets perspective Correction Enabled.
     */
    @Override
    public void setPerspectiveCorrectionEnabled(boolean enabled) {
        setPerspectiveCorrectionEnabledInternal(enabled);
        for (Object3D element : elements) {
            if (element instanceof DrawableObject3D drawable) {
                drawable.setPerspectiveCorrectionEnabled(enabled);
            }
        }
    }

    /**
     * Sets blend Mode.
     */
    @Override
    public void setBlendMode(int blendMode) {
        setBlendModeInternal(blendMode);
        for (Object3D element : elements) {
            if (element instanceof DrawableObject3D drawable) {
                drawable.setBlendMode(blendMode);
            }
        }
    }

    /**
     * Sets transparency.
     */
    @Override
    public void setTransparency(float transparency) {
        setTransparencyInternal(transparency);
        for (Object3D element : elements) {
            if (element instanceof DrawableObject3D drawable) {
                drawable.setTransparency(transparency);
            }
        }
    }
}
