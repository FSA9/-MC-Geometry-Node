package com.mine.geometry_node.core.engine.system.asset;

public record RemoteAssetEntry(String path, String name, boolean directory, long size,
                               long lastModified, String graphTypeId) {
}
