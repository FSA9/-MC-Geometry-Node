package com.mine.geometry_node.client.ui.editor.asset.preview;

import com.mine.geometry_node.client.asset.preview.ClientAssetPreviewService;
import com.mine.geometry_node.client.ui.editor.asset.image.ImageThumbnailView;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetSourceKind;
import com.mine.geometry_node.client.ui.editor.asset.model.AssetTypeAction;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewDescriptor;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewIdentity;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewResultCode;
import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewRevision;
import java.util.concurrent.CompletableFuture;

final class RemoteAssetPreviewProvider implements AssetPreviewProvider {
    @Override
    public boolean supports(AssetEntry entry) {
        return entry != null && entry.sourceKind() == AssetSourceKind.REMOTE && !entry.isDirectory()
                && entry.supports(AssetTypeAction.PREVIEW) && entry.size() >= 0L && entry.lastModified() >= 0L
                && coreKind(entry) != null;
    }

    @Override
    public Subscription subscribe(AssetEntry entry, Listener listener) {
        if (!supports(entry)) {
            listener.unavailable();
            return Subscription.NONE;
        }
        AssetPreviewRevision revision = AssetPreviewRevision.current(
                new AssetPreviewIdentity(entry.path(), coreKind(entry)), entry.size(), entry.lastModified());
        ClientAssetPreviewService.Subscription subscription = ClientAssetPreviewService.INSTANCE.subscribe(revision,
                new ClientAssetPreviewService.Listener() {
                    @Override
                    public void available(AssetPreviewDescriptor descriptor, java.nio.file.Path localArtifact) {
                        listener.available(localArtifact.toFile());
                    }

                    @Override
                    public void unavailable(AssetPreviewResultCode code, String detail) {
                        listener.unavailable();
                    }
                });
        return subscription::close;
    }

    static CompletableFuture<Void> clearCurrentServerCache() {
        ImageThumbnailView.clearCache();
        return ClientAssetPreviewService.INSTANCE.clearCurrentServerCache();
    }

    static CompletableFuture<Void> clearAllCaches() {
        ImageThumbnailView.clearCache();
        return ClientAssetPreviewService.INSTANCE.clearAllCaches();
    }

    private static com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewKind coreKind(AssetEntry entry) {
        return switch (entry.type().previewKind()) {
            case IMAGE -> com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewKind.IMAGE;
            case SCHEMATIC -> com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewKind.SCHEMATIC;
            case NONE -> null;
        };
    }
}
