package com.mine.geometry_node.core.node.value.geometry;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

/**
 * Compact geometry payload for node graphs.
 * <p>
 * Keep primitives parametric for as long as possible. Baking dense vertex or
 * block lists should only happen at the boundary that needs them.
 */
public final class GeometryValue {
    public static final GeometryValue EMPTY = new GeometryValue(new Primitive[0]);

    private static final int MAX_SAFE_AXIS_BLOCKS = 4096;
    private static final long SATURATED_COUNT = Long.MAX_VALUE;

    private final Primitive[] primitives;

    private GeometryValue(Primitive[] primitives) {
        this.primitives = primitives;
    }

    public static GeometryValue of(Primitive primitive) {
        if (primitive == null) {
            return EMPTY;
        }
        return new GeometryValue(new Primitive[]{primitive});
    }

    public boolean isEmpty() {
        return primitives.length == 0;
    }

    public int primitiveCount() {
        return primitives.length;
    }

    public Primitive[] primitives() {
        return primitives.clone();
    }

    public long estimateBlockCount(VoxelMode mode, @Nullable Vec3 translation) {
        VoxelMode safeMode = mode != null ? mode : VoxelMode.SURFACE;
        long total = 0L;
        for (Primitive primitive : primitives) {
            long estimate = primitive.estimateBlockCount(safeMode);
            total = saturatedAdd(total, estimate);
            if (total == SATURATED_COUNT) {
                return SATURATED_COUNT;
            }
        }
        return total;
    }

