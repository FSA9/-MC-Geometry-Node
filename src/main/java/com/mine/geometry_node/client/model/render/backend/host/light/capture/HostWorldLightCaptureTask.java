package com.mine.geometry_node.client.model.render.backend.host.light.capture;

import com.mine.geometry_node.client.model.render.backend.host.light.source.*;
import com.mine.geometry_node.client.model.runtime.ModelDimensionId;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.*;

/** Client-thread-only incremental capture. No Minecraft object escapes through {@link Result}. */
public final class HostWorldLightCaptureTask {
    public static final int MAXIMUM_CELLS = 4_000_000;
    public static final int MAXIMUM_SOURCES = 16_384;
    private static final Comparator<HostLightSource> WORST_SOURCE_FIRST =
            Comparator.comparingDouble(HostLightSource::intensity)
                    .thenComparing(HostLightSource::id, Comparator.reverseOrder());

    private final ModelDimensionId dimension;
    private final long revision;
    private final int minX, minY, minZ, sizeX, sizeY, sizeZ;
    private final byte[] blockLight, skyLight, emission, opacity;
    private final short[] shapeIds;
    private final ArrayList<WorldOccluderShape> palette = new ArrayList<>();
    private final Map<ShapeKey, Short> paletteIds = new HashMap<>();
    private final PriorityQueue<HostLightSource> sources = new PriorityQueue<>(WORST_SOURCE_FIRST);
    private int cursor;
    private boolean conservativeFallback;
    private boolean sourcesTruncated;

