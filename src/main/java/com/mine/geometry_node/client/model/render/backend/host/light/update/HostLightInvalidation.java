package com.mine.geometry_node.client.model.render.backend.host.light.update;

import java.util.Set;

/** Typed invalidation root prevents non-spatial changes from inventing world bounds. */
public sealed interface HostLightInvalidation
        permits HostLightDirtyRegion, HostLightAssetInvalidation,
        HostLightInstanceInvalidation, HostLightOutputInvalidation {
    Set<HostLightInvalidationKind> causes();
    long revision();
}
