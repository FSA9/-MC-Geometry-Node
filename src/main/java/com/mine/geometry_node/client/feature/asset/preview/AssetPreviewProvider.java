package com.mine.geometry_node.client.feature.asset.preview;

import com.mine.geometry_node.client.feature.asset.model.AssetEntry;
import java.io.File;

public interface AssetPreviewProvider {
    boolean supports(AssetEntry entry);

    /** Delivers listener callbacks on the Minecraft client thread. */
    Subscription subscribe(AssetEntry entry, Listener listener);

    interface Listener {
        void available(File localSource);

        default void available(AssetPreviewArtifact artifact) {
            if (artifact != null) available(artifact.file());
        }

        void unavailable();
    }

    @FunctionalInterface
    interface Subscription extends AutoCloseable {
        Subscription NONE = () -> {};
        @Override void close();
    }
}
