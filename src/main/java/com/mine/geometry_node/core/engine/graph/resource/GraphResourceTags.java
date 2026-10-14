package com.mine.geometry_node.core.engine.graph.resource;

import net.minecraft.server.MinecraftServer;

/** Reserved extension contract. No backend is registered until resource-instance tagging is implemented. */
public interface GraphResourceTags {
    boolean add(MinecraftServer server, GraphResourceReferences.Reference resource, String tag);

    boolean remove(MinecraftServer server, GraphResourceReferences.Reference resource, String tag);
}
