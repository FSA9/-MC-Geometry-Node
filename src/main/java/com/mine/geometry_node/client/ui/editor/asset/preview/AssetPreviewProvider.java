package com.mine.geometry_node.client.ui.editor.asset.preview;

import com.mine.geometry_node.client.ui.editor.asset.model.AssetEntry;
import java.io.File;

public interface AssetPreviewProvider {
    boolean supports(AssetEntry entry);
    Subscription subscribe(AssetEntry entry, Listener listener);

    interface Listener {
        void available(File localSource);
        void unavailable();
    }

    @FunctionalInterface
    interface Subscription extends AutoCloseable {
        Subscription NONE = () -> {};
        @Override void close();
    }
}
