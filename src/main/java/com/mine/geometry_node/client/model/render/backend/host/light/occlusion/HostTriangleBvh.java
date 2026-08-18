package com.mine.geometry_node.client.model.render.backend.host.light.occlusion;

import com.mine.geometry_node.client.model.render.backend.host.light.asset.HostLightingGeometryParameters;
import com.mine.geometry_node.client.model.render.backend.host.light.asset.HostLightingSurface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/** Immutable model-space BVH over complete opaque source triangles. */
public final class HostTriangleBvh {
    private final List<HostLightingSurface> surfaces;
    private final int[] triangleSurfaces;
    private final int[] triangleIndices;
    private final float[] minimums;
    private final float[] maximums;
    private final int[] left;
    private final int[] right;
    private final int[] starts;
    private final int[] counts;
    private final float rayEpsilon;
    private final int nodeCount;

    private HostTriangleBvh(List<HostLightingSurface> surfaces, int[] triangleSurfaces, int[] triangleIndices,
                            float[] minimums, float[] maximums, int[] left, int[] right,
                            int[] starts, int[] counts, float rayEpsilon, int nodeCount) {
        this.surfaces = List.copyOf(surfaces);
        this.triangleSurfaces = triangleSurfaces;
        this.triangleIndices = triangleIndices;
        this.minimums = minimums;
        this.maximums = maximums;
        this.left = left;
        this.right = right;
        this.starts = starts;
        this.counts = counts;
        this.rayEpsilon = rayEpsilon;
        this.nodeCount = nodeCount;
    }

    public static HostTriangleBvh build(List<HostLightingSurface> surfaces,
                                        HostLightingGeometryParameters parameters) {
        Objects.requireNonNull(surfaces, "surfaces");
        Objects.requireNonNull(parameters, "parameters");
        int triangleCount = 0;
        for (HostLightingSurface surface : surfaces) {
            if (!surface.material().blocksLight()) continue;
            for (int triangle = 0; triangle < surface.triangleCount(); triangle++) {
                if (!surface.triangleDegenerate(triangle)) triangleCount = Math.incrementExact(triangleCount);
            }
        }
        int[] triangleSurfaces = new int[triangleCount];
        int[] triangleIndices = new int[triangleCount];
        int cursor = 0;
        for (int surfaceIndex = 0; surfaceIndex < surfaces.size(); surfaceIndex++) {
            HostLightingSurface surface = surfaces.get(surfaceIndex);
            if (!surface.material().blocksLight()) continue;
            for (int triangle = 0; triangle < surface.triangleCount(); triangle++) {
                if (surface.triangleDegenerate(triangle)) continue;
                triangleSurfaces[cursor] = surfaceIndex;
                triangleIndices[cursor++] = triangle;
            }
        }
        if (triangleCount == 0) {
            return new HostTriangleBvh(surfaces, triangleSurfaces, triangleIndices,
                    new float[0], new float[0], new int[0], new int[0], new int[0], new int[0],
                    parameters.rayEpsilon(), 0);
        }
        int capacity = nodeCapacity(triangleCount, parameters.bvhLeafTriangles());
        Builder builder = new Builder(surfaces, triangleSurfaces, triangleIndices, capacity,
                parameters.bvhLeafTriangles());
        builder.buildNode(0, triangleCount);
        return new HostTriangleBvh(surfaces, triangleSurfaces, triangleIndices,
                Arrays.copyOf(builder.minimums, builder.nodeCount * 3),
                Arrays.copyOf(builder.maximums, builder.nodeCount * 3),
                Arrays.copyOf(builder.left, builder.nodeCount), Arrays.copyOf(builder.right, builder.nodeCount),
                Arrays.copyOf(builder.starts, builder.nodeCount), Arrays.copyOf(builder.counts, builder.nodeCount),
                parameters.rayEpsilon(), builder.nodeCount);
    }

    public int triangleCount() { return triangleIndices.length; }
    public int nodeCount() { return nodeCount; }

