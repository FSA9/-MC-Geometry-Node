package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.client.model.gpu.*;
import com.mine.geometry_node.core.engine.system.model.api.ModelAssetReference;
import com.mine.geometry_node.core.engine.system.model.domain.ModelDefinition;

import java.util.Objects;

/** Read-only shared resource view. Only the coordinator can release its ownership lease. */
public final class LoadedModelResource {
    private final ModelAssetReference asset;
    private final ModelDefinition definition;
    private final ModelGpuLease gpuLease;
    private final StaticModelRenderMetadata metadata;
    private final long sourceBytes;
    private final long triangles;
    private final long loadNanos;

    LoadedModelResource(ModelDefinition definition, ModelGpuLease gpuLease,
                        StaticModelRenderMetadata metadata, long sourceBytes,
                        long triangles, long loadNanos) {
        this.definition = Objects.requireNonNull(definition, "definition");
        this.asset = definition.source();
        this.gpuLease = Objects.requireNonNull(gpuLease, "gpuLease");
        this.metadata = Objects.requireNonNull(metadata, "metadata");
        this.sourceBytes = sourceBytes;
        this.triangles = triangles;
        this.loadNanos = loadNanos;
    }

    public ModelAssetReference asset() { return asset; }
    public ModelDefinition definition() { return definition; }
    public ModelGpuResource gpuResource() { return gpuLease.resource(); }
    public StaticModelRenderMetadata metadata() { return metadata; }
    public long sourceBytes() { return sourceBytes; }
    public long triangles() { return triangles; }
    public long loadNanos() { return loadNanos; }
    public boolean isReleased() { return gpuLease.isClosed(); }

    void release() { gpuLease.close(); }
}
