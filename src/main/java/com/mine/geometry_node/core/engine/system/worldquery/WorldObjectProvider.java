package com.mine.geometry_node.core.engine.system.worldquery;

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
    record Context(MinecraftServer server, List<ServerLevel> levels, Predicate<String> acceptsDimension) {
        public Context { levels = List.copyOf(levels); }
    }
    interface Sink {
        boolean scan();
        boolean emit(WorldObjectEntry entry);
    }
}
