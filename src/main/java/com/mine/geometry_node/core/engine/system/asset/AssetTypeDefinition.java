package com.mine.geometry_node.core.engine.system.asset;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewKind;
import com.mine.geometry_node.core.engine.system.asset.preview.generator.ServerAssetPreviewGeneratorFactory;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Single common definition for one transferable asset type. */
public record AssetTypeDefinition(
        String id,
        AssetTypeRecognizer recognizer,
        AssetPreviewKind previewKind,
        Optional<AssetLifecycleHandler> lifecycleHandler,
        Optional<ServerAssetPreviewGeneratorFactory> previewGeneratorFactory
) {
    public AssetTypeDefinition {
        id = normalizeId(id);
        if (id.isEmpty()) throw new IllegalArgumentException("asset type id must not be empty");
        recognizer = Objects.requireNonNull(recognizer, "recognizer");
        previewKind = previewKind == null ? AssetPreviewKind.NONE : previewKind;
        lifecycleHandler = Objects.requireNonNull(lifecycleHandler, "lifecycleHandler");
        previewGeneratorFactory = Objects.requireNonNull(previewGeneratorFactory, "previewGeneratorFactory");
        if (previewKind.isConcrete() != previewGeneratorFactory.isPresent()) {
            throw new IllegalArgumentException("asset type " + id
                    + " must declare both a concrete preview kind and its generator factory, or neither");
        }
    }

    public boolean supportsCandidatePath(String normalizedPath) {
        return recognizer.supportsCandidatePath(normalizedPath);
    }

    public AssetMetadata inspect(Path file, String normalizedPath) {
        String variantId = recognizer.inspectVariant(file, normalizedPath);
        return variantId == null ? AssetMetadata.UNKNOWN : new AssetMetadata(id, variantId);
    }

    public static String normalizeId(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }
}
