package com.mine.geometry_node.core.engine.system.asset.transfer.model;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record AssetTransferJobSnapshot(
        UUID jobId,
        AssetTransferDirection direction,
        List<AssetTransferFileSnapshot> files,
        long createdAtEpochMillis
) {
    public AssetTransferJobSnapshot {
        jobId = Objects.requireNonNull(jobId, "jobId");
        direction = Objects.requireNonNull(direction, "direction");
        files = files == null ? List.of() : List.copyOf(files);
        for (AssetTransferFileSnapshot file : files) {
            if (file.direction() != direction) {
                throw new IllegalArgumentException("job contains a file with a different direction");
            }
        }
        createdAtEpochMillis = Math.max(0L, createdAtEpochMillis);
    }

    public long completedFileCount() {
        return files.stream().filter(file -> file.state() == AssetTransferState.COMPLETED).count();
    }

    public long processedFileCount() {
        return files.stream().filter(file -> file.state().isTerminal()).count();
    }

    public boolean isTerminal() {
        return !files.isEmpty() && files.stream().allMatch(file -> file.state().isTerminal());
    }
}
