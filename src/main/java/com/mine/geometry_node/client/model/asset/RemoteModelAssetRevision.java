package com.mine.geometry_node.client.model.asset;

import com.mine.geometry_node.core.engine.graph.storage.GraphPathMapper;

public record RemoteModelAssetRevision(String remotePath, long sourceSize, long sourceLastModified) {
    public RemoteModelAssetRevision {
        remotePath = GraphPathMapper.normalizeRelativePath(remotePath == null ? "" : remotePath, false);
        if (remotePath.isEmpty() || !remotePath.toLowerCase(java.util.Locale.ROOT).endsWith(".glb")) {
            throw new IllegalArgumentException("remote model revision requires a .glb path");
        }
        if (sourceSize < 0 || sourceLastModified < 0) {
            throw new IllegalArgumentException("remote model revision values must not be negative");
        }
    }

    public String canonical() { return remotePath + '\0' + sourceSize + '\0' + sourceLastModified + "\0v1"; }
}
