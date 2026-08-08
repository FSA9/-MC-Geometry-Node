package com.mine.geometry_node.api;

import com.mine.geometry_node.core.engine.system.marker.MarkerType;

/**
 * Common-side marker type registration exposed to GeometryNode addons.
 */
public interface MarkerRegistrationContext {
    String addonId();

    void registerMarkerType(MarkerType type);

    default void register(MarkerType type) {
        registerMarkerType(type);
    }
}
