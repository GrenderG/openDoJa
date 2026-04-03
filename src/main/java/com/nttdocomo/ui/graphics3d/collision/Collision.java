package com.nttdocomo.ui.graphics3d.collision;

import com.nttdocomo.ui.graphics3d.Figure;
import com.nttdocomo.ui.util3d.Transform;
import com.nttdocomo.ui.util3d.Vector3D;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Performs collision and picking calculations.
 */
public class Collision {
    private CollisionObserver observer;

    /**
     * Creates a collision helper.
     */
    public Collision() {
    }

    /**
     * Sets the observer notified by hit-testing and picking operations.
     *
     * @param observer the collision observer, or {@code null}
     */
    public void setObserver(CollisionObserver observer) {
        this.observer = observer;
    }

    /**
     * Gets the distance between a point and a shape.
     *
     * @param point the point
     * @param shape the shape
     * @return the distance between the point and the shape
     */
    public static float getDistance(Point point, Shape shape) {
        if (point == null || shape == null) {
            throw new NullPointerException();
        }
        return CollisionSupport.distancePointToShape(point.getPosition(true), shape);
    }

    /**
     * Gets the distance between two line segments.
     *
     * @param left the first line segment
     * @param right the second line segment
     * @return the distance between the two line segments
     */
    public static float getDistance(Line left, Line right) {
        if (left == null || right == null) {
            throw new NullPointerException();
        }
        return CollisionSupport.distanceSegmentToSegment(left.getStartPosition(true), left.getEndPosition(true), right.getStartPosition(true), right.getEndPosition(true));
    }

    /**
     * Gets the distance between two spheres.
     *
     * @param left the first sphere
     * @param right the second sphere
     * @return the distance between the two spheres
     */
    public static float getDistance(Sphere left, Sphere right) {
        if (left == null || right == null) {
            throw new NullPointerException();
        }
        float distance = CollisionSupport.distance(left.getCenter(true), right.getCenter(true));
        return distance - (left.getRadius() * left.getScale()) - (right.getRadius() * right.getScale());
    }

    /**
     * Gets the intersection point between a ray and a shape.
     *
     * @param ray the ray
     * @param shape the shape
     * @return the intersection point, or {@code null} if they do not intersect
     */
    public static Vector3D getIntersection(Ray ray, Shape shape) {
        if (ray == null || shape == null) {
            throw new NullPointerException();
        }
        return CollisionSupport.intersection(ray, shape);
    }

    /**
     * Tests whether two shapes hit each other.
     *
     * @param left the first shape
     * @param right the second shape
     * @param notify {@code true} to notify the observer
     * @return {@code true} if the shapes hit each other
     */
    public boolean isHit(Shape left, Shape right, boolean notify) {
        if (left == null || right == null) {
            throw new NullPointerException();
        }
        Vector3D point = CollisionSupport.intersection(left, right);
        boolean hit = point != null || CollisionSupport.distanceBetween(left, right) <= 0f;
        if (hit && notify && observer != null) {
            observer.onHit(left, right, false, point);
        }
        return hit;
    }

    /**
     * Tests whether a shape hits any bounding volume in a figure.
     *
     * @param shape the shape to test
     * @param bvFigure the bounding-volume figure
     * @param notify {@code true} to notify the observer
     * @param ignoreDisabled {@code true} to ignore per-bone disabled flags
     * @return {@code true} if a hit is detected
     */
    public boolean isHit(Shape shape, BVFigure bvFigure, boolean notify, boolean ignoreDisabled) {
        if (shape == null || bvFigure == null) {
            throw new NullPointerException();
        }
        List<BoundingVolume> hits = new ArrayList<>();
        List<Integer> ids = new ArrayList<>();
        List<Vector3D> points = new ArrayList<>();
        for (Map.Entry<Integer, BoundingVolume> entry : bvFigure.volumes().entrySet()) {
            if (!ignoreDisabled && !bvFigure.isHittingEnabled(entry.getKey())) {
                continue;
            }
            Vector3D point = CollisionSupport.intersection(shape, entry.getValue());
            if (point != null || CollisionSupport.distanceBetween(shape, entry.getValue()) <= 0f) {
                hits.add(entry.getValue());
                ids.add(entry.getKey());
                points.add(point);
            }
        }
        if (hits.isEmpty()) {
            return false;
        }
        if (notify && observer != null) {
            observer.onHit(shape,
                    hits.size(),
                    hits.toArray(new BoundingVolume[0]),
                    ids.stream().mapToInt(Integer::intValue).toArray(),
                    new boolean[hits.size()],
                    points.toArray(new Vector3D[0]));
        }
        return true;
    }

