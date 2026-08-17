package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.client.model.gpu.TestModelGpuLeaseFactory;
import com.mine.geometry_node.core.engine.system.model.identity.*;
import com.mine.geometry_node.core.engine.system.model.domain.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

final class TestLoadedModelResourceFactory {
    private TestLoadedModelResourceFactory() {}

    static LoadedModelResource create() {
        return create(null);
    }

    static LoadedModelResource create(Supplier<CompletableFuture<com.mine.geometry_node.client.model.gpu.ModelGpuLease>> leaseFactory) {
        return create(leaseFactory, () -> {});
    }

    static LoadedModelResource create(
            Supplier<CompletableFuture<com.mine.geometry_node.client.model.gpu.ModelGpuLease>> leaseFactory,
            Runnable cancellation) {
        ModelAssetReference asset = new ModelAssetReference(ModelSourceKind.MEMORY, "test", "model",
                new ModelAssetRevision(1, 0, ""));
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, ModelVector3.ONE);
        ModelDefinition definition = new ModelDefinition(asset,
                List.of(new ModelScene("scene", List.of(0), Optional.of(bounds))), 0,
                List.of(new ModelNode("node", ModelTransform.Trs.IDENTITY, -1, List.of(), Optional.empty())),
                List.of(), List.of(ModelMaterial.defaultMaterial()), List.of(), List.of(), List.of(), bounds);
        Supplier<CompletableFuture<com.mine.geometry_node.client.model.gpu.ModelGpuLease>> effectiveFactory =
                leaseFactory != null ? leaseFactory
                        : () -> CompletableFuture.completedFuture(TestModelGpuLeaseFactory.create(asset));
        return new LoadedModelResource(definition, effectiveFactory, cancellation,
                StaticModelRenderMetadata.from(definition), 1, 0, 1);
    }
}
