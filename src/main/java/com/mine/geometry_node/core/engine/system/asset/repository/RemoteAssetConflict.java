package com.mine.geometry_node.core.engine.system.asset.repository;

public record RemoteAssetConflict(String sourcePath, String targetPath, boolean directory) {
}
