package com.mine.geometry_node.client.feature.asset.preview;

import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

public final class AssetPreviewProviders {
    private static final CopyOnWriteArrayList<AssetPreviewProvider> PROVIDERS = new CopyOnWriteArrayList<>();

    static {
        register(new LocalAssetPreviewProvider());
        register(new RemoteAssetPreviewProvider());
    }

    private AssetPreviewProviders() {}

    public static void register(AssetPreviewProvider provider) {
        PROVIDERS.addIfAbsent(Objects.requireNonNull(provider, "provider"));
    }

    public static void unregister(AssetPreviewProvider provider) {
        PROVIDERS.remove(provider);
    }

    public static List<AssetPreviewProvider> all() {
        return List.copyOf(PROVIDERS);
    }

    public static AssetPreviewProvider resolve(AssetEntry entry) {
        for (AssetPreviewProvider provider : PROVIDERS) {
            if (provider.supports(entry)) return provider;
        }
        return null;
    }

    public static CompletableFuture<Void> clearCurrentRemoteCache() {
        return RemoteAssetPreviewProvider.clearCurrentServerCache();
    }

    public static CompletableFuture<Void> clearAllRemoteCaches() {
        return RemoteAssetPreviewProvider.clearAllCaches();
    }
}
