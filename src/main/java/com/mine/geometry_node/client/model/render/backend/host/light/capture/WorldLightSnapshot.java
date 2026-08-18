package com.mine.geometry_node.client.model.render.backend.host.light.capture;

import com.mine.geometry_node.client.model.runtime.ModelDimensionId;

import java.util.Arrays;
import java.util.Objects;

/** Immutable world-light input captured on the client thread and safe to consume on a worker. */
public final class WorldLightSnapshot {
    private final ModelDimensionId dimension;
    private final long worldRevision;
    private final int minX, minY, minZ, sizeX, sizeY, sizeZ;
    private final byte[] blockLight, skyLight, emission, opacity;

    public WorldLightSnapshot(ModelDimensionId dimension, long worldRevision,
                              int minX, int minY, int minZ, int sizeX, int sizeY, int sizeZ,
                              byte[] blockLight, byte[] skyLight, byte[] emission, byte[] opacity) {
        this.dimension = Objects.requireNonNull(dimension, "dimension");
        if (worldRevision < 0) throw new IllegalArgumentException("worldRevision must not be negative");
        this.worldRevision = worldRevision;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.sizeX = positive(sizeX, "sizeX");
        this.sizeY = positive(sizeY, "sizeY");
        this.sizeZ = positive(sizeZ, "sizeZ");
        int cells = Math.toIntExact(Math.multiplyExact(Math.multiplyExact((long) sizeX, sizeY), sizeZ));
        this.blockLight = copy(blockLight, cells, "blockLight");
        this.skyLight = copy(skyLight, cells, "skyLight");
        this.emission = copy(emission, cells, "emission");
        this.opacity = copy(opacity, cells, "opacity");
    }

    public ModelDimensionId dimension() { return dimension; }
    public long worldRevision() { return worldRevision; }
    public int minX() { return minX; }
    public int minY() { return minY; }
    public int minZ() { return minZ; }
    public int sizeX() { return sizeX; }
    public int sizeY() { return sizeY; }
    public int sizeZ() { return sizeZ; }
    public int cellCount() { return blockLight.length; }
    public long residentBytes() { return (long) cellCount() * 4; }
    public int blockLight(int x, int y, int z) { return unsigned(blockLight[index(x, y, z)]); }
    public int skyLight(int x, int y, int z) { return unsigned(skyLight[index(x, y, z)]); }
    public int emission(int x, int y, int z) { return unsigned(emission[index(x, y, z)]); }
    public int opacity(int x, int y, int z) { return unsigned(opacity[index(x, y, z)]); }

    private int index(int x, int y, int z) {
        if (x < 0 || x >= sizeX || y < 0 || y >= sizeY || z < 0 || z >= sizeZ) {
            throw new IndexOutOfBoundsException("snapshot cell outside bounds");
        }
        return (y * sizeZ + z) * sizeX + x;
    }

    private static int positive(int value, String name) {
        if (value < 1) throw new IllegalArgumentException(name + " must be positive");
        return value;
    }

    private static byte[] copy(byte[] source, int size, String name) {
        Objects.requireNonNull(source, name);
        if (source.length != size) throw new IllegalArgumentException(name + " length must equal cell count");
        return Arrays.copyOf(source, source.length);
    }

    private static int unsigned(byte value) { return Byte.toUnsignedInt(value); }
}
