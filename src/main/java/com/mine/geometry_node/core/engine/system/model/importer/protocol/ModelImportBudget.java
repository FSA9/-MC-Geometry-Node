package com.mine.geometry_node.core.engine.system.model.importer.protocol;

public record ModelImportBudget(
        long maxSourceBytes, int maxBufferViews, int maxAccessors,
        int maxScenes, int maxNodes, int maxNodeDepth,
        int maxMeshes, int maxPrimitives, long maxVertices, long maxIndices,
        long maxTriangles, int maxMaterials, int maxTextures, int maxImages,
        int maxImageDimension, long maxEncodedImageBytes, long maxDecodedImageBytes,
        int maxAnimations, int maxAnimationChannels,
        long maxAnimationKeyframes, long maxAttributeBytes
) {
    public static final long HARD_MAX_SOURCE_BYTES = 512L << 20;
    public static final ModelImportBudget DEFAULT = new ModelImportBudget(
            128L << 20, 65_536, 262_144, 64, 16_384, 256, 16_384, 65_536,
            5_000_000L, 15_000_000L, 5_000_000L, 4_096, 4_096, 4_096,
            8_192, 128L << 20, 512L << 20, 4_096, 65_536, 10_000_000L, 512L << 20);
    public static final ModelImportBudget LOCAL_PREVIEW = new ModelImportBudget(
            HARD_MAX_SOURCE_BYTES, DEFAULT.maxBufferViews(), DEFAULT.maxAccessors(),
            DEFAULT.maxScenes(), DEFAULT.maxNodes(), DEFAULT.maxNodeDepth(),
            DEFAULT.maxMeshes(), DEFAULT.maxPrimitives(), 10_000_000L, 40_000_000L,
            15_000_000L, DEFAULT.maxMaterials(), DEFAULT.maxTextures(), DEFAULT.maxImages(),
            DEFAULT.maxImageDimension(), DEFAULT.maxEncodedImageBytes(), DEFAULT.maxDecodedImageBytes(),
            DEFAULT.maxAnimations(), DEFAULT.maxAnimationChannels(), DEFAULT.maxAnimationKeyframes(),
            DEFAULT.maxAttributeBytes());

    public ModelImportBudget {
        if (maxSourceBytes < 1L || maxBufferViews < 1 || maxAccessors < 1
                || maxScenes < 1 || maxNodes < 1 || maxNodeDepth < 1
                || maxMeshes < 1 || maxPrimitives < 1 || maxVertices < 1L || maxIndices < 1L
                || maxTriangles < 1L || maxMaterials < 1 || maxTextures < 1 || maxImages < 1
                || maxImageDimension < 1 || maxEncodedImageBytes < 1L || maxDecodedImageBytes < 1L
                || maxAnimations < 0 || maxAnimationChannels < 0
                || maxAnimationKeyframes < 0L || maxAttributeBytes < 1L) {
            throw new IllegalArgumentException("model import budget values are invalid");
        }
    }
}