    /**
     * Tests whether any bounding volumes in two figures hit each other.
     *
     * @param left the first bounding-volume figure
     * @param right the second bounding-volume figure
     * @param notify {@code true} to notify the observer
     * @param ignoreLeftDisabled {@code true} to ignore disabled flags on the left figure
     * @param ignoreRightDisabled {@code true} to ignore disabled flags on the right figure
     * @return {@code true} if a hit is detected
     */
    public boolean isHit(BVFigure left, BVFigure right, boolean notify, boolean ignoreLeftDisabled, boolean ignoreRightDisabled) {
        if (left == null || right == null) {
            throw new NullPointerException();
        }
        for (Map.Entry<Integer, BoundingVolume> l : left.volumes().entrySet()) {
            if (!ignoreLeftDisabled && !left.isHittingEnabled(l.getKey())) {
                continue;
            }
            for (Map.Entry<Integer, BoundingVolume> r : right.volumes().entrySet()) {
                if (!ignoreRightDisabled && !right.isHittingEnabled(r.getKey())) {
                    continue;
                }
                if (isHit(l.getValue(), r.getValue(), notify)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Tests whether a moving sphere hits a shape.
     *
     * @param shape the shape to test
     * @param sphere the sphere before movement
     * @param velocity the movement vector applied to the sphere center
     * @param notify {@code true} to notify the observer
     * @return {@code true} if a hit is detected
     */
    public boolean isHit(Shape shape, Sphere sphere, Vector3D velocity, boolean notify) {
        if (shape == null || sphere == null || velocity == null) {
            throw new NullPointerException();
        }
        Sphere moved = new Sphere(sphere.getRadius());
        moved.setCenter(sphere.getCenter(false));
        Vector3D movedCenter = moved.getCenter(false);
        movedCenter.add(velocity);
        moved.setCenter(movedCenter);
        boolean hit = isHit(shape, sphere, false) || isHit(shape, moved, false);
        if (hit && notify && observer != null) {
            observer.onHit(shape, sphere, CollisionSupport.distanceBetween(shape, sphere), CollisionSupport.intersection(shape, sphere), CollisionSupport.length(velocity));
        }
        return hit;
    }

    /**
     * Tests whether a ray picks a figure.
     *
     * @param ray the picking ray
     * @param figure the figure to test
     * @param transform the transform applied to the figure
     * @param calculateAttribute {@code true} to calculate intersection attributes
     * @param notify {@code true} to notify the observer
     * @return {@code true} if the figure is picked
     */
    public boolean isPicked(Ray ray, Figure figure, Transform transform, boolean calculateAttribute, boolean notify) {
        if (ray == null || figure == null) {
            throw new NullPointerException();
        }
        if (notify && observer != null) {
            observer.onPick(ray, figure, new IntersectionAttribute[0]);
        }
        return false;
    }
}

final class CollisionSupport {
    private CollisionSupport() {
    }

    static float distanceBetween(Shape left, Shape right) {
        if (left instanceof Sphere ls && right instanceof Sphere rs) {
            return Collision.getDistance(ls, rs);
        }
        if (left instanceof Point lp) {
            return Collision.getDistance(lp, right);
        }
        if (right instanceof Point rp) {
            return Collision.getDistance(rp, left);
        }
        if (left instanceof Line ll && right instanceof Line rl) {
            return Collision.getDistance(ll, rl);
        }
        Vector3D lc = centerOf(left);
        Vector3D rc = centerOf(right);
        return distance(lc, rc) - effectiveRadius(left, direction(lc, rc)) - effectiveRadius(right, direction(rc, lc));
    }

    static Vector3D intersection(Shape left, Shape right) {
        if (left instanceof Ray ray) {
            return intersection(ray, right);
        }
        if (right instanceof Ray ray) {
            return intersection(ray, left);
        }
        if (left instanceof Sphere ls && right instanceof Sphere rs) {
            return distanceBetween(ls, rs) <= 0f ? midpoint(ls.getCenter(true), rs.getCenter(true)) : null;
        }
        return distanceBetween(left, right) <= 0f ? midpoint(centerOf(left), centerOf(right)) : null;
    }

    static Vector3D intersection(Ray ray, Shape shape) {
        if (shape instanceof Sphere sphere) {
            return intersectRaySphere(ray, sphere);
        }
        if (shape instanceof Plane plane) {
            return intersectRayPlane(ray, plane);
        }
        if (shape instanceof Triangle triangle) {
            return intersectRayTriangle(ray, triangle);
        }
        if (shape instanceof Box box) {
            return intersectRayAabb(ray, box.getCenter(true), box.getSize(), box.getScale());
        }
        if (shape instanceof Point point) {
            return distance(ray.getStartPosition(true), point.getPosition(true)) <= 1e-4f ? point.getPosition(true) : null;
        }
        return null;
    }

    static float distancePointToShape(Vector3D point, Shape shape) {
        if (shape instanceof Point other) {
            return distance(point, other.getPosition(true));
        }
        if (shape instanceof Sphere sphere) {
            return java.lang.Math.max(0f, distance(point, sphere.getCenter(true)) - sphere.getRadius() * sphere.getScale());
        }
        if (shape instanceof Line line) {
            return distancePointToSegment(point, line.getStartPosition(true), line.getEndPosition(true));
        }
        if (shape instanceof Plane plane) {
            Vector3D position = plane.getPosition(true);
            Vector3D normal = plane.getNormal(true);
            return java.lang.Math.abs(dot(sub(point, position), normal));
        }
        if (shape instanceof Triangle triangle) {
            return distancePointToTriangle(point, triangle.getVertices(true));
        }
        if (shape instanceof Box box) {
            return distancePointToAabb(point, box.getCenter(true), box.getSize(), box.getScale());
        }
        if (shape instanceof Cylinder cylinder) {
            return java.lang.Math.max(0f, distance(point, cylinder.getCenter(true)) - cylinder.getEffectiveRadius(direction(cylinder.getCenter(true), point)));
        }
        if (shape instanceof Capsule capsule) {
            return java.lang.Math.max(0f, distance(point, capsule.getCenter(true)) - capsule.getEffectiveRadius(direction(capsule.getCenter(true), point)));
        }
        return distance(point, centerOf(shape));
    }

    static float distanceSegmentToSegment(Vector3D p1, Vector3D q1, Vector3D p2, Vector3D q2) {
        // Real-Time Collision Detection segment-segment distance.
        Vector3D d1 = sub(q1, p1);
        Vector3D d2 = sub(q2, p2);
        Vector3D r = sub(p1, p2);
        float a = dot(d1, d1);
        float e = dot(d2, d2);
        float f = dot(d2, r);
        float s;
        float t;
        if (a <= 1e-6f && e <= 1e-6f) {
            return distance(p1, p2);
        }
        if (a <= 1e-6f) {
            s = 0f;
            t = clamp(f / e, 0f, 1f);
        } else {
            float c = dot(d1, r);
            if (e <= 1e-6f) {
                t = 0f;
                s = clamp(-c / a, 0f, 1f);
            } else {
                float b = dot(d1, d2);
                float denom = a * e - b * b;
                s = denom != 0f ? clamp((b * f - c * e) / denom, 0f, 1f) : 0f;
                float tnom = b * s + f;
                if (tnom < 0f) {
                    t = 0f;
                    s = clamp(-c / a, 0f, 1f);
                } else if (tnom > e) {
                    t = 1f;
                    s = clamp((b - c) / a, 0f, 1f);
                } else {
                    t = tnom / e;
                }
            }
        }
        Vector3D c1 = add(p1, scale(d1, s));
        Vector3D c2 = add(p2, scale(d2, t));
        return distance(c1, c2);
    }

    static Vector3D centerOf(Shape shape) {
        return switch (shape) {
            case Point point -> point.getPosition(true);
            case Ray ray -> ray.getStartPosition(true);
            case Line line -> midpoint(line.getStartPosition(true), line.getEndPosition(true));
            case Triangle triangle -> {
                Vector3D[] v = triangle.getVertices(true);
                yield new Vector3D((v[0].getX() + v[1].getX() + v[2].getX()) / 3f,
                        (v[0].getY() + v[1].getY() + v[2].getY()) / 3f,
                        (v[0].getZ() + v[1].getZ() + v[2].getZ()) / 3f);
            }
            case Plane plane -> plane.getPosition(true);
            case BoundingVolume bv -> bv.getCenter(true);
            default -> new Vector3D();
        };
    }

    static float effectiveRadius(Shape shape, Vector3D direction) {
        if (shape instanceof BoundingVolume bv) {
            return java.lang.Math.max(0f, bv.getEffectiveRadius(direction));
        }
        return 0f;
    }

    static Vector3D direction(Vector3D from, Vector3D to) {
        return new Vector3D(to.getX() - from.getX(), to.getY() - from.getY(), to.getZ() - from.getZ());
    }

    static float distance(Vector3D left, Vector3D right) {
        return length(direction(left, right));
    }

    static float length(Vector3D vector) {
        return (float) java.lang.Math.sqrt(dot(vector, vector));
    }

    static float dot(Vector3D left, Vector3D right) {
        return left.getX() * right.getX() + left.getY() * right.getY() + left.getZ() * right.getZ();
    }

    static Vector3D sub(Vector3D left, Vector3D right) {
        return new Vector3D(left.getX() - right.getX(), left.getY() - right.getY(), left.getZ() - right.getZ());
    }

    static Vector3D add(Vector3D left, Vector3D right) {
        return new Vector3D(left.getX() + right.getX(), left.getY() + right.getY(), left.getZ() + right.getZ());
    }

    static Vector3D scale(Vector3D vector, float factor) {
        return new Vector3D(vector.getX() * factor, vector.getY() * factor, vector.getZ() * factor);
    }

    static float clamp(float value, float min, float max) {
        return java.lang.Math.max(min, java.lang.Math.min(max, value));
    }

    static Vector3D midpoint(Vector3D left, Vector3D right) {
        return new Vector3D((left.getX() + right.getX()) * 0.5f, (left.getY() + right.getY()) * 0.5f, (left.getZ() + right.getZ()) * 0.5f);
    }

    static float distancePointToSegment(Vector3D point, Vector3D start, Vector3D end) {
        Vector3D ab = sub(end, start);
        float denom = dot(ab, ab);
        float t = denom <= 1e-6f ? 0f : clamp(dot(sub(point, start), ab) / denom, 0f, 1f);
        return distance(point, add(start, scale(ab, t)));
    }

    static float distancePointToTriangle(Vector3D point, Vector3D[] triangle) {
        Vector3D closest = closestPointOnTriangle(point, triangle[0], triangle[1], triangle[2]);
        return distance(point, closest);
    }

    static Vector3D closestPointOnTriangle(Vector3D p, Vector3D a, Vector3D b, Vector3D c) {
        Vector3D ab = sub(b, a);
        Vector3D ac = sub(c, a);
        Vector3D ap = sub(p, a);
        float d1 = dot(ab, ap);
        float d2 = dot(ac, ap);
        if (d1 <= 0f && d2 <= 0f) {
            return a;
        }
        Vector3D bp = sub(p, b);
        float d3 = dot(ab, bp);
        float d4 = dot(ac, bp);
        if (d3 >= 0f && d4 <= d3) {
            return b;
        }
        float vc = d1 * d4 - d3 * d2;
        if (vc <= 0f && d1 >= 0f && d3 <= 0f) {
            float v = d1 / (d1 - d3);
            return add(a, scale(ab, v));
        }
        Vector3D cp = sub(p, c);
        float d5 = dot(ab, cp);
        float d6 = dot(ac, cp);
        if (d6 >= 0f && d5 <= d6) {
            return c;
        }
        float vb = d5 * d2 - d1 * d6;
        if (vb <= 0f && d2 >= 0f && d6 <= 0f) {
            float w = d2 / (d2 - d6);
            return add(a, scale(ac, w));
        }
        float va = d3 * d6 - d5 * d4;
        if (va <= 0f && (d4 - d3) >= 0f && (d5 - d6) >= 0f) {
            float w = (d4 - d3) / ((d4 - d3) + (d5 - d6));
            return add(b, scale(sub(c, b), w));
        }
        float denom = 1f / (va + vb + vc);
        float v = vb * denom;
        float w = vc * denom;
        return add(a, add(scale(ab, v), scale(ac, w)));
    }

    static Vector3D intersectRaySphere(Ray ray, Sphere sphere) {
        Vector3D origin = ray.getStartPosition(true);
        Vector3D dir = ray.getDirection(true);
        dir.normalize();
        Vector3D oc = sub(origin, sphere.getCenter(true));
        float b = 2f * dot(oc, dir);
        float c = dot(oc, oc) - sphere.getRadius() * sphere.getRadius() * sphere.getScale() * sphere.getScale();
        float disc = b * b - 4f * c;
        if (disc < 0f) {
            return null;
        }
        float t = (-b - (float) java.lang.Math.sqrt(disc)) * 0.5f;
        if (t < 0f) {
            t = (-b + (float) java.lang.Math.sqrt(disc)) * 0.5f;
        }
        return t < 0f ? null : add(origin, scale(dir, t));
    }

    static Vector3D intersectRayPlane(Ray ray, Plane plane) {
        Vector3D origin = ray.getStartPosition(true);
        Vector3D dir = ray.getDirection(true);
        Vector3D normal = plane.getNormal(true);
        float denom = dot(normal, dir);
        if (!plane.isHittingFromBackFaceEnabled() && denom > 0f) {
            return null;
        }
        if (java.lang.Math.abs(denom) < 1e-6f) {
            return null;
        }
        float t = dot(sub(plane.getPosition(true), origin), normal) / denom;
        return t < 0f ? null : add(origin, scale(dir, t));
    }

    static Vector3D intersectRayTriangle(Ray ray, Triangle triangle) {
        Vector3D[] v = triangle.getVertices(true);
        Vector3D edge1 = sub(v[1], v[0]);
        Vector3D edge2 = sub(v[2], v[0]);
        Vector3D dir = ray.getDirection(true);
        Vector3D pvec = new Vector3D();
        pvec.cross(dir, edge2);
        float det = dot(edge1, pvec);
        if (!triangle.isHittingFromBackFaceEnabled()) {
            if (det < 1e-6f) {
                return null;
            }
        } else if (java.lang.Math.abs(det) < 1e-6f) {
            return null;
        }
        float invDet = 1f / det;
        Vector3D tvec = sub(ray.getStartPosition(true), v[0]);
        float u = dot(tvec, pvec) * invDet;
        if (u < 0f || u > 1f) {
            return null;
        }
        Vector3D qvec = new Vector3D();
        qvec.cross(tvec, edge1);
        float vcoord = dot(dir, qvec) * invDet;
        if (vcoord < 0f || u + vcoord > 1f) {
            return null;
        }
        float t = dot(edge2, qvec) * invDet;
        return t < 0f ? null : add(ray.getStartPosition(true), scale(dir, t));
    }

    static Vector3D intersectRayAabb(Ray ray, Vector3D center, Vector3D size, float scale) {
        Vector3D origin = ray.getStartPosition(true);
        Vector3D dir = ray.getDirection(true);
        Vector3D half = new Vector3D(size.getX() * scale * 0.5f, size.getY() * scale * 0.5f, size.getZ() * scale * 0.5f);
        float tmin = Float.NEGATIVE_INFINITY;
        float tmax = Float.POSITIVE_INFINITY;
        float[] o = {origin.getX(), origin.getY(), origin.getZ()};
        float[] d = {dir.getX(), dir.getY(), dir.getZ()};
        float[] min = {center.getX() - half.getX(), center.getY() - half.getY(), center.getZ() - half.getZ()};
        float[] max = {center.getX() + half.getX(), center.getY() + half.getY(), center.getZ() + half.getZ()};
        for (int i = 0; i < 3; i++) {
            if (java.lang.Math.abs(d[i]) < 1e-6f) {
                if (o[i] < min[i] || o[i] > max[i]) {
                    return null;
                }
                continue;
            }
            float inv = 1f / d[i];
            float t1 = (min[i] - o[i]) * inv;
            float t2 = (max[i] - o[i]) * inv;
            if (t1 > t2) {
                float tmp = t1;
                t1 = t2;
                t2 = tmp;
            }
            tmin = java.lang.Math.max(tmin, t1);
            tmax = java.lang.Math.min(tmax, t2);
            if (tmin > tmax) {
                return null;
            }
        }
        float t = tmin >= 0f ? tmin : tmax;
        return t < 0f ? null : add(origin, scale(dir, t));
    }

    static float distancePointToAabb(Vector3D point, Vector3D center, Vector3D size, float scale) {
        Vector3D half = new Vector3D(size.getX() * scale * 0.5f, size.getY() * scale * 0.5f, size.getZ() * scale * 0.5f);
        float dx = java.lang.Math.max(java.lang.Math.abs(point.getX() - center.getX()) - half.getX(), 0f);
        float dy = java.lang.Math.max(java.lang.Math.abs(point.getY() - center.getY()) - half.getY(), 0f);
        float dz = java.lang.Math.max(java.lang.Math.abs(point.getZ() - center.getZ()) - half.getZ(), 0f);
        return (float) java.lang.Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
