package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.graphics3d.Figure;

/**
 * Creates bounding-volume helpers for figures.
 */
public class BVBuilder {
    /**
     * Creates a b V Builder instance.
     */
    public BVBuilder() {
    }

    /**
     * Creates b V Figure.
     */
    public static BVFigure createBVFigure(Figure figure) {
        if (figure == null) {
            throw new NullPointerException("figure");
        }
        return new BVFigure();
    }

    /**
     * Creates b V Figure.
     */
    public static BVFigure createBVFigure(Figure figure, int type, float expand) {
        BVFigure bvFigure = createBVFigure(figure);
        bvFigure.setBV(createShape(type, expand));
        return bvFigure;
    }

    /**
     * Creates bone B V.
     */
    public static BoundingVolume createBoneBV(Figure figure, int boneId, int type, int rotate, float expand, int flags) {
        if (figure == null) {
            throw new NullPointerException("figure");
        }
        BoundingVolume volume = createShape(type, expand);
        volume.setRotate(rotate);
        return volume;
    }

    /**
     * Creates bv.
     */
    public static BoundingVolume createBV(Figure figure, int type, int rotate, float expand) {
        if (figure == null) {
            throw new NullPointerException("figure");
        }
        BoundingVolume volume = createShape(type, expand);
        volume.setRotate(rotate);
        return volume;
    }

    static BoundingVolume createShape(int type, float expand) {
        float size = java.lang.Math.max(0f, expand);
        return switch (type) {
            case Shape.TYPE_BOX -> new Box(new com.nttdocomo.ui.util3d.Vector3D(size, size, size));
            case Shape.TYPE_CYLINDER -> new Cylinder(size, size);
            case Shape.TYPE_CAPSULE -> new Capsule(size, size);
            case Shape.TYPE_AAB_BOX -> new AABBox(new com.nttdocomo.ui.util3d.Vector3D(size, size, size));
            case Shape.TYPE_AAB_CYLINDER -> new AABCylinder(size, size);
            case Shape.TYPE_AAB_CAPSULE -> new AABCapsule(size, size);
            default -> new Sphere(size);
        };
    }
}
