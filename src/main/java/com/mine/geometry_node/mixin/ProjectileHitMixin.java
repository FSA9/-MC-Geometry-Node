package com.mine.geometry_node.mixin;

import com.mine.geometry_node.core.engine.blueprint.BlueprintRuntime;
import com.mine.geometry_node.core.engine.blueprint.event.GraphEventData;
import com.mine.geometry_node.core.node.nodes.events.entity.OnProjectileHit;
import com.mine.geometry_node.core.node.port.StandardPorts;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileHitMixin {
    @Inject(method = "onHit", at = @At("HEAD"))
    private void geometryNode$onProjectileHit(HitResult result, CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;

        if (projectile.level().isClientSide()) {
            return;
        }

        ServerLevel serverLevel = (ServerLevel) projectile.level();
        Entity owner = projectile.getOwner();
        Vec3 hitPos = result.getLocation();
        Vec3 previousPos = new Vec3(projectile.xo, projectile.yo, projectile.zo);

        Vec3 impactVelocity = projectile.getDeltaMovement();

        Entity hitEntity = null;
        BlockState hitBlock = null;
        Vec3 hitNormal = null;

        if (result.getType() == HitResult.Type.ENTITY) {
            hitEntity = ((EntityHitResult) result).getEntity();
        } else if (result.getType() == HitResult.Type.BLOCK) {
            BlockHitResult blockHit = (BlockHitResult) result;
            BlockPos pos = blockHit.getBlockPos();
            hitBlock = serverLevel.getBlockState(pos);
            Direction direction = blockHit.getDirection();
            hitNormal = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
        }

        // 以 Owner 优先作为分发主体，投掷物兜底
        Entity dispatchTarget = (owner != null) ? owner : projectile;

        BlueprintRuntime.INSTANCE.dispatchEvent(serverLevel, dispatchTarget, OnProjectileHit.TYPE_ID, GraphEventData.of(
                StandardPorts.PROJECTILE.getId(), projectile,
                StandardPorts.XYZ.getId(), hitPos,
                StandardPorts.HIT_NORMAL.getId(), hitNormal,
                StandardPorts.PREVIOUS_POS.getId(), previousPos,
                StandardPorts.VECTOR.getId(), impactVelocity,
                StandardPorts.SOURCE_ENTITY.getId(), owner,
                StandardPorts.HIT_ENTITY.getId(), hitEntity,
                StandardPorts.BLOCK_STATE.getId(), hitBlock
        ));
    }
}
