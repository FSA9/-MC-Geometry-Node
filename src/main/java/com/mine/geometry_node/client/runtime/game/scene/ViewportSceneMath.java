package com.mine.geometry_node.client.runtime.game.scene;

import com.mine.geometry_node.client.runtime.game.camera.EditViewportCamera;
import com.mine.geometry_node.client.runtime.game.render.PresentedViewportFrame;
import com.mine.geometry_node.core.engine.system.scene.SceneObject;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4d;
import org.joml.Vector4d;

/** Pure math in normalized image coordinates (top-left = 0,0); UI transforms/black bars stay in UI. */
public final class ViewportSceneMath {
    private static final double EPSILON = 1e-9;
    private ViewportSceneMath() {}

    public record Projected(double u, double v, double depth, boolean visible) {}
    public record Ray(Vec3 origin, Vec3 direction, double length) {}

    public static Projected project(PresentedViewportFrame frame, Vec3 point) {
        if (frame == null || point == null) return new Projected(Double.NaN, Double.NaN, Double.NaN, false);
        var camera = frame.camera();
        Vector4d clip = new Vector4d(point.x - camera.x(), point.y - camera.y(), point.z - camera.z(), 1);
        frame.viewProjection(new Matrix4d()).transform(clip);
        if (!Double.isFinite(clip.w) || clip.w <= EPSILON)
            return new Projected(Double.NaN, Double.NaN, Double.NaN, false);
        double x = clip.x / clip.w, y = clip.y / clip.w, z = clip.z / clip.w;
        boolean visible = Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                && x >= -1 && x <= 1 && y >= -1 && y <= 1 && z >= (frame.zeroToOne() ? 0 : -1) && z <= 1;
        return new Projected((x + 1) * .5, (1 - y) * .5, z, visible);
    }

    public static Ray ray(PresentedViewportFrame frame, double u, double v) {
        if (frame == null || !Double.isFinite(u) || !Double.isFinite(v)) return null;
        var inverse = frame.inverseViewProjection(new Matrix4d());
        Vector4d start = new Vector4d(2 * u - 1, 1 - 2 * v, frame.zeroToOne() ? 0 : -1, 1);
        Vector4d end = new Vector4d(2 * u - 1, 1 - 2 * v, 1, 1);
        inverse.transform(start);
        inverse.transform(end);
        if (Math.abs(start.w) < EPSILON || Math.abs(end.w) < EPSILON) return null;
        start.div(start.w); end.div(end.w);
        Vec3 delta = new Vec3(end.x - start.x, end.y - start.y, end.z - start.z);
        double length = delta.length();
        if (!Double.isFinite(length) || length < EPSILON) return null;
        var camera = frame.camera();
        return new Ray(new Vec3(start.x + camera.x(), start.y + camera.y(), start.z + camera.z()),
                delta.scale(1 / length), length);
    }

    public static AABB bounds(SceneObject object) {
        if (!SceneObject.POINT_LIGHT.equals(object.typeId())) return focusBounds(object);
        Vec3 p = object.position();
        return new AABB(p.x - .18, p.y - .18, p.z - .18, p.x + .18, p.y + .18, p.z + .18);
    }

    /** Frame object geometry, not its effect radius. Point lights use their one-block helper bounds. */
    public static AABB focusBounds(SceneObject object) {
        return com.mine.geometry_node.core.engine.system.scene.SceneTransforms.bounds(object,com.mine.geometry_node.core.engine.system.scene.SceneObjectTypes.require(object.typeId()).bounds().apply(object));
    }

