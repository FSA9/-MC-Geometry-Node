package com.mine.geometry_node.core.engine.residency;

import net.minecraft.world.phys.AABB;

/** Conservative XZ coverage. A boundary touched by a bound belongs to both adjacent chunks. */
public record ChunkCoverage(int minX, int minZ, int maxX, int maxZ) {
    public ChunkCoverage {
        if (minX > maxX || minZ > maxZ) throw new IllegalArgumentException("Inverted chunk coverage");
    }

    public static ChunkCoverage of(AABB bounds) {
        if (bounds == null || !Double.isFinite(bounds.minX) || !Double.isFinite(bounds.minY)
                || !Double.isFinite(bounds.minZ) || !Double.isFinite(bounds.maxX)
                || !Double.isFinite(bounds.maxY) || !Double.isFinite(bounds.maxZ))
            throw new IllegalArgumentException("Resource bounds must be finite");
        return new ChunkCoverage(minChunk(bounds.minX), minChunk(bounds.minZ), chunk(bounds.maxX), chunk(bounds.maxZ));
    }

    private static int minChunk(double coordinate) { return (int) Math.floor(Math.nextDown(coordinate / 16.0)); }

    private static int chunk(double coordinate) { return (int) Math.floor(coordinate / 16.0); }
}
