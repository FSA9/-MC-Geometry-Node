package com.mine.geometry_node.core.engine.graph.expression;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

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
        Identifier dimensionId = Identifier.tryParse(entityBinding.dimensionId());
        if (dimensionId == null) return Double.NaN;
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
        ServerLevel targetLevel = server.getLevel(dimension);
        if (targetLevel == null) return Double.NaN;
        Entity entity = targetLevel.getEntity(entityBinding.entityUuid());
        return EntityExpressionValues.resolve(entityBinding, entity, 1.0F);
    }
}
