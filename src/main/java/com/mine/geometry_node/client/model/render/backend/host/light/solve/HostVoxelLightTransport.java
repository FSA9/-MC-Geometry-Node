package com.mine.geometry_node.client.model.render.backend.host.light.solve;

import com.mine.geometry_node.client.model.render.backend.host.light.capture.WorldLightSnapshot;
import com.mine.geometry_node.client.model.render.backend.host.light.capture.WorldOccluderShape;
import com.mine.geometry_node.client.model.render.backend.host.light.capture.WorldOccluderSnapshot;
import com.mine.geometry_node.client.model.render.backend.host.light.occlusion.HostModelOccluderInstance;

import java.util.Arrays;
import java.util.Objects;

/** Bounded low-frequency scalar transport over complete immutable captures. */
public final class HostVoxelLightTransport {
    private static final int POSITIVE_X = 0, NEGATIVE_X = 1, POSITIVE_Y = 2;
    private static final int NEGATIVE_Y = 3, POSITIVE_Z = 4, NEGATIVE_Z = 5;
    private static final int[][] DIRECTIONS = {
            {1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}
    };
    private static final int[] OPPOSITE = {NEGATIVE_X, POSITIVE_X, NEGATIVE_Y, POSITIVE_Y,
            NEGATIVE_Z, POSITIVE_Z};
    private static final int[] POSITIVE_DIRECTIONS = {POSITIVE_X, POSITIVE_Y, POSITIVE_Z};

    public Result propagate(WorldLightSnapshot world, WorldOccluderSnapshot worldOccluder,
                            HostModelOccluderInstance modelOccluder, Parameters parameters,
                            Cancellation cancellation) {
        Objects.requireNonNull(world, "world");
        Objects.requireNonNull(worldOccluder, "worldOccluder");
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(cancellation, "cancellation");
        requireMatchingCapture(world, worldOccluder);
        if (!worldOccluder.complete()) throw new IllegalArgumentException("world occluder capture is incomplete");

        int cells = world.cellCount();
        byte[] block = new byte[cells];
        byte[] sky = new byte[cells];
        byte[] nextBlock = new byte[cells];
        byte[] nextSky = new byte[cells];
        byte[] openFaces = new byte[cells];
        initialize(world, worldOccluder, modelOccluder, block, sky, cancellation);
        buildConnectivity(world, worldOccluder, modelOccluder, openFaces, cancellation);
        seedEmissiveNeighbors(world, openFaces, block);

        int completedPasses = 0;
        for (int pass = 0; pass < parameters.passes(); pass++) {
            cancellation.check();
            System.arraycopy(block, 0, nextBlock, 0, cells);
            System.arraycopy(sky, 0, nextSky, 0, cells);
            boolean changed = false;
            for (int y = 0; y < world.sizeY(); y++) {
                for (int z = 0; z < world.sizeZ(); z++) {
                    for (int x = 0; x < world.sizeX(); x++) {
                        int index = index(world, x, y, z);
                        if ((index & 4095) == 0) cancellation.check();
                        int maximumBlock = Byte.toUnsignedInt(block[index]);
                        int maximumSky = Byte.toUnsignedInt(sky[index]);
                        int faces = Byte.toUnsignedInt(openFaces[index]);
                        for (int direction = 0; direction < DIRECTIONS.length; direction++) {
                            if ((faces & (1 << direction)) == 0) continue;
                            int[] delta = DIRECTIONS[direction];
                            int neighbor = index(world, x + delta[0], y + delta[1], z + delta[2]);
                            maximumBlock = Math.max(maximumBlock,
                                    Math.max(0, Byte.toUnsignedInt(block[neighbor]) - 1));
                            maximumSky = Math.max(maximumSky,
                                    Math.max(0, Byte.toUnsignedInt(sky[neighbor]) - 1));
                        }
                        if (maximumBlock != Byte.toUnsignedInt(nextBlock[index])) {
                            nextBlock[index] = (byte) maximumBlock;
                            changed = true;
                        }
                        if (maximumSky != Byte.toUnsignedInt(nextSky[index])) {
                            nextSky[index] = (byte) maximumSky;
                            changed = true;
                        }
                    }
                }
            }
            byte[] swap = block;
            block = nextBlock;
            nextBlock = swap;
            swap = sky;
            sky = nextSky;
            nextSky = swap;
            completedPasses++;
            if (!changed) break;
        }
        return new Result(world, block, sky, completedPasses,
                worldOccluder.conservativeFallback());
    }

    public static long scratchBytes(int cells) {
        if (cells < 0) throw new IllegalArgumentException("cells must not be negative");
        return Math.multiplyExact((long) cells, 5L);
    }

    private static void initialize(WorldLightSnapshot world, WorldOccluderSnapshot worldOccluder,
                                   HostModelOccluderInstance modelOccluder,
                                   byte[] block, byte[] sky, Cancellation cancellation) {
        for (int y = 0; y < world.sizeY(); y++) {
            for (int z = 0; z < world.sizeZ(); z++) {
                for (int x = 0; x < world.sizeX(); x++) {
                    int index = index(world, x, y, z);
                    if ((index & 4095) == 0) cancellation.check();
                    double worldX = world.minX() + x + 0.5;
                    double worldY = world.minY() + y + 0.5;
                    double worldZ = world.minZ() + z + 0.5;
                    boolean occupied = containsCenter(worldOccluder.shape(x, y, z))
                            || modelOccluder != null && modelOccluder.occupiedAtWorld(worldX, worldY, worldZ);
                    int emission = world.emission(x, y, z);
                    block[index] = (byte) emission;
                    if (!occupied && boundary(world, x, y, z)) {
                        block[index] = (byte) Math.max(emission, world.blockLight(x, y, z));
                        sky[index] = (byte) world.skyLight(x, y, z);
                    }
                }
            }
        }
    }

