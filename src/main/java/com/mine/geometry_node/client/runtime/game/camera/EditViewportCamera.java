package com.mine.geometry_node.client.runtime.game.camera;

import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Per-viewport orbit camera, independent of Minecraft's player and camera objects.
 * Navigation runs on the UI thread; the renderer consumes one immutable snapshot.
 * Yaw and pitch use Minecraft conventions: yaw 0 faces +Z, positive pitch looks down.
 */
public final class EditViewportCamera {
    /** Axis denotes the side from which the camera looks toward its target. */
    public enum Axis {
        POSITIVE_X(1, 0, 0), NEGATIVE_X(-1, 0, 0),
        POSITIVE_Y(0, 1, 0), NEGATIVE_Y(0, -1, 0),
        POSITIVE_Z(0, 0, 1), NEGATIVE_Z(0, 0, -1);

        public final int x, y, z;
        Axis(int x, int y, int z) { this.x = x; this.y = y; this.z = z; }
        public boolean positive() { return x + y + z > 0; }
        public Axis opposite() { return values()[ordinal() ^ 1]; }
    }
    private static final double MIN_DISTANCE = 0.05;
    private static final double MAX_DISTANCE = 4096.0;
    private static final double INITIAL_DISTANCE = 10.0;
    private static final double DEGREES_PER_PIXEL = 0.25;
    private static final double MAX_COORDINATE = 30_000_000.0;

    private boolean initialized;
    private boolean orthographic;
    private double pivotX;
    private double pivotY;
    private double pivotZ;
    private double distance = INITIAL_DISTANCE;
    private float yaw;
    private float pitch;
    private double verticalFovDegrees = 70.0;

    /** Seeds this view only once, so a render-thread refresh never erases navigation. */
    public synchronized void initialize(double x, double y, double z, float yaw, float pitch) {
        if (!initialized) reset(x, y, z, yaw, pitch);
    }

    /** Re-centers from a real camera position while retaining the projection choice. */
    public synchronized void reset(double x, double y, double z, float yaw, float pitch) {
        if (!finite(x, y, z, yaw, pitch)) return;
        this.yaw = wrapYaw(yaw);
        this.pitch = (float) clamp(pitch, -89.5, 89.5);
        distance = INITIAL_DISTANCE;
        double yawRadians = Math.toRadians(this.yaw);
        double pitchRadians = Math.toRadians(this.pitch);
        pivotX = clampCoordinate(x - Math.sin(yawRadians) * Math.cos(pitchRadians) * distance);
        pivotY = clampCoordinate(y - Math.sin(pitchRadians) * distance);
        pivotZ = clampCoordinate(z + Math.cos(yawRadians) * Math.cos(pitchRadians) * distance);
        initialized = true;
    }

    public synchronized boolean isInitialized() { return initialized; }
    public synchronized boolean isOrthographic() { return orthographic; }
    public synchronized void setOrthographic(boolean orthographic) { this.orthographic = orthographic; }
    public synchronized void toggleOrthographic() { orthographic = !orthographic; }

    /** Exact axis alignment. Orbit retains the pivot; flight retains the eye.
     * Clicking the already-facing side flips it, including overlapping pole buttons.
     */
    public synchronized void alignToAxis(Axis axis, boolean keepEye) {
        alignToAxis(axis, keepEye, true);
    }

    /** Named presets are idempotent; the orientation gizmo may opt into flipping. */
    public synchronized void alignToAxis(Axis axis, boolean keepEye, boolean flipIfAligned) {
        if (!initialized || axis == null) return;
        Snapshot eye = snapshot();
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        double facing = Math.sin(y) * Math.cos(p) * axis.x + Math.sin(p) * axis.y
                - Math.cos(y) * Math.cos(p) * axis.z;
        if (flipIfAligned && facing > 1.0 - 1.0e-8) axis = axis.opposite();
        // Retain heading at poles to keep the image's horizontal orientation stable.
        if (axis.y == 0) yaw = axis.x != 0 ? axis.x * 90f : axis.z > 0 ? -180f : 0f;
        pitch = axis.y * 90f;
        if (keepEye) {
            pivotX = clampCoordinate(eye.x() - axis.x * distance);
            pivotY = clampCoordinate(eye.y() - axis.y * distance);
            pivotZ = clampCoordinate(eye.z() - axis.z * distance);
        }
    }

    /** Synchronize the perspective field of view with the chosen viewport projection.
     * Orthographic height matches perspective scale at the orbit pivot.
     */
    public synchronized void setVerticalFovDegrees(double degrees) {
        if (Double.isFinite(degrees)) verticalFovDegrees = clamp(degrees, 10.0, 150.0);
    }

    /** Middle drag: orbit around the fixed pivot. Deltas are viewport-local pixels. */
    public synchronized void orbit(double deltaX, double deltaY) {
        if (!initialized || !finite(deltaX, deltaY)) return;
        yaw = wrapYaw(yaw + deltaX * DEGREES_PER_PIXEL);
        pitch = (float) clamp(pitch + deltaY * DEGREES_PER_PIXEL, -89.5, 89.5);
    }

