package com.mine.geometry_node.core.engine.system.asset.preview.generator;

import com.mine.geometry_node.core.engine.system.asset.AssetTypeDefinition;
import com.mine.geometry_node.core.engine.system.asset.preview.store.ServerAssetPreviewStore;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Lazily materializes the preview capabilities declared by common asset type definitions. */
public final class ServerAssetPreviewGeneratorRegistry {
    private final ServerAssetPreviewStore store;
    private final Map<String, ServerAssetPreviewGenerator> generators = new ConcurrentHashMap<>();

    public ServerAssetPreviewGeneratorRegistry(ServerAssetPreviewStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public ServerAssetPreviewGenerator get(AssetTypeDefinition definition) {
        if (definition == null || !definition.previewKind().isConcrete()) return null;
        return generators.computeIfAbsent(definition.id(), ignored ->
                Objects.requireNonNull(definition.previewGeneratorFactory().orElseThrow().create(store),
                        "preview generator factory returned null for " + definition.id()));
    }
}
