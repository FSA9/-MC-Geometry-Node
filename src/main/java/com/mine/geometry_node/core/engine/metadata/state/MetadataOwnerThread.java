package com.mine.geometry_node.core.engine.metadata.state;

import net.minecraft.server.MinecraftServer;

import java.util.Objects;

/** Shared lifetime and owner-thread invariant for live server metadata. */
public final class MetadataOwnerThread {
    private MetadataOwnerThread() {}

    public static void requireCurrent(MinecraftServer server) {
        Objects.requireNonNull(server);
        if (!server.isSameThread() || server.isStopped())
            throw new IllegalStateException("Runtime metadata requires the live server thread");
    }
}
