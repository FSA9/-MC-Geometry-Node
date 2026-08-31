package com.mine.geometry_node.core.engine.system.asset;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewKind;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** Single common definition for one transferable asset type. */
public record AssetTypeDefinition(
        String id,
        AssetTypeRecognizer recognizer,
        AssetPreviewKind previewKind
) {
    public AssetTypeDefinition {
        id = normalizeId(id);
        if (id.isEmpty()) throw new IllegalArgumentException("asset type id must not be empty");
        recognizer = Objects.requireNonNull(recognizer, "recognizer");
        previewKind = previewKind == null ? AssetPreviewKind.NONE : previewKind;
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
