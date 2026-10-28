package com.mine.geometry_node.core.engine.system.worldquery.provider;

import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntry;
import com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectType;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import java.util.List;
import java.util.function.Predicate;

/** Extensible server-thread contract. Stop on false; spend one scan BEFORE doing per-object work. No loads. */
public interface WorldObjectProvider {
    String id();
    List<WorldObjectType> types();
    String coverage();
    void collect(Context context, Sink sink);
    /** Optional exact lookup, independent of list limits. Never load an owner or trust a client DTO. */
    default WorldObjectEntry lookup(Context context, java.util.UUID objectId) { return null; }
    /** Optional live owner resolver. Called on the server thread, never with client-supplied rows.
     * Returned capabilities must reject access after owner removal/replacement. */
    default com.mine.geometry_node.core.metadata.MetadataCapabilities.Access resolveMetadata(Context context, WorldObjectEntry entry) {
        return null;
    }
    record Context(MinecraftServer server, List<ServerLevel> levels, Predicate<String> acceptsDimension,
                   com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntityFilter entityFilter) {
        public Context { levels = List.copyOf(levels); }
        public Context(MinecraftServer server, List<ServerLevel> levels, Predicate<String> acceptsDimension) {
            this(server, levels, acceptsDimension,
                    com.mine.geometry_node.core.engine.system.worldquery.model.WorldObjectEntityFilter.empty());
        }
    }
    interface Sink {
        boolean scan();
        boolean emit(WorldObjectEntry entry);
    }
}
