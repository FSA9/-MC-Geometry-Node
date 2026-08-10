package com.mine.geometry_node.core.engine.system.asset.preview;

import java.util.Objects;
import java.util.UUID;

public record AssetPreviewRequest(UUID requestId, AssetPreviewRevision revision) {
    public AssetPreviewRequest {
        requestId = Objects.requireNonNull(requestId, "requestId");
        revision = Objects.requireNonNull(revision, "revision");
    }
}
