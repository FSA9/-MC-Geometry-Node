package com.mine.geometry_node.core.engine.system.entity.appearance;

import com.mine.geometry_node.GeometryNode;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.player.Player;

import java.util.Objects;

/** The entity attachment is the sole authority. Registration, metadata and graphs are independent. */
public final class EntityAppearances {
    private EntityAppearances() {}
    /** Both sides; does not allocate an attachment when reading NONE. */
    public static EntityAppearanceSpec get(Entity entity) {
        Objects.requireNonNull(entity);
        return entity.hasData(GeometryNode.ENTITY_APPEARANCE) ? entity.getData(GeometryNode.ENTITY_APPEARANCE) : EntityAppearanceSpec.NONE;
    }
    /** Validates before mutation. Immutable setData values automatically synchronize to tracking players. */
    public static void set(Entity entity, EntityAppearanceSpec spec) {
        requireOwner(entity); Objects.requireNonNull(spec);
        EntityAppearanceAdapters.validate(spec, entity.level());
        if (!get(entity).equals(spec)) entity.setData(GeometryNode.ENTITY_APPEARANCE, spec);
    }
    /**
     * Capture before a synchronous server transaction writes. Rollback restores that exact snapshot,
     * including a persisted variant removed by a registry reload; it cannot accept an unchecked replacement.
     * The callback is transaction-local and must not be retained as an editor undo/history entry.
     */
    public static Runnable captureRollback(Entity entity) {
        requireOwner(entity);
        EntityAppearanceSpec before = get(entity);
        return () -> {
            requireOwner(entity);
            if (!get(entity).equals(before)) entity.setData(GeometryNode.ENTITY_APPEARANCE, before);
        };
    }
    /** Explicit NONE also synchronizes reset, and is omitted from the entity save. */
    public static void reset(Entity entity) { set(entity, EntityAppearanceSpec.NONE); }
    private static void requireOwner(Entity entity) {
        if (entity == null || entity.isRemoved() || entity instanceof Player || entity instanceof Interaction
                || entity.getType() == net.minecraft.world.entity.EntityType.MANNEQUIN
                || !(entity.level() instanceof ServerLevel level) || !level.getServer().isSameThread() || level.getServer().isStopped())
            throw new IllegalStateException("Entity appearance owner is unavailable or unsupported");
    }
}
