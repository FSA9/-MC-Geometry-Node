package com.mine.geometry_node.core.engine.blueprint.projectile;

import com.mine.geometry_node.GeometryNode;
import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.blueprint.event.GraphEventData;
import com.mine.geometry_node.core.node.definition.port.StandardPorts;
import com.mine.geometry_node.core.node.nodes.events.projectile.OnProjectileHit;
import com.mine.geometry_node.mixin.AbstractArrowAccessor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.arrow.ThrownTrident;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;

/** Owns projectile-impact dispatch, interception, re-launch precedence and terminal lifecycle. */
public final class ProjectileImpactController {
    private static final ThreadLocal<Deque<ImpactFrame>> ACTIVE_IMPACTS =
            ThreadLocal.withInitial(ArrayDeque::new);
    private ProjectileImpactController() {
    }

    public static void markRelaunched(Projectile projectile) {
        if (projectile == null) return;
        Deque<ImpactFrame> frames = ACTIVE_IMPACTS.get();
        if (frames.isEmpty()) {
            ACTIVE_IMPACTS.remove();
            return;
        }
        for (ImpactFrame frame : frames) {
            if (frame.projectile == projectile) {
                frame.relaunched = true;
                return;
            }
        }
    }

    /** Returns true when the caller must skip the complete native hit implementation. */
    public static boolean handleImpact(Projectile projectile, HitResult hitResult) {
        if (!(projectile.level() instanceof ServerLevel level) || projectile.isRemoved()
                || hitResult == null || hitResult.getType() == HitResult.Type.MISS) return false;
        ProjectileControlAttachment control = projectile.getData(GeometryNode.PROJECTILE_CONTROL_ATTACHMENT);
        if (control.retained()) return true;
        Entity owner = projectile.getOwner();
        Entity dispatchTarget = owner != null ? owner : projectile;
        Map<String, Object> payload = eventPayload(level, projectile, owner, hitResult);
        boolean intercept = BlueprintRuntime.INSTANCE.shouldInterceptProjectileHit(
                level, dispatchTarget, payload);

        ImpactFrame frame = new ImpactFrame(projectile);
        Deque<ImpactFrame> frames = ACTIVE_IMPACTS.get();
        frames.push(frame);
        try {
            BlueprintRuntime.INSTANCE.dispatchEvent(
                    level, dispatchTarget, OnProjectileHit.TYPE_ID, payload);
        } finally {
            frames.pop();
            if (frames.isEmpty()) ACTIVE_IMPACTS.remove();
        }

        if (frame.relaunched) {
            return true;
        }
        if (projectile.isRemoved()) {
            return true;
        }
        if (!intercept) {
            return false;
        }

        applyLifecycle(projectile, hitResult, configuredLifecycle(projectile, hitResult));
        return true;
    }

    private static ProjectileCollisionPolicy configuredLifecycle(Projectile projectile, HitResult hitResult) {
        ProjectileCollisionPolicy configured = projectile
                .getData(GeometryNode.PROJECTILE_CONTROL_ATTACHMENT)
                .collisionPolicy();
        if (configured != ProjectileCollisionPolicy.VANILLA) {
            return configured;
        }
        return resolveVanillaLifecycle(projectile, hitResult);
    }

    /**
     * Resolves the lifecycle-only counterpart of common vanilla projectiles.
     * Unknown projectiles discard on an intercepted hit because their business
     * effects cannot be safely separated from their native onHit implementation.
     */
    private static ProjectileCollisionPolicy resolveVanillaLifecycle(Projectile projectile, HitResult hitResult) {
        if (projectile instanceof FishingHook || projectile instanceof ThrownTrident) {
            return ProjectileCollisionPolicy.RETAIN_ON_HIT;
        }
        if (projectile instanceof AbstractArrow && hitResult.getType() == HitResult.Type.BLOCK) {
            return ProjectileCollisionPolicy.RETAIN_ON_HIT;
        }
        return ProjectileCollisionPolicy.DISCARD_ON_HIT;
    }

    private static void applyLifecycle(Projectile projectile, HitResult hitResult,
                                       ProjectileCollisionPolicy lifecycle) {
        if (lifecycle == ProjectileCollisionPolicy.DISCARD_ON_HIT) {
            projectile.discard();
            return;
        }

        Vec3 hitPosition = hitResult.getLocation();
        if (projectile instanceof AbstractArrow arrow && hitResult instanceof BlockHitResult blockHit) {
            Vec3 movement = arrow.getDeltaMovement();
            Vec3 offset = new Vec3(Math.signum(movement.x), Math.signum(movement.y), Math.signum(movement.z))
                    .scale(0.05D);
            arrow.setPos(hitPosition.subtract(offset));
            arrow.setDeltaMovement(Vec3.ZERO);
            AbstractArrowAccessor accessor = (AbstractArrowAccessor) arrow;
            accessor.geometryNode$setInGround(true);
            accessor.geometryNode$setLastState(arrow.level().getBlockState(blockHit.getBlockPos()));
            arrow.shakeTime = 7;
            arrow.setCritArrow(false);
            return;
        }

        projectile.setPos(hitPosition);
        projectile.setDeltaMovement(Vec3.ZERO);
        projectile.setNoGravity(true);
        projectile.getData(GeometryNode.PROJECTILE_CONTROL_ATTACHMENT).setRetained(true);
    }

    private static Map<String, Object> eventPayload(ServerLevel level, Projectile projectile,
                                                     Entity owner, HitResult result) {
        Entity hitEntity = result instanceof EntityHitResult entityHit ? entityHit.getEntity() : null;
        BlockState hitBlock = null;
        Vec3 hitNormal = null;
        if (result instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            hitBlock = level.getBlockState(pos);
            Direction direction = blockHit.getDirection();
            hitNormal = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        }

        return GraphEventData.of(
                StandardPorts.PROJECTILE.getId(), projectile,
                StandardPorts.XYZ.getId(), result.getLocation(),
                StandardPorts.HIT_NORMAL.getId(), hitNormal,
                StandardPorts.PREVIOUS_POS.getId(), new Vec3(projectile.xo, projectile.yo, projectile.zo),
                StandardPorts.VECTOR.getId(), projectile.getDeltaMovement(),
                StandardPorts.SOURCE_ENTITY.getId(), owner,
                StandardPorts.HIT_ENTITY.getId(), hitEntity,
                StandardPorts.BLOCK_STATE.getId(), hitBlock
        );
    }

    private static final class ImpactFrame {
        private final Projectile projectile;
        private boolean relaunched;

        private ImpactFrame(Projectile projectile) {
            this.projectile = projectile;
        }
    }
}
