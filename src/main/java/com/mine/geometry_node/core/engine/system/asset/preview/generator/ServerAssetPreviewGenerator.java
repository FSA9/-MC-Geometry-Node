package com.mine.geometry_node.core.engine.system.asset.preview.generator;

import com.mine.geometry_node.core.engine.system.asset.preview.AssetPreviewRevision;
import com.mine.geometry_node.core.engine.system.asset.preview.store.ServerAssetPreviewStore;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.file.Path;

/** Generates one immutable preview artifact from the authoritative server asset. */
@FunctionalInterface
public interface ServerAssetPreviewGenerator {
    ServerAssetPreviewStore.StoredPreview generate(MinecraftServer server, Path source,
                                                   AssetPreviewRevision revision) throws IOException;
}
