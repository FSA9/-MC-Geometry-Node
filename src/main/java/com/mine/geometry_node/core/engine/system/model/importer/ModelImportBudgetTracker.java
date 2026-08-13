package com.mine.geometry_node.core.engine.system.model.importer;

import java.util.EnumMap;

public final class ModelImportBudgetTracker {
    private final ModelImportBudget budget;
    private final EnumMap<ModelBudgetResource, Long> usage = new EnumMap<>(ModelBudgetResource.class);

    ModelImportBudgetTracker(ModelImportBudget budget) {
        this.budget = budget;
    }

    public long claim(ModelBudgetResource resource, long amount, String location) throws ModelImportException {
        if (resource == null || amount < 0L) throw new IllegalArgumentException("budget claim is invalid");
        long previous = usage.getOrDefault(resource, 0L);
        final long total;
        try {
            total = Math.addExact(previous, amount);
        } catch (ArithmeticException exception) {
            throw exceeded(resource, location, Long.MAX_VALUE, limit(resource));
        }
        long maximum = limit(resource);
        if (total > maximum) throw exceeded(resource, location, total, maximum);
        usage.put(resource, total);
        return total;
    }

    public long usage(ModelBudgetResource resource) {
        return usage.getOrDefault(resource, 0L);
    }

    private long limit(ModelBudgetResource resource) {
        return switch (resource) {
            case SOURCE_BYTES -> budget.maxSourceBytes();
            case BUFFER_VIEWS -> budget.maxBufferViews();
            case ACCESSORS -> budget.maxAccessors();
            case SCENES -> budget.maxScenes();
            case NODES -> budget.maxNodes();
            case MESHES -> budget.maxMeshes();
            case PRIMITIVES -> budget.maxPrimitives();
            case VERTICES -> budget.maxVertices();
            case INDICES -> budget.maxIndices();
            case TRIANGLES -> budget.maxTriangles();
            case MATERIALS -> budget.maxMaterials();
            case TEXTURES -> budget.maxTextures();
            case SAMPLERS -> budget.maxTextures();
            case IMAGES -> budget.maxImages();
            case ENCODED_IMAGE_BYTES -> budget.maxEncodedImageBytes();
            case DECODED_IMAGE_BYTES -> budget.maxDecodedImageBytes();
            case ANIMATIONS -> budget.maxAnimations();
            case ANIMATION_CHANNELS -> budget.maxAnimationChannels();
            case ANIMATION_KEYFRAMES -> budget.maxAnimationKeyframes();
            case ATTRIBUTE_BYTES -> budget.maxAttributeBytes();
        };
    }

    private static ModelImportException exceeded(ModelBudgetResource resource, String location,
                                                 long actual, long maximum) {
        return new ModelImportException(new ModelImportFailure(ModelImportErrorCode.LIMIT_EXCEEDED,
                location == null ? resource.name() : location, "model import budget exceeded", actual, maximum));
    }
}