    /** True when an opaque source triangle intersects the open segment. */
    public boolean blocked(float fromX, float fromY, float fromZ, float toX, float toY, float toZ) {
        if (nodeCount == 0) return false;
        float dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
        float length = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(length > rayEpsilon * 2F)) return false;
        int[] stack = new int[64];
        int stackSize = 1;
        stack[0] = 0;
        while (stackSize > 0) {
            int node = stack[--stackSize];
            if (!intersectsBounds(node, fromX, fromY, fromZ, dx, dy, dz)) continue;
            if (counts[node] > 0) {
                int end = starts[node] + counts[node];
                for (int index = starts[node]; index < end; index++) {
                    if (intersectsTriangle(index, fromX, fromY, fromZ, dx, dy, dz, length)) return true;
                }
            } else {
                if (stackSize + 2 > stack.length) stack = Arrays.copyOf(stack, stack.length * 2);
                stack[stackSize++] = left[node];
                stack[stackSize++] = right[node];
            }
        }
        return false;
    }

    public long retainedBytes() {
        return (long) (triangleSurfaces.length + triangleIndices.length + left.length + right.length
                + starts.length + counts.length) * Integer.BYTES
                + (long) (minimums.length + maximums.length) * Float.BYTES;
    }

    private static int nodeCapacity(int triangles, int leafSize) {
        if (triangles <= leafSize) return 1;
        int first = triangles / 2;
        return Math.addExact(1, Math.addExact(nodeCapacity(first, leafSize),
                nodeCapacity(triangles - first, leafSize)));
    }

    private boolean intersectsBounds(int node, float ox, float oy, float oz, float dx, float dy, float dz) {
        float near = 0F, far = 1F;
        for (int axis = 0; axis < 3; axis++) {
            float origin = axis == 0 ? ox : axis == 1 ? oy : oz;
            float direction = axis == 0 ? dx : axis == 1 ? dy : dz;
            float minimum = minimums[node * 3 + axis], maximum = maximums[node * 3 + axis];
            if (Math.abs(direction) < 1.0e-20F) {
                if (origin < minimum || origin > maximum) return false;
                continue;
            }
            float first = (minimum - origin) / direction;
            float second = (maximum - origin) / direction;
            if (first > second) { float swap = first; first = second; second = swap; }
            near = Math.max(near, first);
            far = Math.min(far, second);
            if (near > far) return false;
        }
        return true;
    }

    private boolean intersectsTriangle(int reference, float ox, float oy, float oz,
                                       float dx, float dy, float dz, float segmentLength) {
        HostLightingSurface surface = surfaces.get(triangleSurfaces[reference]);
        int triangle = triangleIndices[reference];
        int a = surface.vertexIndex(triangle, 0), b = surface.vertexIndex(triangle, 1),
                c = surface.vertexIndex(triangle, 2);
        float ax = surface.position(a, 0), ay = surface.position(a, 1), az = surface.position(a, 2);
        float e1x = surface.position(b, 0) - ax, e1y = surface.position(b, 1) - ay,
                e1z = surface.position(b, 2) - az;
        float e2x = surface.position(c, 0) - ax, e2y = surface.position(c, 1) - ay,
                e2z = surface.position(c, 2) - az;
        float px = dy * e2z - dz * e2y, py = dz * e2x - dx * e2z, pz = dx * e2y - dy * e2x;
        float determinant = e1x * px + e1y * py + e1z * pz;
        if (Math.abs(determinant) < 1.0e-20F) return false;
        float inverse = 1F / determinant;
        float tx = ox - ax, ty = oy - ay, tz = oz - az;
        float u = (tx * px + ty * py + tz * pz) * inverse;
        if (u < 0F || u > 1F) return false;
        float qx = ty * e1z - tz * e1y, qy = tz * e1x - tx * e1z, qz = tx * e1y - ty * e1x;
        float v = (dx * qx + dy * qy + dz * qz) * inverse;
        if (v < 0F || u + v > 1F) return false;
        float t = (e2x * qx + e2y * qy + e2z * qz) * inverse;
        float normalizedEpsilon = rayEpsilon / segmentLength;
        return t > normalizedEpsilon && t < 1F - normalizedEpsilon;
    }

    private static final class Builder {
        private final List<HostLightingSurface> surfaces;
        private final int[] triangleSurfaces, triangleIndices;
        private final float[] minimums, maximums;
        private final int[] left, right, starts, counts;
        private final int leafSize;
        private int nodeCount;

        private Builder(List<HostLightingSurface> surfaces, int[] triangleSurfaces, int[] triangleIndices,
                        int capacity, int leafSize) {
            this.surfaces = new ArrayList<>(surfaces);
            this.triangleSurfaces = triangleSurfaces;
            this.triangleIndices = triangleIndices;
            this.minimums = new float[capacity * 3];
            this.maximums = new float[capacity * 3];
            this.left = new int[capacity]; this.right = new int[capacity];
            this.starts = new int[capacity]; this.counts = new int[capacity];
            this.leafSize = leafSize;
        }

        private int buildNode(int start, int count) {
            int node = nodeCount++;
            bounds(node, start, count);
            if (count <= leafSize) {
                starts[node] = start;
                counts[node] = count;
                return node;
            }
            float extentX = maximums[node * 3] - minimums[node * 3];
            float extentY = maximums[node * 3 + 1] - minimums[node * 3 + 1];
            float extentZ = maximums[node * 3 + 2] - minimums[node * 3 + 2];
            int axis = extentY > extentX ? 1 : 0;
            if ((axis == 0 ? extentX : extentY) < extentZ) axis = 2;
            sort(start, start + count - 1, axis);
            int firstCount = count / 2;
            left[node] = buildNode(start, firstCount);
            right[node] = buildNode(start + firstCount, count - firstCount);
            return node;
        }

        private void bounds(int node, int start, int count) {
            int offset = node * 3;
            minimums[offset] = minimums[offset + 1] = minimums[offset + 2] = Float.POSITIVE_INFINITY;
            maximums[offset] = maximums[offset + 1] = maximums[offset + 2] = Float.NEGATIVE_INFINITY;
            for (int reference = start; reference < start + count; reference++) {
                HostLightingSurface surface = surfaces.get(triangleSurfaces[reference]);
                int triangle = triangleIndices[reference];
                for (int corner = 0; corner < 3; corner++) {
                    int vertex = surface.vertexIndex(triangle, corner);
                    for (int axis = 0; axis < 3; axis++) {
                        float value = surface.position(vertex, axis);
                        minimums[offset + axis] = Math.min(minimums[offset + axis], value);
                        maximums[offset + axis] = Math.max(maximums[offset + axis], value);
                    }
                }
            }
        }

        private void sort(int low, int high, int axis) {
            while (low < high) {
                int i = low, j = high;
                float pivot = centroid((low + high) >>> 1, axis);
                while (i <= j) {
                    while (centroid(i, axis) < pivot) i++;
                    while (centroid(j, axis) > pivot) j--;
                    if (i <= j) { swap(i++, j--); }
                }
                if (j - low < high - i) {
                    if (low < j) sort(low, j, axis);
                    low = i;
                } else {
                    if (i < high) sort(i, high, axis);
                    high = j;
                }
            }
        }

        private float centroid(int reference, int axis) {
            HostLightingSurface surface = surfaces.get(triangleSurfaces[reference]);
            int triangle = triangleIndices[reference];
            return (surface.position(surface.vertexIndex(triangle, 0), axis)
                    + surface.position(surface.vertexIndex(triangle, 1), axis)
                    + surface.position(surface.vertexIndex(triangle, 2), axis)) / 3F;
        }

        private void swap(int first, int second) {
            int surface = triangleSurfaces[first]; triangleSurfaces[first] = triangleSurfaces[second];
            triangleSurfaces[second] = surface;
            int triangle = triangleIndices[first]; triangleIndices[first] = triangleIndices[second];
            triangleIndices[second] = triangle;
        }
    }
}
