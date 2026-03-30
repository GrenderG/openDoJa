package com.nttdocomo.ui.graphics3d;

import com.nttdocomo.ui.util3d.Transform;
import opendoja.g3d.MascotFigure;
import opendoja.g3d.MbacModel;
import opendoja.g3d.Software3DContext;

import java.util.ArrayList;
import java.util.List;

final class _DrawableCollisionSupport {
    private static final float FIGURE_VERTEX_SCALE =
            opendoja.host.OpenDoJaLaunchArgs.getFloat(opendoja.host.OpenDoJaLaunchArgs.UI_FIGURE_VERTEX_SCALE);
    private static final float EPSILON = 1.0e-5f;

    private _DrawableCollisionSupport() {
    }

    static boolean isCross(DrawableObject3D self, DrawableObject3D other, Transform selfTransform, Transform otherTransform) {
        if (other == null) {
            throw new NullPointerException("obj");
        }
        List<TriangleData> left = collectTriangles(self, selfTransform);
        if (left.isEmpty()) {
            return false;
        }
        List<TriangleData> right = collectTriangles(other, otherTransform);
        if (right.isEmpty()) {
            return false;
        }
        for (TriangleData a : left) {
            for (TriangleData b : right) {
                if (!overlaps(a.bounds, b.bounds)) {
                    continue;
                }
                if (trianglesCross(a.a, a.b, a.c, b.a, b.b, b.c)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<TriangleData> collectTriangles(DrawableObject3D object, Transform externalTransform) {
        List<TriangleData> triangles = new ArrayList<>();
        collectTriangles(object, externalTransform == null ? Software3DContext.identity() : matrixOf(externalTransform), triangles);
        return triangles;
    }

    private static void collectTriangles(DrawableObject3D object, float[] transform, List<TriangleData> out) {
        if (object == null || object.getType() == Object3D.TYPE_NONE) {
            return;
        }
        if (object instanceof Group group) {
            Transform groupTransform = new Transform();
            group.getTransform(groupTransform);
            float[] combined = Software3DContext.multiply(transform, matrixOf(groupTransform));
            for (int i = 0; i < group.getNumElements(); i++) {
                Object3D element = group.getElement(i);
                if (element instanceof DrawableObject3D drawable) {
                    collectTriangles(drawable, combined, out);
                }
            }
            return;
        }
        if (object instanceof Primitive primitive) {
            collectPrimitiveTriangles(primitive, transform, out);
            return;
        }
        if (object instanceof Figure figure) {
            collectFigureTriangles(figure, transform, out);
        }
    }

    private static void collectPrimitiveTriangles(Primitive primitive, float[] transform, List<TriangleData> out) {
        int primitiveType = primitive.getPrimitiveType();
        if (primitiveType != Primitive.PRIMITIVE_TRIANGLES && primitiveType != Primitive.PRIMITIVE_QUADS) {
            return;
        }
        int verticesPerPrimitive = primitiveType == Primitive.PRIMITIVE_TRIANGLES ? 3 : 4;
        int[] vertices = primitive.getVertexArray();
        for (int primitiveIndex = 0; primitiveIndex < primitive.size(); primitiveIndex++) {
            int base = primitiveIndex * verticesPerPrimitive * 3;
            if (base + verticesPerPrimitive * 3 > vertices.length) {
                break;
            }
            Point a = transform(transform, vertices[base], vertices[base + 1], vertices[base + 2]);
            Point b = transform(transform, vertices[base + 3], vertices[base + 4], vertices[base + 5]);
            Point c = transform(transform, vertices[base + 6], vertices[base + 7], vertices[base + 8]);
            out.add(new TriangleData(a, b, c));
            if (primitiveType == Primitive.PRIMITIVE_QUADS) {
                Point d = transform(transform, vertices[base + 9], vertices[base + 10], vertices[base + 11]);
                out.add(new TriangleData(a, c, d));
            }
        }
    }

    private static void collectFigureTriangles(Figure figure, float[] transform, List<TriangleData> out) {
        MascotFigure handle = figure.handle();
        if (handle == null || handle.model() == null) {
            return;
        }
        MbacModel model = handle.model();
        float[] vertices = handle.vertices();
        int patternMask = handle.patternMask();
        for (MbacModel.Polygon polygon : model.polygons()) {
            int[] indices = polygon.indices();
            if (indices == null || (indices.length != 3 && indices.length != 4)) {
                continue;
            }
            int polygonPattern = polygon.patternMask();
            if (polygonPattern != 0 && (polygonPattern & patternMask) != polygonPattern) {
                continue;
            }
            Point a = figurePoint(vertices, indices[0], transform);
            Point b = figurePoint(vertices, indices[1], transform);
            Point c = figurePoint(vertices, indices[2], transform);
            out.add(new TriangleData(a, b, c));
            if (indices.length == 4) {
                Point d = figurePoint(vertices, indices[3], transform);
                out.add(new TriangleData(a, c, d));
            }
        }
    }

    private static Point figurePoint(float[] vertices, int vertexIndex, float[] transform) {
        int base = vertexIndex * 3;
        return transform(transform,
                vertices[base] * FIGURE_VERTEX_SCALE,
                vertices[base + 1] * FIGURE_VERTEX_SCALE,
                vertices[base + 2] * FIGURE_VERTEX_SCALE);
    }

    private static Point transform(float[] matrix, float x, float y, float z) {
        return new Point(
                matrix[0] * x + matrix[1] * y + matrix[2] * z + matrix[3],
                matrix[4] * x + matrix[5] * y + matrix[6] * z + matrix[7],
                matrix[8] * x + matrix[9] * y + matrix[10] * z + matrix[11]
        );
    }

    private static float[] matrixOf(Transform transform) {
        float[] matrix = new float[16];
        for (int i = 0; i < 16; i++) {
            matrix[i] = transform.get(i);
        }
        return matrix;
    }

    private static boolean trianglesCross(Point a1, Point a2, Point a3, Point b1, Point b2, Point b3) {
        if (edgeHitsTriangle(a1, a2, b1, b2, b3)
                || edgeHitsTriangle(a2, a3, b1, b2, b3)
                || edgeHitsTriangle(a3, a1, b1, b2, b3)
                || edgeHitsTriangle(b1, b2, a1, a2, a3)
                || edgeHitsTriangle(b2, b3, a1, a2, a3)
                || edgeHitsTriangle(b3, b1, a1, a2, a3)) {
            return true;
        }
        return coplanarEdgeIntersection(a1, a2, a3, b1, b2, b3);
    }

    private static boolean edgeHitsTriangle(Point start, Point end, Point t0, Point t1, Point t2) {
        Point direction = subtract(end, start);
        Point edge1 = subtract(t1, t0);
        Point edge2 = subtract(t2, t0);
        Point pvec = cross(direction, edge2);
        float det = dot(edge1, pvec);
        if (java.lang.Math.abs(det) < EPSILON) {
            return false;
        }
        float invDet = 1f / det;
        Point tvec = subtract(start, t0);
        float u = dot(tvec, pvec) * invDet;
        if (u < -EPSILON || u > 1f + EPSILON) {
            return false;
        }
        Point qvec = cross(tvec, edge1);
        float v = dot(direction, qvec) * invDet;
        if (v < -EPSILON || u + v > 1f + EPSILON) {
            return false;
        }
        float t = dot(edge2, qvec) * invDet;
        return t >= -EPSILON && t <= 1f + EPSILON;
    }

    private static boolean coplanarEdgeIntersection(Point a1, Point a2, Point a3, Point b1, Point b2, Point b3) {
        Point normal = cross(subtract(a2, a1), subtract(a3, a1));
        if (length(normal) < EPSILON) {
            return false;
        }
        if (java.lang.Math.abs(dot(normal, subtract(b1, a1))) > EPSILON
                || java.lang.Math.abs(dot(normal, subtract(b2, a1))) > EPSILON
                || java.lang.Math.abs(dot(normal, subtract(b3, a1))) > EPSILON) {
            return false;
        }
        int axis = dominantAxis(normal);
        Point2D[] first = project(axis, a1, a2, a3);
        Point2D[] second = project(axis, b1, b2, b3);
        return segmentsIntersect(first[0], first[1], second[0], second[1])
                || segmentsIntersect(first[1], first[2], second[0], second[1])
                || segmentsIntersect(first[2], first[0], second[0], second[1])
                || segmentsIntersect(first[0], first[1], second[1], second[2])
                || segmentsIntersect(first[1], first[2], second[1], second[2])
                || segmentsIntersect(first[2], first[0], second[1], second[2])
                || segmentsIntersect(first[0], first[1], second[2], second[0])
                || segmentsIntersect(first[1], first[2], second[2], second[0])
                || segmentsIntersect(first[2], first[0], second[2], second[0]);
    }

    private static Point2D[] project(int axis, Point a, Point b, Point c) {
        return switch (axis) {
            case 0 -> new Point2D[]{
                    new Point2D(a.y, a.z), new Point2D(b.y, b.z), new Point2D(c.y, c.z)
            };
            case 1 -> new Point2D[]{
                    new Point2D(a.x, a.z), new Point2D(b.x, b.z), new Point2D(c.x, c.z)
            };
            default -> new Point2D[]{
                    new Point2D(a.x, a.y), new Point2D(b.x, b.y), new Point2D(c.x, c.y)
            };
        };
    }

    private static int dominantAxis(Point normal) {
        float ax = java.lang.Math.abs(normal.x);
        float ay = java.lang.Math.abs(normal.y);
        float az = java.lang.Math.abs(normal.z);
        if (ax >= ay && ax >= az) {
            return 0;
        }
        if (ay >= ax && ay >= az) {
            return 1;
        }
        return 2;
    }

    private static boolean segmentsIntersect(Point2D a, Point2D b, Point2D c, Point2D d) {
        float ab1 = orientation(a, b, c);
        float ab2 = orientation(a, b, d);
        float cd1 = orientation(c, d, a);
        float cd2 = orientation(c, d, b);
        if (java.lang.Math.abs(ab1) < EPSILON && onSegment(a, b, c)) {
            return true;
        }
        if (java.lang.Math.abs(ab2) < EPSILON && onSegment(a, b, d)) {
            return true;
        }
        if (java.lang.Math.abs(cd1) < EPSILON && onSegment(c, d, a)) {
            return true;
        }
        if (java.lang.Math.abs(cd2) < EPSILON && onSegment(c, d, b)) {
            return true;
        }
        return (ab1 > 0f) != (ab2 > 0f) && (cd1 > 0f) != (cd2 > 0f);
    }

    private static float orientation(Point2D a, Point2D b, Point2D c) {
        return (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
    }

    private static boolean onSegment(Point2D a, Point2D b, Point2D p) {
        return p.x >= java.lang.Math.min(a.x, b.x) - EPSILON
                && p.x <= java.lang.Math.max(a.x, b.x) + EPSILON
                && p.y >= java.lang.Math.min(a.y, b.y) - EPSILON
                && p.y <= java.lang.Math.max(a.y, b.y) + EPSILON;
    }

    private static boolean overlaps(Bounds a, Bounds b) {
        return a.minX <= b.maxX + EPSILON && a.maxX + EPSILON >= b.minX
                && a.minY <= b.maxY + EPSILON && a.maxY + EPSILON >= b.minY
                && a.minZ <= b.maxZ + EPSILON && a.maxZ + EPSILON >= b.minZ;
    }

    private static Point subtract(Point left, Point right) {
        return new Point(left.x - right.x, left.y - right.y, left.z - right.z);
    }

    private static Point cross(Point left, Point right) {
        return new Point(
                left.y * right.z - left.z * right.y,
                left.z * right.x - left.x * right.z,
                left.x * right.y - left.y * right.x
        );
    }

    private static float dot(Point left, Point right) {
        return left.x * right.x + left.y * right.y + left.z * right.z;
    }

    private static float length(Point point) {
        return (float) java.lang.Math.sqrt(dot(point, point));
    }

    private record TriangleData(Point a, Point b, Point c, Bounds bounds) {
        private TriangleData(Point a, Point b, Point c) {
            this(a, b, c, Bounds.of(a, b, c));
        }
    }

    private record Bounds(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {
        private static Bounds of(Point a, Point b, Point c) {
            return new Bounds(
                    java.lang.Math.min(a.x, java.lang.Math.min(b.x, c.x)),
                    java.lang.Math.min(a.y, java.lang.Math.min(b.y, c.y)),
                    java.lang.Math.min(a.z, java.lang.Math.min(b.z, c.z)),
                    java.lang.Math.max(a.x, java.lang.Math.max(b.x, c.x)),
                    java.lang.Math.max(a.y, java.lang.Math.max(b.y, c.y)),
                    java.lang.Math.max(a.z, java.lang.Math.max(b.z, c.z))
            );
        }
    }

    private record Point(float x, float y, float z) {
    }

    private record Point2D(float x, float y) {
    }
}
