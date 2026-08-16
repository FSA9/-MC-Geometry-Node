package com.mine.geometry_node.client.model.asset;

import com.mine.geometry_node.core.engine.system.model.identity.ModelAssetReference;

import java.nio.file.Path;
import java.util.Objects;

public record MaterializedModelAsset(ModelAssetReference reference, Path localBytes) {
    public MaterializedModelAsset {
        reference = Objects.requireNonNull(reference, "reference");
        localBytes = Objects.requireNonNull(localBytes, "localBytes").toAbsolutePath().normalize();
    }
}
