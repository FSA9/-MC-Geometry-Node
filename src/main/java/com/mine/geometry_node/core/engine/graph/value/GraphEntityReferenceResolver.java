package com.mine.geometry_node.core.engine.graph.value;

import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/** Resolves the UUID representation used by ENTITY graph values. */
public final class GraphEntityReferenceResolver {
    private GraphEntityReferenceResolver() {
    }

    @Nullable
    public static Entity resolve(UUID entityId, @Nullable GraphDataContext context) {
        if (context == null) return null;
        ServerLevel level = context.getLevel();
        return level != null ? resolve(entityId, level) : null;
    }

    @Nullable
    public static Entity resolve(UUID entityId, ServerLevel preferredLevel) {
        if (entityId == null || preferredLevel == null) return null;

        Entity entity = preferredLevel.getEntity(entityId);
        if (isAvailable(entity)) {
            GraphEntityReferenceIndex.INSTANCE.remember(entity);
            return entity;
        }

        return resolve(entityId, preferredLevel.getServer());
    }

    @Nullable
    public static Entity resolve(UUID entityId, MinecraftServer server) {
        if (entityId == null || server == null) return null;
        Entity entity = GraphEntityReferenceIndex.INSTANCE.resolve(server, entityId);
        if (entity != null) return entity;

        Entity player = server.getPlayerList().getPlayer(entityId);
        if (isAvailable(player)) {
            GraphEntityReferenceIndex.INSTANCE.remember(player);
            return player;
        }
        return null;
    }

    private static boolean isAvailable(@Nullable Entity entity) {
        return entity != null && !entity.isRemoved();
    }
}
