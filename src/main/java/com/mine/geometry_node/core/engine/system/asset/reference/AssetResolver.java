package com.mine.geometry_node.core.engine.system.asset.reference;

import java.nio.file.Path;

/** Resolves a logical asset reference for a concrete runtime environment. */
@FunctionalInterface
public interface AssetResolver {
    /**
     * Resolves a reference to a filesystem path. Sources that are not backed by the
     * local filesystem (for example remote assets) return {@code null}. The
     * context supplies the client game directory and, when available, the
     * current save directory.
     */
    Path resolve(AssetReference reference, AssetResolutionContext context);
}
