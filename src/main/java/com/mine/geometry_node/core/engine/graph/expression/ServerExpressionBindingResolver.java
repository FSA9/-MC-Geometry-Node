package com.mine.geometry_node.core.engine.graph.expression;

import com.mine.geometry_node.core.engine.graph.value.GraphEntityReferenceResolver;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;

import java.util.Objects;

/** Resolves typed live-expression bindings against server-owned entities. */
public final class ServerExpressionBindingResolver implements ExpressionEvaluationContext.BindingResolver {
    private final MinecraftServer server;

    public ServerExpressionBindingResolver(MinecraftServer server) {
        this.server = Objects.requireNonNull(server, "server");
    }

    @Override
    public double resolve(ExpressionBinding binding) {
        if (!(binding instanceof ExpressionBinding.EntityProperty entityBinding)) {
            return Double.NaN;
        }
        Entity entity = GraphEntityReferenceResolver.resolve(entityBinding.entityUuid(), server);
        return EntityExpressionValues.resolve(entityBinding, entity, 1.0F);
    }
}
