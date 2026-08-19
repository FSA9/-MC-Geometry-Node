package com.mine.geometry_node.client.model.render.backend.host.light.capture;

import com.mine.geometry_node.client.model.runtime.ModelDimensionId;

import java.util.List;
import java.util.Objects;

/** Immutable worker-safe world-shape capture. Zero cell IDs mean empty. */
public final class WorldOccluderSnapshot {
    private final ModelDimensionId dimension;
    private final long worldRevision;
    private final int minX, minY, minZ, sizeX, sizeY, sizeZ;
    private final List<WorldOccluderShape> palette;
    private final short[] cellShapeIds;
    private final boolean complete;
    private final boolean conservativeFallback;

    public WorldOccluderSnapshot(ModelDimensionId dimension, long worldRevision,
                                 int minX, int minY, int minZ,
                                 int sizeX, int sizeY, int sizeZ,
                                 List<WorldOccluderShape> palette, short[] cellShapeIds,
                                 boolean complete, boolean conservativeFallback) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        if (worldRevision < 0) throw new IllegalArgumentException("worldRevision must not be negative");
        this.worldRevision = worldRevision;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = positive(sizeX);
        this.sizeY = positive(sizeY);
        this.sizeZ = positive(sizeZ);
        int cells = Math.toIntExact(Math.multiplyExact(Math.multiplyExact((long) sizeX, sizeY), sizeZ));
        Objects.requireNonNull(palette, "palette");
        if (palette.size() > Short.MAX_VALUE) throw new IllegalArgumentException("shape palette is too large");
        this.palette = List.copyOf(palette);
        this.palette.forEach(shape -> Objects.requireNonNull(shape, "shape"));
        this.cellShapeIds = Objects.requireNonNull(cellShapeIds, "cellShapeIds").clone();
        if (this.cellShapeIds.length != cells) {
            throw new IllegalArgumentException("cellShapeIds length must equal cell count");
        }
        for (short value : this.cellShapeIds) {
            int id = Short.toUnsignedInt(value);
            if (id > this.palette.size()) throw new IllegalArgumentException("invalid shape palette ID");
        }
        this.complete = complete;
        this.conservativeFallback = conservativeFallback;
    }

    public ModelDimensionId dimension() { return dimension; }
    public long worldRevision() { return worldRevision; }
    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }
    public int cellCount() { return cellShapeIds.length; }
    public List<WorldOccluderShape> palette() { return palette; }
    public boolean complete() { return complete; }
    public boolean conservativeFallback() { return conservativeFallback; }

    public int shapeId(int x, int y, int z) {
        return Short.toUnsignedInt(cellShapeIds[index(x, y, z)]);
    }

    public WorldOccluderShape shape(int x, int y, int z) {
        int id = shapeId(x, y, z);
        return id == 0 ? null : palette.get(id - 1);
    }

    public boolean containsWorldCell(int x, int y, int z) {
        return x >= minX && y >= minY && z >= minZ
                && x < minX + sizeX && y < minY + sizeY && z < minZ + sizeZ;
    }

    public boolean blocksOpenSegment(double fromX, double fromY, double fromZ,
                                     double toX, double toY, double toZ) {
        return blocksOpenSegment(fromX, fromY, fromZ, toX, toY, toZ,
                Integer.MIN_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE);
    }

    /** Ignores the endpoint cell occupied by a light fixture while retaining every intervening blocker. */
    public boolean blocksOpenSegmentToSource(double fromX, double fromY, double fromZ,
                                             double toX, double toY, double toZ) {
        return blocksOpenSegment(fromX, fromY, fromZ, toX, toY, toZ,
                floorToInt(toX), floorToInt(toY), floorToInt(toZ));
    }

    public boolean blocksOpenSegmentFromSource(double fromX, double fromY, double fromZ,
                                               double toX, double toY, double toZ) {
        return blocksOpenSegment(fromX, fromY, fromZ, toX, toY, toZ,
                floorToInt(fromX), floorToInt(fromY), floorToInt(fromZ));
    }

    private boolean blocksOpenSegment(double fromX, double fromY, double fromZ,
                                      double toX, double toY, double toZ,
                                      int ignoredX, int ignoredY, int ignoredZ) {
        requireFinite(fromX, fromY, fromZ);
        requireFinite(toX, toY, toZ);
        double dx = toX - fromX, dy = toY - fromY, dz = toZ - fromZ;
        int x = floorToInt(fromX), y = floorToInt(fromY), z = floorToInt(fromZ);
        int endX = floorToInt(toX), endY = floorToInt(toY), endZ = floorToInt(toZ);
        int stepX = Integer.compare(endX, x), stepY = Integer.compare(endY, y), stepZ = Integer.compare(endZ, z);
        double tDeltaX = stepX == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dx);
        double tDeltaY = stepY == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dy);
        double tDeltaZ = stepZ == 0 ? Double.POSITIVE_INFINITY : 1.0 / Math.abs(dz);
        double tMaxX = firstBoundaryT(fromX, dx, x, stepX);
        double tMaxY = firstBoundaryT(fromY, dy, y, stepY);
        double tMaxZ = firstBoundaryT(fromZ, dz, z, stepZ);
        long remaining = Math.addExact(Math.addExact(Math.abs((long) endX - x), Math.abs((long) endY - y)),
                Math.addExact(Math.abs((long) endZ - z), 1L));
        while (remaining-- > 0) {
            if ((x != ignoredX || y != ignoredY || z != ignoredZ)
                    && cellBlocksSegment(x, y, z, fromX, fromY, fromZ, toX, toY, toZ)) return true;
            if (x == endX && y == endY && z == endZ) break;
            double next = Math.min(tMaxX, Math.min(tMaxY, tMaxZ));
            if (tMaxX == next) { x += stepX; tMaxX += tDeltaX; }
            if (tMaxY == next) { y += stepY; tMaxY += tDeltaY; }
            if (tMaxZ == next) { z += stepZ; tMaxZ += tDeltaZ; }
        }
        return false;
    }

    public long residentBytes() {
        long bytes = (long) cellShapeIds.length * Short.BYTES;
        for (WorldOccluderShape shape : palette) bytes = Math.addExact(bytes, shape.residentBytes());
        return bytes;
    }

    private int index(int x, int y, int z) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
            throw new IndexOutOfBoundsException("world occluder cell is outside the snapshot");
        }
        return (y * sizeZ + z) * sizeX + x;
    }

    private static int positive(int value) {
        if (value < 1) throw new IllegalArgumentException("snapshot dimensions must be positive");
        return value;
    }

    private static void requireFinite(double x, double y, double z) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            throw new IllegalArgumentException("segment coordinates must be finite");
        }
    }

    private boolean cellBlocksSegment(int x, int y, int z,
                                      double fromX, double fromY, double fromZ,
                                      double toX, double toY, double toZ) {
        if (!containsWorldCell(x, y, z)) return false;
        WorldOccluderShape shape = shape(x - minX, y - minY, z - minZ);
        return shape != null && shape.intersectsOpenSegment(
                fromX, fromY, fromZ, toX, toY, toZ, x, y, z);
    }

    private static int floorToInt(double value) {
        if (value < Integer.MIN_VALUE || value >= (double) Integer.MAX_VALUE + 1.0) {
            throw new IllegalArgumentException("segment coordinate is outside the supported block range");
        }
        return (int) Math.floor(value);
    }

    private static double firstBoundaryT(double origin, double delta, int cell, int step) {
        if (step == 0) return Double.POSITIVE_INFINITY;
        double boundary = step > 0 ? cell + 1.0 : cell;
        return (boundary - origin) / delta;
    }
}
