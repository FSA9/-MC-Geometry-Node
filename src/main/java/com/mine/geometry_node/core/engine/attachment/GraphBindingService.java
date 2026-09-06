package com.mine.geometry_node.core.engine.attachment;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.graph.GraphKind;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.binding.GraphBindingRuntimeIndex;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/**
 * Single mutation boundary for bindings persisted on entity graph attachments.
 * Every successful mutation updates the loaded-entity runtime index before returning.
 */
public final class GraphBindingService {
    public static final GraphBindingService INSTANCE = new GraphBindingService();

    private GraphBindingService() {
    }

    public boolean bind(Entity entity, GraphBindingKey binding) {
        EntityGraphAttachment attachment = requireAttachment(entity);
        boolean changed = attachment.addBinding(Objects.requireNonNull(binding, "binding"));
        if (changed) GraphBindingRuntimeIndex.INSTANCE.synchronize(entity);
        return changed;
    }

    public boolean unbind(Entity entity, GraphBindingKey binding) {
        EntityGraphAttachment attachment = requireAttachment(entity);
        boolean changed = attachment.removeBinding(Objects.requireNonNull(binding, "binding"));
        if (changed) GraphBindingRuntimeIndex.INSTANCE.synchronize(entity);
        return changed;
    }

    public boolean clear(Entity entity, GraphKind kind) {
        EntityGraphAttachment attachment = requireAttachment(entity);
        boolean changed = attachment.clearBindings(Objects.requireNonNull(kind, "kind"));
        if (changed) GraphBindingRuntimeIndex.INSTANCE.synchronize(entity);
        return changed;
    }

    private static EntityGraphAttachment requireAttachment(Entity entity) {
        Objects.requireNonNull(entity, "entity");
        if (entity.isRemoved() || !(entity.level() instanceof ServerLevel)) {
            throw new IllegalArgumentException("Graph bindings require a loaded server entity");
        }
        return entity.getData(GeometryNode.GRAPH_DATA_ATTACHMENT);
    }
}
