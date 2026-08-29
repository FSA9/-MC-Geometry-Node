package com.mine.geometry_node.core.engine.runtime;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Server subsystem with shared initialization, ticking and shutdown lifecycle. */
public interface ServerEngine {
    String id();

    default void init() {
    }

    default int tickOrder() {
        return 0;
    }

    default void tickLevel(ServerLevel level) {
    }

    default void shutdown(MinecraftServer server) {
    }
}