    private static void buildConnectivity(WorldLightSnapshot world, WorldOccluderSnapshot worldOccluder,
                                          HostModelOccluderInstance modelOccluder, byte[] openFaces,
                                          Cancellation cancellation) {
        for (int y = 0; y < world.sizeY(); y++) {
            for (int z = 0; z < world.sizeZ(); z++) {
                for (int x = 0; x < world.sizeX(); x++) {
                    int current = index(world, x, y, z);
                    if ((current & 2047) == 0) cancellation.check();
                    for (int direction : POSITIVE_DIRECTIONS) {
                        int[] delta = DIRECTIONS[direction];
                        int nx = x + delta[0], ny = y + delta[1], nz = z + delta[2];
                        if (nx >= world.sizeX() || ny >= world.sizeY() || nz >= world.sizeZ()) continue;
                        double fromX = world.minX() + x + 0.5;
                        double fromY = world.minY() + y + 0.5;
                        double fromZ = world.minZ() + z + 0.5;
                        double toX = world.minX() + nx + 0.5;
                        double toY = world.minY() + ny + 0.5;
                        double toZ = world.minZ() + nz + 0.5;
                        boolean blocked = (world.emission(x, y, z) > 0
                                ? worldOccluder.blocksOpenSegmentFromSource(fromX, fromY, fromZ, toX, toY, toZ)
                                : world.emission(nx, ny, nz) > 0
                                ? worldOccluder.blocksOpenSegmentToSource(fromX, fromY, fromZ, toX, toY, toZ)
                                : worldOccluder.blocksOpenSegment(fromX, fromY, fromZ, toX, toY, toZ))
                                || modelOccluder != null && modelOccluder.blocksVoxelSegment(
                                fromX, fromY, fromZ, toX, toY, toZ);
                        if (blocked) continue;
                        int neighbor = index(world, nx, ny, nz);
                        openFaces[current] |= (byte) (1 << direction);
                        openFaces[neighbor] |= (byte) (1 << OPPOSITE[direction]);
                    }
                }
            }
        }
    }

    private static void seedEmissiveNeighbors(WorldLightSnapshot world, byte[] openFaces, byte[] block) {
        for (int y = 0; y < world.sizeY(); y++) {
            for (int z = 0; z < world.sizeZ(); z++) {
                for (int x = 0; x < world.sizeX(); x++) {
                    int emission = world.emission(x, y, z);
                    if (emission <= 1) continue;
                    int current = index(world, x, y, z);
                    int faces = Byte.toUnsignedInt(openFaces[current]);
                    for (int direction = 0; direction < DIRECTIONS.length; direction++) {
                        if ((faces & (1 << direction)) == 0) continue;
                        int[] delta = DIRECTIONS[direction];
                        int nx = x + delta[0], ny = y + delta[1], nz = z + delta[2];
                        if (nx < 0 || ny < 0 || nz < 0
                                || nx >= world.sizeX() || ny >= world.sizeY() || nz >= world.sizeZ()) continue;
                        int neighbor = index(world, nx, ny, nz);
                        block[neighbor] = (byte) Math.max(Byte.toUnsignedInt(block[neighbor]), emission - 1);
                    }
                }
            }
        }
    }

    private static boolean containsCenter(WorldOccluderShape shape) {
        return shape != null && shape.contains(0.5F, 0.5F, 0.5F);
    }

    private static boolean boundary(WorldLightSnapshot world, int x, int y, int z) {
        return x == 0 || y == 0 || z == 0
                || x == world.sizeX() - 1 || y == world.sizeY() - 1 || z == world.sizeZ() - 1;
    }

    private static int index(WorldLightSnapshot world, int x, int y, int z) {
        return (y * world.sizeZ() + z) * world.sizeX() + x;
    }

    private static void requireMatchingCapture(WorldLightSnapshot world, WorldOccluderSnapshot occluder) {
        if (!world.dimension().equals(occluder.dimension())
                || world.worldRevision() != occluder.worldRevision()
                || world.minX() != occluder.minX() || world.minY() != occluder.minY()
                || world.minZ() != occluder.minZ() || world.sizeX() != occluder.sizeX()
                || world.sizeY() != occluder.sizeY() || world.sizeZ() != occluder.sizeZ()) {
            throw new IllegalArgumentException("scalar and occluder snapshots do not describe one capture");
        }
    }

    @FunctionalInterface
    public interface Cancellation {
        Cancellation NONE = () -> {};
        void check();
    }

    public record Parameters(int passes) {
        public Parameters {
            if (passes < 1 || passes > 64) throw new IllegalArgumentException("passes must be in [1, 64]");
        }
        public static Parameters defaults() { return new Parameters(16); }
    }

    public static final class Result {
        private final WorldLightSnapshot world;
        private final byte[] block;
        private final byte[] sky;
        private final int completedPasses;
        private final boolean conservativeFallback;

        private Result(WorldLightSnapshot world, byte[] block, byte[] sky,
                       int completedPasses, boolean conservativeFallback) {
            this.world = world;
            this.block = block;
            this.sky = sky;
            this.completedPasses = completedPasses;
            this.conservativeFallback = conservativeFallback;
        }

        public int block(int x, int y, int z) { return Byte.toUnsignedInt(block[index(world, x, y, z)]); }
        public int sky(int x, int y, int z) { return Byte.toUnsignedInt(sky[index(world, x, y, z)]); }
        public int completedPasses() { return completedPasses; }
        public boolean conservativeFallback() { return conservativeFallback; }
    }
}
