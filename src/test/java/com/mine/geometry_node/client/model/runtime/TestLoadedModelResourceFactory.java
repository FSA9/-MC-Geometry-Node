package com.mine.geometry_node.client.model.runtime;

import com.mine.geometry_node.client.model.gpu.TestModelGpuLeaseFactory;
import com.mine.geometry_node.core.engine.system.model.api.*;
import com.mine.geometry_node.core.engine.system.model.domain.*;

import java.util.*;

final class TestLoadedModelResourceFactory {
    private TestLoadedModelResourceFactory() {}

    static LoadedModelResource create() {
        ModelAssetReference asset = new ModelAssetReference(ModelSourceKind.MEMORY, "test", "model",
                new ModelAssetRevision(1, 0, ""));
        ModelBounds bounds = new ModelBounds(ModelVector3.ZERO, ModelVector3.ONE);
        ModelDefinition definition = new ModelDefinition(asset,
                List.of(new ModelScene("scene", List.of(0), Optional.of(bounds))), 0,
                List.of(new ModelNode("node", ModelTransform.Trs.IDENTITY, -1, List.of(), Optional.empty())),
                List.of(), List.of(ModelMaterial.defaultMaterial()), List.of(), List.of(), List.of(), bounds);
        return new LoadedModelResource(definition, TestModelGpuLeaseFactory.create(asset),
                StaticModelRenderMetadata.from(definition), 1, 0, 1);
    }
}
