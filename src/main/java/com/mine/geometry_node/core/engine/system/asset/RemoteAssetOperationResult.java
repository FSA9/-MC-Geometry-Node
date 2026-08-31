package com.mine.geometry_node.core.engine.system.asset;

import java.util.Set;

/** Result of one remote repository mutation and the asset managers it invalidated. */
public record RemoteAssetOperationResult(int affectedEntries, Set<String> affectedTypeIds) {
    public RemoteAssetOperationResult {
        affectedEntries = Math.max(0, affectedEntries);
        affectedTypeIds = affectedTypeIds == null ? Set.of() : Set.copyOf(affectedTypeIds);
    }
}
