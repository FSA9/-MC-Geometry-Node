package com.mine.geometry_node.core.engine.system.model.api;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public record ModelAssetReference(ModelSourceKind sourceKind, String sourceScope,
                                  String normalizedPath, ModelAssetRevision revision) {
    public ModelAssetReference {
        if (sourceKind == null || revision == null) throw new IllegalArgumentException("asset identity fields must not be null");
        sourceScope = normalizeScope(sourceScope);
        normalizedPath = normalizePath(normalizedPath);
        if (sourceKind == ModelSourceKind.REMOTE && sourceScope.isEmpty()) {
            throw new IllegalArgumentException("remote assets require a server identity scope");
        }
    }

    public String cacheIdentity() {
        String revisionIdentity = revision.contentHash().isEmpty()
                ? revision.sourceSize() + ":" + revision.sourceLastModified()
                : revision.contentHash();
        return sourceKind.name().toLowerCase(Locale.ROOT) + ":" + sourceScope + ":" + normalizedPath + ":" + revisionIdentity;
    }

    private static String normalizeScope(String value) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.indexOf('\0') >= 0) throw new IllegalArgumentException("sourceScope contains a null character");
        return normalized;
    }

    private static String normalizePath(String value) {
        String raw = value == null ? "" : value.trim().replace('\\', '/');
        if (raw.indexOf('\0') >= 0) throw new IllegalArgumentException("normalizedPath contains a null character");
        List<String> segments = new ArrayList<>();
        for (String segment : raw.split("/")) {
            if (segment.isEmpty() || segment.equals(".")) continue;
            if (segment.equals("..")) throw new IllegalArgumentException("normalizedPath must not traverse its root");
            segments.add(segment);
        }
        String normalized = String.join("/", segments);
        if (normalized.isEmpty()) throw new IllegalArgumentException("normalizedPath must not be empty");
        return normalized;
    }
}
