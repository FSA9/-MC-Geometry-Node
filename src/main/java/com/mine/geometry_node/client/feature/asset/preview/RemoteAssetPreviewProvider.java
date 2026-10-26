package com.mine.geometry_node.client.feature.asset.preview;

import com.mine.geometry_node.client.platform.ClientThread;
import com.mine.geometry_node.client.feature.asset.preview.ClientAssetPreviewService;
import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import com.mine.geometry_node.client.feature.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.feature.asset.model.AssetTypeAction;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewDescriptor;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewIdentity;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewKind;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewResultCode;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewRevision;
import java.util.concurrent.CompletableFuture;

final class RemoteAssetPreviewProvider implements AssetPreviewProvider {
    @Override
    public boolean supports(AssetEntry entry) {
        return entry != null && entry.sourceKind() == AssetSourceKind.REMOTE && !entry.isDirectory()
                && entry.supports(AssetTypeAction.PREVIEW) && entry.size() >= 0L && entry.lastModified() >= 0L
                && entry.type().previewKind().isConcrete();
    }

    @Override
    public Subscription subscribe(AssetEntry entry, Listener listener) {
        if (!supports(entry)) {
            ClientThread.execute(listener::unavailable);
            return Subscription.NONE;
        }
        AssetPreviewRevision revision = AssetPreviewRevision.current(
                new AssetPreviewIdentity(entry.path(), entry.type().previewKind()), entry.size(), entry.lastModified());
        ClientAssetPreviewService.Subscription subscription = ClientAssetPreviewService.INSTANCE.subscribe(revision,
                new ClientAssetPreviewService.Listener() {
                    @Override
                    public void available(AssetPreviewDescriptor descriptor, java.nio.file.Path localArtifact) {
                        listener.available(new AssetPreviewArtifact(localArtifact.toFile(),
                                entry.type().previewKind(), descriptor.format() == null ? "" : descriptor.format().extension()));
                    }

                    @Override
                    public void unavailable(AssetPreviewResultCode code, String detail) {
                        listener.unavailable();
                    }
                });
        return subscription::close;
    }

    static CompletableFuture<Void> clearCurrentServerCache() {
        return ClientAssetPreviewService.INSTANCE.clearCurrentServerCache();
    }

    static CompletableFuture<Void> clearAllCaches() {
        return ClientAssetPreviewService.INSTANCE.clearAllCaches();
    }
}
