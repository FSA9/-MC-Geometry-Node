package com.mine.geometry_node.core.engine.system.asset;

import net.minecraft.server.MinecraftServer;

import java.util.Set;
import java.util.concurrent.CompletionStage;

/** Refreshes runtime state owned by one or more asset types after committed file changes. */
@FunctionalInterface
public interface AssetLifecycleHandler {
    CompletionStage<Void> refresh(MinecraftServer server, Set<String> affectedPaths,
                                  boolean directoryScope);
}
