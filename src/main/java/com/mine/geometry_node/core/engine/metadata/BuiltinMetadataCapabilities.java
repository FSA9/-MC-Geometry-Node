package com.mine.geometry_node.core.engine.metadata;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedMetadataTargets;
import com.mine.geometry_node.core.engine.metadata.target.RuntimeMetadataTargets;
import com.mine.geometry_node.core.engine.system.entity.EntityMetadataAdapter;
import com.mine.geometry_node.core.engine.system.faction.service.FactionMetadataTargets;
import com.mine.geometry_node.core.engine.system.marker.MarkerMetadataTargets;
import com.mine.geometry_node.core.engine.system.scene.SceneMetadataTargets;
import com.mine.geometry_node.core.engine.system.schematic.placement.PlacementMetadataTargets;

import com.mine.geometry_node.core.engine.system.datalibrary.service.DataLibraryMetadata;
import com.mine.geometry_node.core.metadata.MetadataCapabilities;

/** Common-side bootstrap; client-only document adapters are registered by the client. */
public final class BuiltinMetadataCapabilities {
    private static boolean initialized;
    private BuiltinMetadataCapabilities() {}

    public static synchronized void init() {
        if (initialized) return;
        register(MetadataCapabilities.GLOBAL);
        initialized = true;
    }

    /** Also permits isolated registries in tests or application integrations. */
    public static void register(MetadataCapabilities registry) {
        ScopedMetadataTargets.register(registry);
        EntityMetadataAdapter.register(registry);
        SceneMetadataTargets.register(registry);
        PlacementMetadataTargets.register(registry);
        RuntimeMetadataTargets.register(registry);
        FactionMetadataTargets.register(registry);
        MarkerMetadataTargets.register(registry);
        DataLibraryMetadata.register(registry);
    }
}
