package com.mine.geometry_node.core.engine.system.entity;

import com.mine.geometry_node.core.engine.graph.scoped.ScopedMetadataTargets;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.attachment.EntityGraphAttachment;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateAccessException;
import com.mine.geometry_node.core.engine.graph.scoped.ScopedStateNamespace;
import com.mine.geometry_node.core.metadata.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.UnaryOperator;

/** Custom tags are independent of vanilla scoreboard tags; data shares the PUBLIC owner bucket. */
public final class EntityMetadataAdapter {
    private EntityMetadataAdapter() {}
    public record Target(Entity entity) { public Target { Objects.requireNonNull(entity); } }

    public static void register(MetadataCapabilities registry) {
        registry.register("geometry_node:entity", Target.class,
                Set.of(MetadataCapabilities.Capability.TAG, MetadataCapabilities.Capability.DATA),
                target -> new MetadataCapabilities.Access(tags(target.entity()), data(target.entity())));
    }
    public static TagAccess tags(Entity entity) {
        WeakReference<Entity> reference = new WeakReference<>(Objects.requireNonNull(entity));
        return new TagAccess(new MetadataStore<>() {
            private EntityGraphAttachment attachment() {
                Entity current = requireOwner(reference);
                return current.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
            }
            @Override public List<String> read() { return attachment().customTags(); }
            @Override public boolean update(UnaryOperator<List<String>> transformation) {
                EntityGraphAttachment attachment = attachment();
                return attachment.updateCustomTags(current -> {
                    List<String> candidate = transformation.apply(current);
                    requireOwner(reference);
                    return candidate;
                });
            }
        }, TagPolicy.EXACT);
    }
    public static DataAccess data(Entity entity) {
        return ScopedMetadataTargets.owner(entity, ScopedStateNamespace.PUBLIC);
    }
    /** Revision hook for native query/cache invalidation; vanilla tag changes do not affect it. */
    public static long tagRevision(Entity entity) {
        return requireOwner(new WeakReference<>(entity)).getData(GeometryNode.GRAPH_DATA_ATTACHMENT).customTagRevision();
    }
    private static Entity requireOwner(WeakReference<Entity> reference) {
        Entity entity = reference.get();
        if (entity == null || entity.isRemoved() || !(entity.level() instanceof ServerLevel level))
            throw new ScopedStateAccessException("Entity metadata owner is unavailable");
        if (!level.getServer().isSameThread() || level.getServer().isStopped())
            throw new ScopedStateAccessException("Entity metadata must be accessed on the server owner thread");
        return entity;
    }
}
