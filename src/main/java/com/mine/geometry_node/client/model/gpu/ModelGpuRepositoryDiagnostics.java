package com.mine.geometry_node.client.model.gpu;

/** Immutable repository counters used by the client diagnostic command. */
public record ModelGpuRepositoryDiagnostics(
        long uploadAttempts,
        long completedUploads,
        long failedUploads,
        long cancelledUploads,
        long releasedResources,
        int repositoryEntries,
        int pendingUploads,
        int liveResources,
        long createdBuffers,
        long createdTextures,
        long liveBufferBytes,
        long liveTextureBytes
) {}