    /** Distance from the visible near plane, or infinity. Works for signed orthographic near planes. */
    public static double pickBounds(PresentedViewportFrame frame, double u, double v, AABB bounds) {
        Ray ray = ray(frame, u, v);
        if (ray == null || bounds == null) return Double.POSITIVE_INFINITY;
        double lo = 0, hi = ray.length();
        double[] origins = {ray.origin().x, ray.origin().y, ray.origin().z};
        double[] directions = {ray.direction().x, ray.direction().y, ray.direction().z};
        double[] minimum = {bounds.minX, bounds.minY, bounds.minZ};
        double[] maximum = {bounds.maxX, bounds.maxY, bounds.maxZ};
        for (int axis = 0; axis < 3; axis++) {
            if (Math.abs(directions[axis]) < EPSILON) {
                if (origins[axis] < minimum[axis] || origins[axis] > maximum[axis]) return Double.POSITIVE_INFINITY;
            } else {
                double a = (minimum[axis] - origins[axis]) / directions[axis];
                double b = (maximum[axis] - origins[axis]) / directions[axis];
                lo = Math.max(lo, Math.min(a, b));
                hi = Math.min(hi, Math.max(a, b));
                if (hi < lo) return Double.POSITIVE_INFINITY;
            }
        }
        return lo;
    }

    /** Conservative selection-frustum/AABB intersection, including boxes crossing the near plane. */
    public static boolean intersects(PresentedViewportFrame frame, AABB box,
                                     double minU, double minV, double maxU, double maxV) {
        if (frame == null || box == null || !finite(minU, minV, maxU, maxV)) return false;
        double left = 2 * Math.max(0, Math.min(minU, maxU)) - 1;
        double right = 2 * Math.min(1, Math.max(minU, maxU)) - 1;
        double bottom = 1 - 2 * Math.min(1, Math.max(minV, maxV));
        double top = 1 - 2 * Math.max(0, Math.min(minV, maxV));
        if (left > right || bottom > top) return false;
        var matrix = frame.viewProjection(new Matrix4d());
        var camera = frame.camera();
        // A convex box is outside a clip plane only if all its vertices are outside that plane.
        boolean[] allOutside = {true, true, true, true, true, true};
        for (int i = 0; i < 8; i++) {
            Vector4d p = new Vector4d(((i & 1) == 0 ? box.minX : box.maxX) - camera.x(),
                    ((i & 2) == 0 ? box.minY : box.maxY) - camera.y(),
                    ((i & 4) == 0 ? box.minZ : box.maxZ) - camera.z(), 1);
            matrix.transform(p);
            double[] planes = {p.x - left * p.w, right * p.w - p.x, p.y - bottom * p.w,
                    top * p.w - p.y, p.z - (frame.zeroToOne() ? 0 : -p.w), p.w - p.z};
            for (int plane = 0; plane < 6; plane++) if (planes[plane] >= -EPSILON) allOutside[plane] = false;
        }
        for (boolean outside : allOutside) if (outside) return false;
        return true;
    }

    /** Fixed-plane constrained axis translation. NaN means the projected axis is ambiguous. */
    public static double axisDrag(PresentedViewportFrame frame, Vec3 origin, EditViewportCamera.Axis axis,
                                  double startU, double startV, double u, double v) {
        if (frame == null || origin == null || axis == null) return Double.NaN;
        Ray center = ray(frame, .5, .5), start = ray(frame, startU, startV), current = ray(frame, u, v);
        if (center == null || start == null || current == null) return Double.NaN;
        Vec3 direction = new Vec3(axis.x, axis.y, axis.z);
        Vec3 normal = center.direction().subtract(direction.scale(center.direction().dot(direction)));
        if (normal.lengthSqr() < 1e-6) return Double.NaN;
        normal = normal.normalize();
        Vec3 first = intersectPlane(start, origin, normal), next = intersectPlane(current, origin, normal);
        return first == null || next == null ? Double.NaN : next.subtract(first).dot(direction);
    }

    private static Vec3 intersectPlane(Ray ray, Vec3 origin, Vec3 normal) {
        double denominator = ray.direction().dot(normal);
        if (Math.abs(denominator) < 1e-6) return null;
        double distance = origin.subtract(ray.origin()).dot(normal) / denominator;
        if (!Double.isFinite(distance)) return null;
        return ray.origin().add(ray.direction().scale(distance));
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }
}
