package com.mine.geometry_node.client.model.render;

/** Immutable, render-thread-owned limits for the P4 static model backend. */
public record ModelRenderBudget(
        long maxResidentCpuBytes,
        long maxResidentGpuBytes,
        long maxResidentTextureBytes,
        long maxUploadBytesPerFrame,
        long maxUploadNanosPerFrame,
        int maxVisiblePrimitives,
        int maxTransparentTriangles,
        double maxDrawDistance
) {
    public static final long MIB = 1024L * 1024L;
    public static final ModelRenderBudget DEFAULT = new ModelRenderBudget(
            1024L * MIB,
            1024L * MIB,
            512L * MIB,
            32L * MIB,
            2_000_000L,
            1_000_000,
            500_000,
            2048.0
    );

    public ModelRenderBudget {
        if (maxResidentCpuBytes < 1 || maxResidentGpuBytes < 1 || maxResidentTextureBytes < 1
                || maxUploadBytesPerFrame < 1 || maxUploadNanosPerFrame < 1
                || maxVisiblePrimitives < 1 || maxTransparentTriangles < 1
                || !Double.isFinite(maxDrawDistance) || maxDrawDistance <= 0.0) {
            throw new IllegalArgumentException("invalid model render budget");
        }
    }
}
