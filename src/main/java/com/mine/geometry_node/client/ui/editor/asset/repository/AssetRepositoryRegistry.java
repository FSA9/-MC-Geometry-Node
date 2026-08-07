package com.mine.geometry_node.client.ui.editor.asset.repository;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public final class AssetRepositoryRegistry {
    public static final AssetRepositoryRegistry INSTANCE = new AssetRepositoryRegistry();

    private final Map<AssetSourceKind, AssetRepository> mRepositories = new EnumMap<>(AssetSourceKind.class);

    private AssetRepositoryRegistry() {
        register(new LocalAssetRepository());
        register(new RemoteAssetRepository());
    }

    public synchronized void register(AssetRepository repository) {
        if (repository == null || repository.sourceKind() == null) {
            throw new IllegalArgumentException("asset repository and source kind must not be null");
        }
        AssetRepository previous = mRepositories.putIfAbsent(repository.sourceKind(), repository);
        if (previous != null) {
            throw new IllegalArgumentException("duplicate asset repository: " + repository.sourceKind());
        }
    }

    public synchronized AssetRepository get(AssetSourceKind sourceKind) {
        return mRepositories.get(sourceKind);
    }

    public synchronized List<AssetRepository> all() {
        return List.copyOf(mRepositories.values());
    }
}
