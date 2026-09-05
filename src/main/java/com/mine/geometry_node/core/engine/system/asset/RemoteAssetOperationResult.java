package com.mine.geometry_node.core.engine.system.asset;

import java.util.Set;

/** Result of one remote repository mutation and the asset managers and paths it invalidated. */
public record RemoteAssetOperationResult(int affectedEntries, Set<String> affectedTypeIds,
                                         Set<String> affectedPaths, boolean directoryScope) {
    public RemoteAssetOperationResult {
        affectedEntries = Math.max(0, affectedEntries);
        affectedTypeIds = affectedTypeIds == null ? Set.of() : Set.copyOf(affectedTypeIds);
        affectedPaths = affectedPaths == null ? Set.of() : Set.copyOf(affectedPaths);
    }

    public RemoteAssetOperationResult(int affectedEntries, Set<String> affectedTypeIds) {
        this(affectedEntries, affectedTypeIds, Set.of(), false);
    }
}
