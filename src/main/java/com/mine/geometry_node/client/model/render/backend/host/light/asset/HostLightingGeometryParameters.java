package com.mine.geometry_node.client.model.render.backend.host.light.asset;

/** Central immutable parameters for source-geometry lighting structures. */
public record HostLightingGeometryParameters(int bvhLeafTriangles, int voxelLongestAxisCells,
                                             long maximumVoxelCells, long maximumTriangleBoxTests,
                                             float degenerateAreaSquared, float rayEpsilon) {
    public static final HostLightingGeometryParameters DEFAULT =
            new HostLightingGeometryParameters(8, 128, 2_097_152L, 32_000_000L, 1.0e-18F, 1.0e-5F);

    public HostLightingGeometryParameters {
        if (bvhLeafTriangles < 1 || voxelLongestAxisCells < 1 || maximumVoxelCells < 1
                || maximumTriangleBoxTests < 1 || !(degenerateAreaSquared > 0) || !(rayEpsilon > 0)) {
            throw new IllegalArgumentException("lighting geometry parameters must be positive");
        }
    }
}
