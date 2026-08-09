package com.mine.geometry_node.core.engine.system.asset.transfer.model;

import java.util.Objects;
import java.util.UUID;

public record AssetTransferFileSnapshot(
        UUID transferId,
        AssetTransferDirection direction,
        String sourcePath,
        String targetPath,
        AssetTransferState state,
        long totalBytes,
        long transferredBytes,
        long bytesPerSecond,
        AssetTransferFailure failure
) {
    public AssetTransferFileSnapshot {
        transferId = Objects.requireNonNull(transferId, "transferId");
        direction = Objects.requireNonNull(direction, "direction");
        sourcePath = Objects.requireNonNullElse(sourcePath, "");
        targetPath = Objects.requireNonNullElse(targetPath, "");
        state = Objects.requireNonNull(state, "state");
        totalBytes = Math.max(0L, totalBytes);
        transferredBytes = Math.clamp(transferredBytes, 0L, totalBytes);
        bytesPerSecond = Math.max(0L, bytesPerSecond);
        if (state == AssetTransferState.FAILED && failure == null) {
            throw new IllegalArgumentException("failed transfer requires failure metadata");
        }
    }

    public double progress() {
        return totalBytes == 0L ? (state == AssetTransferState.COMPLETED ? 1.0 : 0.0)
                : (double) transferredBytes / totalBytes;
    }
}
