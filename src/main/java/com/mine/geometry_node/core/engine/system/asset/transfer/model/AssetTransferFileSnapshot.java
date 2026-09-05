package com.mine.geometry_node.core.engine.system.asset.transfer.model;

import java.util.Objects;
import java.util.UUID;

public record AssetTransferFileSnapshot(
        UUID transferId,
        AssetTransferDirection direction,
        String sourcePath,
        String targetPath,
        AssetTransferState state,
        AssetTransferFailure failure
) {
    public AssetTransferFileSnapshot {
        transferId = Objects.requireNonNull(transferId, "transferId");
        direction = Objects.requireNonNull(direction, "direction");
        sourcePath = Objects.requireNonNullElse(sourcePath, "");
        targetPath = Objects.requireNonNullElse(targetPath, "");
        state = Objects.requireNonNull(state, "state");
        if (state == AssetTransferState.FAILED && failure == null) {
            throw new IllegalArgumentException("failed transfer requires failure metadata");
        }
    }
}
