package com.mine.geometry_node.client.integration.asset;

import com.mine.geometry_node.client.feature.asset.repository.AssetRepositoryRegistry;
import com.mine.geometry_node.client.feature.graph.persistence.GraphTagIO;

/** Installs graph metadata reads without making the generic asset repository depend on graph documents. */
public final class AssetBrowserRepositories {
    private AssetBrowserRepositories() { }

    public static void init() {
        AssetRepositoryRegistry.INSTANCE.setLocalTagReader(GraphTagIO::readTags);
    }
}
