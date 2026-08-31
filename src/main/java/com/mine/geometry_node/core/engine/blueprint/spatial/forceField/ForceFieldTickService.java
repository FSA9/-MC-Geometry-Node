package com.mine.geometry_node.core.engine.blueprint.spatial.forceField;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaEntityQuery;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResource;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResourceStore;
import com.mine.geometry_node.core.engine.graph.expression.EntityExpressionValues;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionBinding;
import com.mine.geometry_node.core.engine.graph.expression.ExpressionEvaluationContext;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.UUID;

/** Applies all active finite force fields once per server-level tick. */
public final class ForceFieldTickService {
    public static final ForceFieldTickService INSTANCE = new ForceFieldTickService();

    private static final double MIN_DISTANCE = 0.25D;
    private static final double DIRECTION_EPSILON_SQUARED = 1.0E-12D;

    private ForceFieldTickService() {
    }

    public void tickLevel(ServerLevel level) {
        List<ForceFieldResource> fields = ForceFieldResourceStore.INSTANCE.snapshot(level);
        ServerBindingResolver resolver = new ServerBindingResolver(level.getServer());
        long worldGameTime = level.getGameTime();
        for (ForceFieldResource field : fields) {
            double age = Math.max(0L, worldGameTime - field.creationGameTime());
            apply(level, field, new ExpressionEvaluationContext(worldGameTime, age, resolver));
        }
    }

    private void apply(ServerLevel level, ForceFieldResource field, ExpressionEvaluationContext context) {
        double strength = field.evaluateStrength(context);
        AreaResource areaResource = AreaResourceStore.INSTANCE.get(level.getServer(), field.area());
        AreaResource.Resolved area = areaResource != null ? areaResource.resolve(level) : null;
        if (area == null || strength == 0.0D) return;

        UUID anchorId = areaResource.anchorEntityId();
        List<Entity> entities = AreaEntityQuery.find(level, area.shape(), area.center(), area.size(),
                area.rotation(), entity -> !entity.isSpectator()
                        && (anchorId == null || !anchorId.equals(entity.getUUID())));
        for (Entity entity : entities) {
            Vec3 offset = area.center().subtract(entity.getBoundingBox().getCenter());
            double distanceSquared = offset.lengthSqr();
            if (!Double.isFinite(distanceSquared) || distanceSquared <= DIRECTION_EPSILON_SQUARED) continue;

            double effectiveDistanceSquared = Math.max(distanceSquared, MIN_DISTANCE * MIN_DISTANCE);
            double acceleration = strength / effectiveDistanceSquared;
            Vec3 delta = offset.scale(acceleration / Math.sqrt(distanceSquared));
            if (!isFinite(delta)) continue;

            entity.setDeltaMovement(entity.getDeltaMovement().add(delta));
            entity.hurtMarked = true;
        }
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }

    private static final class ServerBindingResolver implements ExpressionEvaluationContext.BindingResolver {
        private final MinecraftServer server;

        private ServerBindingResolver(MinecraftServer server) {
            this.server = server;
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
            return EntityExpressionValues.resolve(entityBinding, entity, 1.0f);
        }
    }
}
