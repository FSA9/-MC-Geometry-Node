package com.mine.geometry_node.core.engine.blueprint.spatial.forceField;

import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaEntityQuery;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResource;
import com.mine.geometry_node.core.engine.blueprint.spatial.area.AreaResourceStore;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
        for (ForceFieldResource field : fields) {
            apply(level, field);
        }
    }

    private void apply(ServerLevel level, ForceFieldResource field) {
        AreaResource areaResource = AreaResourceStore.INSTANCE.get(level.getServer(), field.area());
        AreaResource.Resolved area = areaResource != null ? areaResource.resolve(level) : null;
        if (area == null || field.strength() == 0.0D) return;

        UUID anchorId = areaResource.anchorEntityId();
        List<Entity> entities = AreaEntityQuery.find(level, area.shape(), area.center(), area.size(),
                area.rotation(), entity -> !entity.isSpectator()
                        && (anchorId == null || !anchorId.equals(entity.getUUID())));
        for (Entity entity : entities) {
            Vec3 offset = area.center().subtract(entity.getBoundingBox().getCenter());
            double distanceSquared = offset.lengthSqr();
            if (!Double.isFinite(distanceSquared) || distanceSquared <= DIRECTION_EPSILON_SQUARED) continue;

            double effectiveDistanceSquared = Math.max(distanceSquared, MIN_DISTANCE * MIN_DISTANCE);
            double acceleration = field.strength() / effectiveDistanceSquared;
            Vec3 delta = offset.scale(field.mode().directionSign() * acceleration / Math.sqrt(distanceSquared));
            if (!isFinite(delta)) continue;

            entity.setDeltaMovement(entity.getDeltaMovement().add(delta));
            entity.hurtMarked = true;
        }
    }

    private static boolean isFinite(Vec3 value) {
        return Double.isFinite(value.x) && Double.isFinite(value.y) && Double.isFinite(value.z);
    }
}
