package com.mine.geometry_node.client.model.render.backend.host.light.occlusion;

import com.mine.geometry_node.client.model.render.backend.host.light.asset.HostLightingGeometryParameters;
import com.mine.geometry_node.client.model.render.backend.host.light.asset.HostLightingSurface;

import java.util.BitSet;
import java.util.List;
import java.util.Objects;

/** Conservative uniform occupancy grid derived from opaque source triangles. */
public final class HostConservativeVoxelGrid {
    private final float minimumX, minimumY, minimumZ, cellSize;
    private final int sizeX, sizeY, sizeZ;
    private final BitSet occupied;
    private final long triangleBoxTests;

    private HostConservativeVoxelGrid(float minimumX, float minimumY, float minimumZ, float cellSize,
                                      int sizeX, int sizeY, int sizeZ, BitSet occupied,
                                      long triangleBoxTests) {
        this.minimumX = minimumX; this.minimumY = minimumY; this.minimumZ = minimumZ;
        this.cellSize = cellSize;
        this.sizeX = sizeX; this.sizeY = sizeY; this.sizeZ = sizeZ;
        this.occupied = occupied;
        this.triangleBoxTests = triangleBoxTests;
    }

    public static HostConservativeVoxelGrid build(List<HostLightingSurface> surfaces,
                                                   HostLightingGeometryParameters parameters) {
        Objects.requireNonNull(surfaces, "surfaces");
        Objects.requireNonNull(parameters, "parameters");
        float minX = Float.POSITIVE_INFINITY, minY = minX, minZ = minX;
        float maxX = Float.NEGATIVE_INFINITY, maxY = maxX, maxZ = maxX;
        boolean any = false;
        for (HostLightingSurface surface : surfaces) {
            if (!surface.material().blocksLight()) continue;
            for (int triangle = 0; triangle < surface.triangleCount(); triangle++) {
                if (surface.triangleDegenerate(triangle)) continue;
                any = true;
                for (int corner = 0; corner < 3; corner++) {
                    int vertex = surface.vertexIndex(triangle, corner);
                    minX = Math.min(minX, surface.position(vertex, 0));
                    minY = Math.min(minY, surface.position(vertex, 1));
                    minZ = Math.min(minZ, surface.position(vertex, 2));
                    maxX = Math.max(maxX, surface.position(vertex, 0));
                    maxY = Math.max(maxY, surface.position(vertex, 1));
                    maxZ = Math.max(maxZ, surface.position(vertex, 2));
                }
            }
        }
        if (!any) return new HostConservativeVoxelGrid(0, 0, 0, 1, 0, 0, 0, new BitSet(), 0);
        float longest = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));
        float cell = Math.max(longest / parameters.voxelLongestAxisCells(), 1.0e-6F);
        minX -= cell * 0.5F; minY -= cell * 0.5F; minZ -= cell * 0.5F;
        maxX += cell * 0.5F; maxY += cell * 0.5F; maxZ += cell * 0.5F;
        int sizeX = Math.max(1, (int) Math.ceil((maxX - minX) / cell));
        int sizeY = Math.max(1, (int) Math.ceil((maxY - minY) / cell));
        int sizeZ = Math.max(1, (int) Math.ceil((maxZ - minZ) / cell));
        long cells = Math.multiplyExact((long) sizeX, Math.multiplyExact((long) sizeY, sizeZ));
        if (cells > parameters.maximumVoxelCells() || cells > Integer.MAX_VALUE) {
            throw new VoxelizationLimitExceeded("lighting voxel grid requires " + cells + " cells");
        }
        BitSet occupied = new BitSet((int) cells);
        long tests = 0;
        float half = cell * 0.5F;
        float[] vertices = new float[9];
        float[] translated = new float[9];
        for (HostLightingSurface surface : surfaces) {
            if (!surface.material().blocksLight()) continue;
            for (int triangle = 0; triangle < surface.triangleCount(); triangle++) {
                if (surface.triangleDegenerate(triangle)) continue;
                triangle(surface, triangle, vertices);
                int fromX = clamp((int) Math.floor((minimum(vertices[0], vertices[3], vertices[6]) - minX) / cell), sizeX);
                int fromY = clamp((int) Math.floor((minimum(vertices[1], vertices[4], vertices[7]) - minY) / cell), sizeY);
                int fromZ = clamp((int) Math.floor((minimum(vertices[2], vertices[5], vertices[8]) - minZ) / cell), sizeZ);
                int toX = clamp((int) Math.floor((maximum(vertices[0], vertices[3], vertices[6]) - minX) / cell), sizeX);
                int toY = clamp((int) Math.floor((maximum(vertices[1], vertices[4], vertices[7]) - minY) / cell), sizeY);
                int toZ = clamp((int) Math.floor((maximum(vertices[2], vertices[5], vertices[8]) - minZ) / cell), sizeZ);
                long candidates = (long) (toX - fromX + 1) * (toY - fromY + 1) * (toZ - fromZ + 1);
                tests = Math.addExact(tests, candidates);
                if (tests > parameters.maximumTriangleBoxTests()) {
                    throw new VoxelizationLimitExceeded("lighting voxelization exceeds triangle-box test limit");
                }
                for (int z = fromZ; z <= toZ; z++) {
                    float centerZ = minZ + (z + 0.5F) * cell;
                    for (int y = fromY; y <= toY; y++) {
                        float centerY = minY + (y + 0.5F) * cell;
                        for (int x = fromX; x <= toX; x++) {
                            float centerX = minX + (x + 0.5F) * cell;
                            if (overlaps(vertices, translated, centerX, centerY, centerZ, half)) {
                                occupied.set((z * sizeY + y) * sizeX + x);
                            }
                        }
                    }
                }
            }
        }
        return new HostConservativeVoxelGrid(minX, minY, minZ, cell, sizeX, sizeY, sizeZ, occupied, tests);
    }

    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }
    public float minimumX() { return minimumX; }
    public float minimumY() { return minimumY; }
    public float minimumZ() { return minimumZ; }
    public float cellSize() { return cellSize; }
    public int occupiedCells() { return occupied.cardinality(); }
    public long triangleBoxTests() { return triangleBoxTests; }
    public long retainedBytes() { return ((long) occupied.length() + 63L) / 64L * Long.BYTES; }

    public boolean occupied(int x, int y, int z) {
        if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) return false;
        return occupied.get((z * sizeY + y) * sizeX + x);
    }

    public boolean occupiedAt(float x, float y, float z) {
        return occupied((int) Math.floor((x - minimumX) / cellSize),
                (int) Math.floor((y - minimumY) / cellSize),
                (int) Math.floor((z - minimumZ) / cellSize));
    }

    private static void triangle(HostLightingSurface surface, int triangle, float[] values) {
        for (int corner = 0; corner < 3; corner++) {
            int vertex = surface.vertexIndex(triangle, corner);
            for (int axis = 0; axis < 3; axis++) values[corner * 3 + axis] = surface.position(vertex, axis);
        }
    }

    private static boolean overlaps(float[] triangle, float[] translated,
                                    float cx, float cy, float cz, float half) {
        for (int corner = 0; corner < 3; corner++) {
            translated[corner * 3] = triangle[corner * 3] - cx;
            translated[corner * 3 + 1] = triangle[corner * 3 + 1] - cy;
            translated[corner * 3 + 2] = triangle[corner * 3 + 2] - cz;
        }
        return overlapsCentered(translated, half);
    }

    private static boolean overlapsCentered(float[] v, float half) {
        for (int axis = 0; axis < 3; axis++) {
            float min = minimum(v[axis], v[3 + axis], v[6 + axis]);
            float max = maximum(v[axis], v[3 + axis], v[6 + axis]);
            if (min > half || max < -half) return false;
        }
        for (int edge = 0; edge < 3; edge++) {
            int first = edge * 3, second = ((edge + 1) % 3) * 3;
            float ex = v[second] - v[first], ey = v[second + 1] - v[first + 1],
                    ez = v[second + 2] - v[first + 2];
            if (separated(v, 0, ez, -ey, half) || separated(v, -ez, 0, ex, half)
                    || separated(v, ey, -ex, 0, half)) return false;
        }
        float e0x = v[3] - v[0], e0y = v[4] - v[1], e0z = v[5] - v[2];
        float e1x = v[6] - v[0], e1y = v[7] - v[1], e1z = v[8] - v[2];
        float nx = e0y * e1z - e0z * e1y, ny = e0z * e1x - e0x * e1z,
                nz = e0x * e1y - e0y * e1x;
        return !separated(v, nx, ny, nz, half);
    }

    private static boolean separated(float[] v, float ax, float ay, float az, float half) {
        if (ax == 0F && ay == 0F && az == 0F) return false;
        float p0 = v[0] * ax + v[1] * ay + v[2] * az;
        float p1 = v[3] * ax + v[4] * ay + v[5] * az;
        float p2 = v[6] * ax + v[7] * ay + v[8] * az;
        float radius = half * (Math.abs(ax) + Math.abs(ay) + Math.abs(az));
        return minimum(p0, p1, p2) > radius || maximum(p0, p1, p2) < -radius;
    }

    private static int clamp(int value, int size) { return Math.max(0, Math.min(size - 1, value)); }
    private static float minimum(float a, float b, float c) { return Math.min(a, Math.min(b, c)); }
    private static float maximum(float a, float b, float c) { return Math.max(a, Math.max(b, c)); }

    public static final class VoxelizationLimitExceeded extends RuntimeException {
        private VoxelizationLimitExceeded(String message) { super(message); }
    }
}
