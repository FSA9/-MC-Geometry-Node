package com.mine.geometry_node.core.engine.graph.resource;

import net.minecraft.server.MinecraftServer;

/** Compatibility facade over registered owner-backed metadata capabilities. */
public interface GraphResourceTags {
    GraphResourceTags INSTANCE = new GraphResourceTags() {
        @Override public boolean add(MinecraftServer server, GraphResourceReferences.Reference resource, String tag) {
            return com.mine.geometry_node.core.engine.metadata.RuntimeMetadataTargets.resolve(server, resource).requireTags().add(tag);
        }
        @Override public boolean remove(MinecraftServer server, GraphResourceReferences.Reference resource, String tag) {
            return com.mine.geometry_node.core.engine.metadata.RuntimeMetadataTargets.resolve(server, resource).requireTags().remove(tag);
        }
    };

    boolean add(MinecraftServer server, GraphResourceReferences.Reference resource, String tag);

    boolean remove(MinecraftServer server, GraphResourceReferences.Reference resource, String tag);
}
