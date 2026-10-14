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
        return forKey(context, stableNodeId, type, key, null);
    }

    public static GraphResourceId forKey(GraphDataContext context, String stableNodeId,
                                         GraphResourceType type, String key, UUID targetEntityId) {
        GraphResourceSelector selector = key == null || key.isBlank()
                ? new GraphResourceSelector.Node(stableNodeId)
                : new GraphResourceSelector.Named(key);
        return create(context, type, selector, targetEntityId);
    }

    public static GraphResourceId resolveCreate(GraphDataContext context, String stableNodeId,
                                                 GraphResourceType type, String input) {
        return resolveCreate(context, stableNodeId, type, input, null);
    }

    public static GraphResourceId resolveCreate(GraphDataContext context, String stableNodeId,
                                                 GraphResourceType type, String input, UUID targetEntityId) {
        String value = input == null ? "" : input.trim();
        if (GraphResourceReferences.isEncoded(value)) return requireType(value, type);
        return forKey(context, stableNodeId, type, value, targetEntityId);
    }

    public static GraphResourceId resolveExisting(GraphDataContext context, GraphResourceType type, String input) {
        return resolveExisting(context, type, input, null);
    }

    public static GraphResourceId resolveExisting(GraphDataContext context, GraphResourceType type,
                                                   String input, UUID targetEntityId) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Resource id cannot be empty");
        if (GraphResourceReferences.isEncoded(value)) return requireType(value, type);
        return create(context, type, new GraphResourceSelector.Named(value), targetEntityId);
    }

    private static GraphResourceId requireType(String encoded, GraphResourceType type) {
        GraphResourceReferences.Reference reference = GraphResourceReferences.parse(encoded);
        if (!(reference instanceof GraphResourceReferences.GraphReference graph)
                || !graph.id().type().equals(type)) {
            throw new IllegalArgumentException("Resource id does not refer to " + type.id());
        }
        // Explicit references retain their original owner and dimension; names use the caller's scope.
        return graph.id();
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
