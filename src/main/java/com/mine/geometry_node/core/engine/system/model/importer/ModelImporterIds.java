package com.mine.geometry_node.core.engine.system.model.importer;

import java.util.Locale;

final class ModelImporterIds {
    private ModelImporterIds() { }

    static String normalize(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")) {
            throw new IllegalArgumentException("invalid model importer id: " + id);
        }
        return normalized;
    }
}