    public boolean forEachBlockPosition(VoxelMode mode, @Nullable Vec3 translation, BlockPositionConsumer consumer) {
        if (consumer == null) {
            return true;
        }
        VoxelMode safeMode = mode != null ? mode : VoxelMode.SURFACE;
        double tx = translation != null ? translation.x : 0.0D;
        double ty = translation != null ? translation.y : 0.0D;
        double tz = translation != null ? translation.z : 0.0D;
        for (Primitive primitive : primitives) {
            if (!primitive.forEachBlockPosition(safeMode, tx, ty, tz, consumer)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "GeometryValue[primitives=" + primitives.length + "]";
    }

    private static long saturatedAdd(long left, long right) {
        if (left == SATURATED_COUNT || right == SATURATED_COUNT) {
            return SATURATED_COUNT;
        }
        long result = left + right;
        return result < 0L || result < left ? SATURATED_COUNT : result;
    }

    private static long saturatedMultiply(long left, long right) {
        if (left < 0L || right < 0L) {
            return SATURATED_COUNT;
        }
        if (left == 0L || right == 0L) {
            return 0L;
        }
        if (left > SATURATED_COUNT / right) {
            return SATURATED_COUNT;
        }
        return left * right;
    }

    private static int blockCount(double size) {
        if (!Double.isFinite(size)) {
            return 1;
        }
        double abs = Math.abs(size);
        if (abs > MAX_SAFE_AXIS_BLOCKS) {
            return MAX_SAFE_AXIS_BLOCKS + 1;
        }
        return Math.max(1, (int) Math.round(abs));
    }

    private static int blockCenter(double value) {
        if (!Double.isFinite(value)) {
            return 0;
        }
        return (int) Math.floor(value);
    }

    private static int minCentered(int center, int count) {
        return center - (count - 1) / 2;
    }

    private static boolean axisTooLarge(int count) {
        return count > MAX_SAFE_AXIS_BLOCKS;
    }

    @FunctionalInterface
    public interface BlockPositionConsumer {
        boolean accept(long packedBlockPos);
    }

    public enum VoxelMode {
        SURFACE("surface"),
        VOLUME("volume");

        private final String id;

        VoxelMode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static VoxelMode fromId(@Nullable String id) {
            if (id != null) {
                for (VoxelMode mode : values()) {
                    if (mode.id.equalsIgnoreCase(id)) {
                        return mode;
                    }
                }
            }
            return SURFACE;
        }
    }

    public enum PrimitiveType {
        CUBE,
        CYLINDER
    }

    public enum CylinderFillType {
        NONE("none"),
        TRIANGLE("triangle"),
        NGON("ngon");

        public static final String[] OPTIONS = {NONE.id, TRIANGLE.id, NGON.id};

        private final String id;

        CylinderFillType(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static CylinderFillType fromId(@Nullable String id) {
            if (id != null) {
                for (CylinderFillType fillType : values()) {
                    if (fillType.id.equalsIgnoreCase(id)) {
                        return fillType;
                    }
                }
            }
            return NGON;
        }
    }

    public static final class Primitive {
        private final PrimitiveType type;
        private final float centerX;
        private final float centerY;
        private final float centerZ;
        private final float sizeX;
        private final float sizeY;
        private final float sizeZ;
        private final int verticesX;
        private final int verticesY;
        private final int verticesZ;
        private final int radialVertices;
        private final int sideSegments;
        private final int fillSegments;
        private final CylinderFillType fillType;

        private Primitive(PrimitiveType type,
                          float centerX,
                          float centerY,
                          float centerZ,
                          float sizeX,
                          float sizeY,
                          float sizeZ,
                          int verticesX,
                          int verticesY,
                          int verticesZ,
                          int radialVertices,
                          int sideSegments,
                          int fillSegments,
                          CylinderFillType fillType) {
            this.type = type;
            this.centerX = centerX;
            this.centerY = centerY;
            this.centerZ = centerZ;
            this.sizeX = sanitizePositive(sizeX, 1.0f);
            this.sizeY = sanitizePositive(sizeY, 1.0f);
            this.sizeZ = sanitizePositive(sizeZ, 1.0f);
            this.verticesX = Math.max(1, verticesX);
            this.verticesY = Math.max(1, verticesY);
            this.verticesZ = Math.max(1, verticesZ);
            this.radialVertices = Math.max(3, radialVertices);
            this.sideSegments = Math.max(1, sideSegments);
            this.fillSegments = Math.max(1, fillSegments);
            this.fillType = fillType != null ? fillType : CylinderFillType.NGON;
        }

        public static Primitive cube(Vec3 center, Vec3 size, int verticesX, int verticesY, int verticesZ) {
            Vec3 safeCenter = center != null ? center : Vec3.ZERO;
            Vec3 safeSize = size != null ? size : new Vec3(1.0D, 1.0D, 1.0D);
            return new Primitive(
                    PrimitiveType.CUBE,
                    (float) safeCenter.x, (float) safeCenter.y, (float) safeCenter.z,
                    (float) safeSize.x, (float) safeSize.y, (float) safeSize.z,
                    verticesX, verticesY, verticesZ,
                    0, 0, 0, CylinderFillType.NGON
            );
        }

        public static Primitive cylinder(Vec3 center,
                                         int radialVertices,
                                         int sideSegments,
                                         int fillSegments,
                                         float radius,
                                         float depth,
                                         CylinderFillType fillType) {
            Vec3 safeCenter = center != null ? center : Vec3.ZERO;
            float safeRadius = sanitizePositive(radius, 1.0f);
            float safeDepth = sanitizePositive(depth, 2.0f);
            return new Primitive(
                    PrimitiveType.CYLINDER,
                    (float) safeCenter.x, (float) safeCenter.y, (float) safeCenter.z,
                    safeRadius * 2.0f, safeDepth, safeRadius * 2.0f,
                    1, 1, 1,
                    radialVertices, sideSegments, fillSegments, fillType
            );
        }

        public PrimitiveType type() {
            return type;
        }

        public Vec3 center() {
            return new Vec3(centerX, centerY, centerZ);
        }

        public Vec3 size() {
            return new Vec3(sizeX, sizeY, sizeZ);
        }

        public int verticesX() {
            return verticesX;
        }

        public int verticesY() {
            return verticesY;
        }

        public int verticesZ() {
            return verticesZ;
        }

        public int radialVertices() {
            return radialVertices;
        }

        public int sideSegments() {
            return sideSegments;
        }

        public int fillSegments() {
            return fillSegments;
        }

        public CylinderFillType fillType() {
            return fillType;
        }

        private long estimateBlockCount(VoxelMode mode) {
            return switch (type) {
                case CUBE -> estimateCubeBlocks(mode);
                case CYLINDER -> estimateCylinderBlocks(mode);
            };
        }

        private boolean forEachBlockPosition(VoxelMode mode, double tx, double ty, double tz, BlockPositionConsumer consumer) {
            return switch (type) {
                case CUBE -> forEachCubeBlock(mode, tx, ty, tz, consumer);
                case CYLINDER -> forEachCylinderBlock(mode, tx, ty, tz, consumer);
            };
        }

        private long estimateCubeBlocks(VoxelMode mode) {
            int countX = blockCount(sizeX);
            int countY = blockCount(sizeY);
            int countZ = blockCount(sizeZ);
            if (axisTooLarge(countX) || axisTooLarge(countY) || axisTooLarge(countZ)) {
                return SATURATED_COUNT;
            }

            long volume = saturatedMultiply(saturatedMultiply(countX, countY), countZ);
            if (mode == VoxelMode.VOLUME) {
                return volume;
            }

            long inner = saturatedMultiply(
                    saturatedMultiply(Math.max(0, countX - 2), Math.max(0, countY - 2)),
                    Math.max(0, countZ - 2)
            );
            return Math.max(0L, volume - inner);
        }

        private boolean forEachCubeBlock(VoxelMode mode, double tx, double ty, double tz, BlockPositionConsumer consumer) {
            int countX = blockCount(sizeX);
            int countY = blockCount(sizeY);
            int countZ = blockCount(sizeZ);
            if (axisTooLarge(countX) || axisTooLarge(countY) || axisTooLarge(countZ)) {
                return false;
            }

            int centerBlockX = blockCenter(centerX + tx);
            int centerBlockY = blockCenter(centerY + ty);
            int centerBlockZ = blockCenter(centerZ + tz);
            int minX = minCentered(centerBlockX, countX);
            int minY = minCentered(centerBlockY, countY);
            int minZ = minCentered(centerBlockZ, countZ);
            int maxX = minX + countX - 1;
            int maxY = minY + countY - 1;
            int maxZ = minZ + countZ - 1;

            for (int x = minX; x <= maxX; x++) {
                boolean xEdge = x == minX || x == maxX;
                for (int y = minY; y <= maxY; y++) {
                    boolean yEdge = y == minY || y == maxY;
                    for (int z = minZ; z <= maxZ; z++) {
                        if (mode == VoxelMode.SURFACE && !xEdge && !yEdge && z != minZ && z != maxZ) {
                            continue;
                        }
                        if (!consumer.accept(BlockPos.asLong(x, y, z))) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private long estimateCylinderBlocks(VoxelMode mode) {
            int radius = Math.max(1, blockCount(sizeX) / 2);
            int height = blockCount(sizeY);
            if (axisTooLarge(radius * 2 + 1) || axisTooLarge(height)) {
                return SATURATED_COUNT;
            }

            long diameter = radius * 2L + 1L;
            long diskUpperBound = saturatedMultiply(diameter, diameter);

            if (mode == VoxelMode.VOLUME) {
                return saturatedMultiply(diskUpperBound, height);
            }

            long topBottom = height > 1 ? saturatedMultiply(diskUpperBound, 2L) : diskUpperBound;
            long innerDiameter = Math.max(0L, diameter - 2L);
            long sideRingUpperBound = diskUpperBound - saturatedMultiply(innerDiameter, innerDiameter);
            long sideMiddle = saturatedMultiply(Math.max(0, height - 2L), sideRingUpperBound);
            return saturatedAdd(topBottom, sideMiddle);
        }

        private boolean forEachCylinderBlock(VoxelMode mode, double tx, double ty, double tz, BlockPositionConsumer consumer) {
            int radius = Math.max(1, blockCount(sizeX) / 2);
            int height = blockCount(sizeY);
            if (axisTooLarge(radius * 2 + 1) || axisTooLarge(height)) {
                return false;
            }

            int centerBlockX = blockCenter(centerX + tx);
            int centerBlockY = blockCenter(centerY + ty);
            int centerBlockZ = blockCenter(centerZ + tz);
            int minY = minCentered(centerBlockY, height);
            int maxY = minY + height - 1;
            int innerRadius = Math.max(0, radius - 1);
            int radiusSqr = radius * radius;
            int innerSqr = innerRadius * innerRadius;

            for (int y = minY; y <= maxY; y++) {
                boolean cap = y == minY || y == maxY;
                for (int dx = -radius; dx <= radius; dx++) {
                    int xSqr = dx * dx;
                    int x = centerBlockX + dx;
                    for (int dz = -radius; dz <= radius; dz++) {
                        int distSqr = xSqr + dz * dz;
                        if (distSqr > radiusSqr) {
                            continue;
                        }
                        if (mode == VoxelMode.SURFACE && !cap && distSqr < innerSqr) {
                            continue;
                        }
                        if (!consumer.accept(BlockPos.asLong(x, y, centerBlockZ + dz))) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }

        private static float sanitizePositive(float value, float fallback) {
            if (!Float.isFinite(value)) {
                return fallback;
            }
            return Math.max(0.001f, Math.abs(value));
        }

        @Override
        public String toString() {
            return switch (type) {
                case CUBE -> String.format(Locale.ROOT, "Cube[size=%.3f,%.3f,%.3f]", sizeX, sizeY, sizeZ);
                case CYLINDER -> String.format(Locale.ROOT, "Cylinder[r=%.3f,depth=%.3f,vertices=%d]", sizeX * 0.5f, sizeY, radialVertices);
            };
        }
    }
}
