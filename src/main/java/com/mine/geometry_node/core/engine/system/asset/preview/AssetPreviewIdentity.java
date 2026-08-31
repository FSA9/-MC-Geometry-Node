package com.mine.geometry_node.core.engine.system.asset.preview;

import com.mine.geometry_node.core.engine.system.asset.AssetRelativePath;

import java.util.Objects;

public record AssetPreviewIdentity(String remotePath, AssetPreviewKind kind) {
    public AssetPreviewIdentity {
        remotePath = AssetRelativePath.normalize(remotePath, false);
        if (remotePath.length() > AssetPreviewLimits.MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("Preview path is too long");
        }
        kind = Objects.requireNonNull(kind, "kind");
        if (!kind.isConcrete()) {
            throw new IllegalArgumentException("Preview identity requires a concrete preview kind");
        }
    }
}