    public HostWorldLightCaptureTask(ModelDimensionId dimension, long revision,
                                     int minX, int minY, int minZ,
                                     int sizeX, int sizeY, int sizeZ) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        if (revision < 0) throw new IllegalArgumentException("revision must not be negative");
        this.revision = revision;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = positive(sizeX);
        this.sizeY = positive(sizeY);
        this.sizeZ = positive(sizeZ);
        int cells = Math.toIntExact(Math.multiplyExact(Math.multiplyExact((long) sizeX, sizeY), sizeZ));
        if (cells > MAXIMUM_CELLS) throw new IllegalArgumentException("world light capture exceeds cell limit");
        blockLight = new byte[cells];
        skyLight = new byte[cells];
        emission = new byte[cells];
        opacity = new byte[cells];
        shapeIds = new short[cells];
    }

    /** Captures at most {@code cellBudget} cells and returns the number consumed. */
    public int capture(ClientLevel level, int cellBudget) {
        Objects.requireNonNull(level, "level");
        if (cellBudget < 0) throw new IllegalArgumentException("cellBudget must not be negative");
        ModelDimensionId current = new ModelDimensionId(level.dimension().identifier().toString());
        if (!dimension.equals(current)) throw new IllegalArgumentException("capture level dimension changed");
        int start = cursor;
        int end = Math.min(shapeIds.length, cursor + cellBudget);
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
        var blockLayer = level.getLightEngine().getLayerListener(LightLayer.BLOCK);
        var skyLayer = level.getLightEngine().getLayerListener(LightLayer.SKY);
        for (; cursor < end; cursor++) {
            int x = cursor % sizeX;
            int yz = cursor / sizeX;
            int z = yz % sizeZ;
            int y = yz / sizeZ;
            int worldX = minX + x, worldY = minY + y, worldZ = minZ + z;
            position.set(worldX, worldY, worldZ);
            BlockState state = level.getBlockState(position);
            int emitted = clampLight(state.getLightEmission(level, position));
            blockLight[cursor] = (byte) clampLight(blockLayer.getLightValue(position));
            skyLight[cursor] = (byte) clampLight(skyLayer.getLightValue(position));
            emission[cursor] = (byte) emitted;
            opacity[cursor] = (byte) clampLight(state.getLightDampening());
            shapeIds[cursor] = shapeId(state.getOcclusionShape());
            if (emitted > 0) addSource(source(worldX, worldY, worldZ, emitted));
        }
        return cursor - start;
    }

    public boolean complete() { return cursor == shapeIds.length; }
    public int capturedCells() { return cursor; }
    public int totalCells() { return shapeIds.length; }

    public Result finish() {
        if (!complete()) throw new IllegalStateException("world light capture is incomplete");
        WorldLightSnapshot scalar = new WorldLightSnapshot(dimension, revision,
                minX, minY, minZ, sizeX, sizeY, sizeZ,
                blockLight, skyLight, emission, opacity);
        WorldOccluderSnapshot occluders = new WorldOccluderSnapshot(dimension, revision,
                minX, minY, minZ, sizeX, sizeY, sizeZ,
                palette, shapeIds, true, conservativeFallback);
        return new Result(scalar, occluders,
                new HostLightSourceSnapshot(dimension, revision, List.copyOf(sources)), sourcesTruncated);
    }

    private short shapeId(VoxelShape shape) {
        if (shape.isEmpty()) return 0;
        List<AABB> boxes = shape.toAabbs();
        if (boxes.isEmpty()) return 0;
        if (boxes.size() > WorldOccluderShape.MAX_BOXES) return fallbackFullCube();
        ArrayList<BoxKey> keys = new ArrayList<>(boxes.size());
        float[] values = new float[boxes.size() * 6];
        int offset = 0;
        for (AABB box : boxes) {
            float minBoxX = (float) box.minX, minBoxY = (float) box.minY, minBoxZ = (float) box.minZ;
            float maxBoxX = (float) box.maxX, maxBoxY = (float) box.maxY, maxBoxZ = (float) box.maxZ;
            if (!validBox(minBoxX, minBoxY, minBoxZ, maxBoxX, maxBoxY, maxBoxZ)) {
                return fallbackFullCube();
            }
            keys.add(new BoxKey(minBoxX, minBoxY, minBoxZ, maxBoxX, maxBoxY, maxBoxZ));
            values[offset++] = minBoxX; values[offset++] = minBoxY; values[offset++] = minBoxZ;
            values[offset++] = maxBoxX; values[offset++] = maxBoxY; values[offset++] = maxBoxZ;
        }
        ShapeKey key = new ShapeKey(List.copyOf(keys), false);
        Short existing = paletteIds.get(key);
        if (existing != null) return existing;
        if (palette.size() == Short.MAX_VALUE) return fallbackFullCube();
        short id = (short) (palette.size() + 1);
        palette.add(new WorldOccluderShape(values, false));
        paletteIds.put(key, id);
        return id;
    }

    private short fallbackFullCube() {
        conservativeFallback = true;
        ShapeKey key = new ShapeKey(List.of(new BoxKey(0, 0, 0, 1, 1, 1)), true);
        Short existing = paletteIds.get(key);
        if (existing != null) return existing;
        if (palette.size() == Short.MAX_VALUE) throw new IllegalStateException("occluder palette exhausted");
        short id = (short) (palette.size() + 1);
        palette.add(WorldOccluderShape.fullCube(true));
        paletteIds.put(key, id);
        return id;
    }

    private HostLightSource source(int x, int y, int z, int light) {
        HostLightSourceId id = new HostLightSourceId(dimension, "world-emission",
                HostLightSourceKind.PLACED_BLOCK,
                Math.floorDiv(x, 16), Math.floorDiv(y, 16), Math.floorDiv(z, 16),
                x + "," + y + "," + z);
        return new HostLightSource(id, revision, x + 0.5, y + 0.5, z + 0.5,
                1, 1, 1, light, light);
    }

    private void addSource(HostLightSource source) {
        if (sources.size() < MAXIMUM_SOURCES) {
            sources.add(source);
            return;
        }
        sourcesTruncated = true;
        HostLightSource worst = sources.peek();
        if (WORST_SOURCE_FIRST.compare(source, worst) > 0) {
            sources.remove();
            sources.add(source);
        }
    }

    private static boolean validBox(float minX, float minY, float minZ,
                                    float maxX, float maxY, float maxZ) {
        return Float.isFinite(minX) && Float.isFinite(minY) && Float.isFinite(minZ)
                && Float.isFinite(maxX) && Float.isFinite(maxY) && Float.isFinite(maxZ)
                && minX >= 0 && minY >= 0 && minZ >= 0
                && maxX <= 1 && maxY <= 1 && maxZ <= 1
                && minX < maxX && minY < maxY && minZ < maxZ;
    }

    private static int positive(int value) {
        if (value < 1) throw new IllegalArgumentException("capture dimensions must be positive");
        return value;
    }

    private static int clampLight(int value) { return Math.max(0, Math.min(15, value)); }

    public record Result(WorldLightSnapshot scalar, WorldOccluderSnapshot occluders,
                         HostLightSourceSnapshot sources, boolean sourcesTruncated) {
        public Result {
            Objects.requireNonNull(scalar, "scalar");
            Objects.requireNonNull(occluders, "occluders");
            Objects.requireNonNull(sources, "sources");
        }
    }

    private record ShapeKey(List<BoxKey> boxes, boolean fallback) {}
    private record BoxKey(float minX, float minY, float minZ, float maxX, float maxY, float maxZ) {}
}
