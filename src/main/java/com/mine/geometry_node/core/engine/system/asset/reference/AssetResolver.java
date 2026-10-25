package com.mine.geometry_node.core.engine.system.asset.reference;

import java.nio.file.Path;

/** Resolves a logical asset reference for a concrete runtime environment. */
@FunctionalInterface
public interface AssetResolver {
    /**
     * Resolves a reference to a filesystem path. Sources that are not backed by the
     * local filesystem (for example remote assets) return {@code null}.
     */
    Path resolve(AssetReference reference, Path gameDirectory);
}