    /** Shift-middle drag: move the camera and pivot together in the image plane.
     * Height must use the same local coordinate space as the deltas, excluding bars.
     * This makes panning invariant under UI scaling and supports orthographic mode.
     */
    public synchronized void pan(double deltaX, double deltaY, double viewportHeight) {
        if (!initialized || !finite(deltaX, deltaY, viewportHeight) || viewportHeight <= 0.0) return;
        double scale = visibleHeight() / viewportHeight;
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        double sinYaw = Math.sin(yawRadians);
        double cosYaw = Math.cos(yawRadians);
        double sinPitch = Math.sin(pitchRadians);
        double cosPitch = Math.cos(pitchRadians);
        // camera-right = (-cosYaw, 0, -sinYaw), camera-up = right x forward.
        pivotX = clampCoordinate(pivotX + (cosYaw * deltaX - sinYaw * sinPitch * deltaY) * scale);
        pivotY = clampCoordinate(pivotY + cosPitch * deltaY * scale);
        pivotZ = clampCoordinate(pivotZ + (sinYaw * deltaX + cosYaw * sinPitch * deltaY) * scale);
    }

    /** Positive wheel steps zoom in. The pivot stays fixed in both projections. */
    public synchronized void dolly(double steps) {
        if (!initialized || !Double.isFinite(steps)) return;
        // Clamp before exponentiation to avoid overflow from malformed/native deltas.
        distance = clamp(distance * Math.exp(-clamp(steps, -100.0, 100.0) * 0.15),
                MIN_DISTANCE, MAX_DISTANCE);
    }

    /** Turn around the eye rather than the orbit pivot; angles are in degrees. */
    public synchronized void look(double deltaYaw, double deltaPitch) {
        if (!initialized || !finite(deltaYaw, deltaPitch)) return;
        Snapshot eye = snapshot();
        yaw = wrapYaw(yaw + deltaYaw);
        pitch = (float) clamp(pitch + deltaPitch, -89.5, 89.5);
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        pivotX = clampCoordinate(eye.x() - Math.sin(y) * Math.cos(p) * distance);
        pivotY = clampCoordinate(eye.y() - Math.sin(p) * distance);
        pivotZ = clampCoordinate(eye.z() + Math.cos(y) * Math.cos(p) * distance);
    }

    /** Translate the eye and orbit pivot together, without changing zoom. */
    public synchronized void translate(double x, double y, double z) {
        if (!initialized || !finite(x, y, z)) return;
        pivotX = clampCoordinate(pivotX + x);
        pivotY = clampCoordinate(pivotY + y);
        pivotZ = clampCoordinate(pivotZ + z);
    }

    /** Frame all supplied bounds while preserving heading and projection. Padding must be >= 1. */
    public synchronized void focus(AABB bounds, double aspect, double padding) {
        if (bounds == null || !finite(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY,
                bounds.maxZ, aspect, padding) || aspect <= 0 || padding < 1) return;
        Vec3 center = bounds.getCenter();
        double y = Math.toRadians(yaw), p = Math.toRadians(pitch);
        Vec3 forward = new Vec3(-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p));
        Vec3 right = new Vec3(-Math.cos(y), 0, -Math.sin(y));
        Vec3 up = right.cross(forward);
        double tanY = Math.tan(Math.toRadians(verticalFovDegrees) * .5), tanX = tanY * aspect;
        double required = .5;
        for (int i = 0; i < 8; i++) {
            Vec3 offset = new Vec3((i & 1) == 0 ? bounds.minX : bounds.maxX,
                    (i & 2) == 0 ? bounds.minY : bounds.maxY,
                    (i & 4) == 0 ? bounds.minZ : bounds.maxZ).subtract(center);
            double depth = orthographic ? 0 : offset.dot(forward);
            required = Math.max(required, Math.max(Math.abs(offset.dot(right)) * padding / tanX - depth,
                    Math.abs(offset.dot(up)) * padding / tanY - depth));
            if (!orthographic) required = Math.max(required, .1 - depth);
        }
        pivotX = clampCoordinate(center.x);
        pivotY = clampCoordinate(center.y);
        pivotZ = clampCoordinate(center.z);
        distance = clamp(required, .5, MAX_DISTANCE);
        initialized = true;
    }

    public synchronized void focus(Vec3 center, double radius) {
        if (center == null || !finite(center.x, center.y, center.z, radius) || radius < 0) return;
        focus(new AABB(center.x - radius, center.y - radius, center.z - radius,
                center.x + radius, center.y + radius, center.z + radius), 1, 1.2);
    }

    public synchronized Snapshot snapshot() {
        double yawRadians = Math.toRadians(yaw);
        double pitchRadians = Math.toRadians(pitch);
        return new Snapshot(
                pivotX + Math.sin(yawRadians) * Math.cos(pitchRadians) * distance,
                pivotY + Math.sin(pitchRadians) * distance,
                pivotZ - Math.cos(yawRadians) * Math.cos(pitchRadians) * distance,
                yaw, pitch, pivotX, pivotY, pivotZ, distance, orthographic,
                visibleHeight(), verticalFovDegrees);
    }

    private double visibleHeight() {
        return 2.0 * distance * Math.tan(Math.toRadians(verticalFovDegrees) * 0.5);
    }

    private static float wrapYaw(double degrees) {
        double result = degrees % 360.0;
        if (result >= 180.0) result -= 360.0;
        if (result < -180.0) result += 360.0;
        return (float) result;
    }

    private static double clampCoordinate(double value) {
        return clamp(value, -MAX_COORDINATE, MAX_COORDINATE);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static boolean finite(double... values) {
        for (double value : values) if (!Double.isFinite(value)) return false;
        return true;
    }

    public record Snapshot(double x, double y, double z, float yaw, float pitch,
                           double pivotX, double pivotY, double pivotZ, double distance,
                           boolean orthographic, double orthographicHeight,
                           double verticalFovDegrees) {}
}
