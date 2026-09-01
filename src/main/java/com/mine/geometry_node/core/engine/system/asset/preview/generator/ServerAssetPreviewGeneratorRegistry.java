package com.mine.geometry_node.core.engine.system.asset.preview.generator;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewKind;
import com.mine.geometry_node.core.engine.system.asset.preview.store.ServerAssetPreviewStore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Extensible server-side provider registry keyed by common preview capability ids. */
public final class ServerAssetPreviewGeneratorRegistry {
    private final Map<AssetPreviewKind, ServerAssetPreviewGenerator> generators = new ConcurrentHashMap<>();

    /** Registers the built-in providers from one canonical location. */
    public void registerBuiltins(ServerAssetPreviewStore store) {
        register(AssetPreviewKind.IMAGE, new ServerImagePreviewGenerator(store));
        register(AssetPreviewKind.SCHEMATIC, new ServerSchematicPreviewGenerator(store));
    }

    public void register(AssetPreviewKind kind, ServerAssetPreviewGenerator generator) {
        if (kind == null || !kind.isConcrete()) {
            throw new IllegalArgumentException("generator requires a concrete preview kind");
        }
        if (generator == null) throw new IllegalArgumentException("generator must not be null");
        ServerAssetPreviewGenerator previous = generators.putIfAbsent(kind, generator);
        if (previous != null) throw new IllegalArgumentException("duplicate preview generator: " + kind.id());
    }

    public ServerAssetPreviewGenerator get(AssetPreviewKind kind) {
        return generators.get(kind);
    }
}
