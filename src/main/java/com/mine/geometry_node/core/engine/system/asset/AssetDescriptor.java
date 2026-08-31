package com.mine.geometry_node.core.engine.system.asset;

import java.util.Objects;

/** Lightweight asset description shared by local repositories, remote repositories and the wire protocol. */
public record AssetDescriptor(String path, String name, boolean directory, long size,
                              long lastModified, AssetMetadata metadata) {
    public AssetDescriptor {
        path = Objects.requireNonNullElse(path, "");
        name = Objects.requireNonNullElse(name, "");
        size = Math.max(0L, size);
        lastModified = Math.max(0L, lastModified);
        metadata = metadata == null ? AssetMetadata.UNKNOWN : metadata;
    }
}
