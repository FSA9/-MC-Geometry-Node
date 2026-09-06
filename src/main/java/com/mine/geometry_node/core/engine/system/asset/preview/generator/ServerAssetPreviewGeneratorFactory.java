package com.mine.geometry_node.core.engine.system.asset.preview.generator;

import com.mine.geometry_node.core.engine.system.asset.preview.store.ServerAssetPreviewStore;

/** Creates a preview generator with the server preview store owned by the preview service. */
@FunctionalInterface
public interface ServerAssetPreviewGeneratorFactory {
    ServerAssetPreviewGenerator create(ServerAssetPreviewStore store);
}
