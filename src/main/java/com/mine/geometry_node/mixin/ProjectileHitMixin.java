package com.mine.geometry_node.mixin;

import com.mine.geometry_node.core.execution.GraphEngine;
import com.mine.geometry_node.core.node.nodes.events.entity.OnProjectileHit;
import com.mine.geometry_node.core.node.port.StandardPorts;
import net.minecraft.core.BlockPos;
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

        Vec3 impactVelocity = projectile.getDeltaMovement();

        Entity hitEntity = null;
        BlockState hitBlock = null;

        if (result.getType() == HitResult.Type.ENTITY) {
            hitEntity = ((EntityHitResult) result).getEntity();
        } else if (result.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = ((BlockHitResult) result).getBlockPos();
            hitBlock = serverLevel.getBlockState(pos);
        }

        // 以 Owner 优先作为分发主体，投掷物兜底
        Entity dispatchTarget = (owner != null) ? owner : projectile;

        final Entity finalOwner = owner;
        final Entity finalHitEntity = hitEntity;
        final BlockState finalHitBlock = hitBlock;

        GraphEngine.dispatchEvent(serverLevel, dispatchTarget, OnProjectileHit.TYPE_ID, process -> {
            process.setEventData(StandardPorts.ENTITY.getId(), projectile);
            process.setEventData(StandardPorts.XYZ.getId(), hitPos);
            process.setEventData(StandardPorts.VECTOR.getId(), impactVelocity);

            if (finalOwner != null) {
                process.setEventData(StandardPorts.SOURCE_ENTITY.getId(), finalOwner);
            }
            if (finalHitEntity != null) {
                process.setEventData(StandardPorts.TRIGGER_ENTITY.getId(), finalHitEntity);
            }
            if (finalHitBlock != null) {
                process.setEventData(StandardPorts.BLOCK_STATE.getId(), finalHitBlock);
            }
        });
    }
}