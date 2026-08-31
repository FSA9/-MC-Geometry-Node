package com.mine.geometry_node.client.runtime.render.math;

import com.mine.geometry_node.core.engine.graph.expression.ExpressionBinding;
import com.mine.geometry_node.core.engine.graph.expression.EntityExpressionValues;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;

import java.util.UUID;

/** Resolves typed entity bindings for client-side visual expressions. */
public final class ClientPropertyFetcher {
    private ClientPropertyFetcher() {
    }

    public static final class Resolver implements com.mine.geometry_node.core.engine.graph.expression.ExpressionEvaluationContext.BindingResolver {
        private ClientLevel level;
        private float partialTick;
        private int cachedRuntimeId = Integer.MIN_VALUE;
        private UUID cachedUuid;
        private Entity cachedEntity;

        public void begin(ClientLevel level, float partialTick) {
            this.level = level;
            this.partialTick = partialTick;
            this.cachedRuntimeId = Integer.MIN_VALUE;
            this.cachedUuid = null;
            this.cachedEntity = null;
        }

        @Override
        public double resolve(ExpressionBinding binding) {
            if (!(binding instanceof ExpressionBinding.EntityProperty entityBinding) || level == null) {
                return Double.NaN;
            }
            if (!entityBinding.dimensionId().equals(level.dimension().identifier().toString())) {
                return Double.NaN;
            }
            Entity entity = entity(entityBinding);
            return EntityExpressionValues.resolve(entityBinding, entity, partialTick);
        }

        private Entity entity(ExpressionBinding.EntityProperty binding) {
            if (binding.runtimeEntityId() != cachedRuntimeId || !binding.entityUuid().equals(cachedUuid)
                    || cachedEntity == null) {
                cachedRuntimeId = binding.runtimeEntityId();
                cachedUuid = binding.entityUuid();
                cachedEntity = level.getEntity(cachedRuntimeId);
                if (cachedEntity != null && !cachedEntity.getUUID().equals(binding.entityUuid())) {
                    cachedEntity = null;
                }
            }
            return cachedEntity;
        }
    }

}
