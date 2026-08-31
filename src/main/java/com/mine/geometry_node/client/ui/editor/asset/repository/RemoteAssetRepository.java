package com.mine.geometry_node.client.ui.editor.asset.repository;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetTypeRegistry;
import com.mine.geometry_node.client.asset.remote.RemoteAssetClient;
import com.mine.geometry_node.core.engine.system.asset.RemoteAssetEntry;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketRemoteAssetListRequest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class RemoteAssetRepository implements AssetRepository {
    @Override
    public AssetSourceKind sourceKind() {
        return AssetSourceKind.REMOTE;
    }

    @Override
    public boolean supports(AssetRepositoryOperation operation) {
        return switch (operation) {
            case BROWSE -> RemoteAssetClient.canBrowse();
            case UPLOAD -> RemoteAssetClient.canUpload();
            case DOWNLOAD -> RemoteAssetClient.canDownload();
            case MANAGE, CREATE -> RemoteAssetClient.canManage();
        };
    }

    @Override
    public AssetRequest browse(AssetBrowseRequest request, Consumer<AssetListing> onResult) {
        if (!(request.location() instanceof AssetLocation.Remote location)) {
            throw new IllegalArgumentException("remote repository requires a remote location");
        }
        Consumer<AssetListing> callback = onResult != null ? onResult : ignored -> {};
        if (!supports(AssetRepositoryOperation.BROWSE)) {
            callback.accept(AssetListing.failure(location));
            return AssetRequest.NONE;
        }

        int requestId = RemoteAssetClient.nextRequestId();
        AtomicBoolean cancelled = new AtomicBoolean(false);
        RemoteAssetClient.onList(requestId, response -> {
            if (cancelled.get()) return;
            if (!response.success()) {
                callback.accept(AssetListing.failure(location));
                return;
            }
            AssetLocation.Remote responseLocation = new AssetLocation.Remote(response.directory(), false);
            callback.accept(toListing(responseLocation, response.entries(), request.query()));
        });
        NetworkHandler.sendToServer(new PacketRemoteAssetListRequest(
                requestId, location.directory(), location.createIfMissing()));
        return () -> {
            if (cancelled.compareAndSet(false, true)) RemoteAssetClient.cancel(requestId);
        };
    }

    private static AssetListing toListing(AssetLocation.Remote location, List<RemoteAssetEntry> source,
                                          AssetQuery query) {
        if (!query.tag().isEmpty()) return AssetListing.empty(location);
        String nameQuery = query.normalizedName();
        List<AssetEntry> entries = new ArrayList<>();
        Map<String, String> graphTypesByKey = new HashMap<>();
        for (RemoteAssetEntry remote : source == null ? List.<RemoteAssetEntry>of() : source) {
            if (!nameQuery.isEmpty() && !remote.name().toLowerCase(Locale.ROOT).contains(nameQuery)) continue;
            AssetEntry entry = AssetEntry.remote(remote.path(), remote.name(), remote.directory(),
                    remote.size(), remote.lastModified(), remote.assetTypeId());
            if (entry.type() == null || !entry.type().displayInBrowser()
                    || !entry.type().supportsSource(AssetSourceKind.REMOTE)) continue;
            entries.add(entry);
            if (AssetTypeRegistry.GRAPH_ID.equals(entry.type().id()) && !remote.variantId().isBlank()) {
                graphTypesByKey.put(entry.key(), remote.variantId());
            }
        }
        return new AssetListing(true, location, entries, Map.of(), graphTypesByKey);
    }
}
