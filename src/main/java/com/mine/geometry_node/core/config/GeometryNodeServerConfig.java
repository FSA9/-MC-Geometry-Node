package com.mine.geometry_node.core.config;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewServerConfig;
import com.mine.geometry_node.core.engine.system.visual.image.RuntimeImageServerConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Composes server settings while feature modules retain ownership of their entries. */
public final class GeometryNodeServerConfig {
    public static final ModConfigSpec SPEC;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        AssetPreviewServerConfig.register(builder);
        RuntimeImageServerConfig.register(builder);
        ScopedStateServerConfig.register(builder);
        SPEC = builder.build();
    }

    private GeometryNodeServerConfig() {
    }
}
