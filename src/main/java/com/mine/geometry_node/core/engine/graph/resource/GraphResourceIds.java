package com.mine.geometry_node.core.engine.graph.resource;

import com.mine.geometry_node.core.engine.graph.binding.GraphBindingKey;
import com.mine.geometry_node.core.engine.graph.data.GraphDataContext;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** The only construction entry point graph nodes should use for runtime resource ids. */
public final class GraphResourceIds {
    private GraphResourceIds() {
    }

    public static GraphResourceId graph(GraphDataContext context, GraphResourceType type) {
        return create(context, type, GraphResourceSelector.Graph.INSTANCE, null);
    }

    public static GraphResourceId node(GraphDataContext context, String stableNodeId, GraphResourceType type) {
        return create(context, type, new GraphResourceSelector.Node(stableNodeId), null);
    }

    public static GraphResourceId forKey(GraphDataContext context, String stableNodeId,
                                         GraphResourceType type, String key) {
        GraphResourceSelector selector = key == null || key.isBlank()
                ? new GraphResourceSelector.Node(stableNodeId)
                : new GraphResourceSelector.Named(key);
        return create(context, type, selector, null);
    }

    public static GraphResourceId create(GraphDataContext context, GraphResourceType type,
                                         GraphResourceSelector selector, UUID targetEntityId) {
        return create(context.getLevel(), context.getGraphOwnerEntity(), context.getGraphBindingKey(),
                context.getGraphProcessInstanceId(), type, selector, targetEntityId);
    }

    public static GraphResourceId create(ServerLevel level, Entity graphOwner, GraphBindingKey binding,
                                         UUID processInstanceId, GraphResourceType type,
                                         GraphResourceSelector selector, UUID targetEntityId) {
        GraphResourceScope scope = graphOwner != null
                ? new GraphResourceScope.EntityScope(level.dimension(), graphOwner.getUUID())
                : new GraphResourceScope.LevelScope(level.dimension());
        UUID processId = type.lifetime() == GraphResourceLifetime.PROCESS ? processInstanceId : null;
        return new GraphResourceId(type, scope, binding, selector, targetEntityId, processId);
    }
}
