package com.mine.geometry_node.core.engine.graph.debug;

import com.mine.geometry_node.core.engine.runtime.ServerEngine;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/** Lifecycle adapter for graph-related server debug rendering. */
public final class GraphDebugEngine implements ServerEngine {
    public static final GraphDebugEngine INSTANCE = new GraphDebugEngine();

    private GraphDebugEngine() {
    }

    @Override public String id() { return "geometry_node:graph_debug"; }
    @Override public int tickOrder() { return 400; }
    @Override public void init() { DebugRendererSessionManager.register(); }
    @Override public void tickLevel(ServerLevel level) { DebugRendererSessionManager.tickLevel(level); }
    @Override public void levelUnloaded(ServerLevel level) { DebugRendererSessionManager.levelUnloaded(level); }
    @Override public void shutdown(MinecraftServer server) { DebugRendererSessionManager.shutdown(server); }
}
