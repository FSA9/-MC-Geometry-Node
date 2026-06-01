package com.mine.geometry_node.mixin;

import com.mine.geometry_node.core.execution.GraphEngine;
import com.mine.geometry_node.core.node.nodes.events.entity.OnProjectileShoot;
import com.mine.geometry_node.core.node.port.StandardPorts;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Projectile.class)
public abstract class ProjectileShootMixin {

    /**
     * 拦截原版的投掷物发射核心方法
     * 方法签名: shoot(double x, double y, double z, float velocity, float inaccuracy)
     */
    @Inject(method = "shoot(DDDFF)V", at = @At("TAIL"))
    private void onProjectileShoot(double x, double y, double z, float velocity, float inaccuracy, CallbackInfo ci) {
        Projectile projectile = (Projectile) (Object) this;

        // 仅在服务端触发图纸逻辑
        if (!projectile.level().isClientSide() && projectile.level() instanceof ServerLevel serverLevel) {

            Entity owner = projectile.getOwner();
            Vec3 pos = projectile.position();
            Vec3 motion = projectile.getDeltaMovement();

            // 路由逻辑与 Hit 事件保持一致：优先以发射者为主体触发，兜底为投掷物本身
            Entity dispatchTarget = (owner != null) ? owner : projectile;

            // 为了防止闭包要求 final 引用
            final Entity finalOwner = owner;

            GraphEngine.dispatchEvent(serverLevel, dispatchTarget, OnProjectileShoot.TYPE_ID, process -> {
                process.setEventData(StandardPorts.ENTITY.getId(), projectile);
                process.setEventData(StandardPorts.XYZ.getId(), pos);
                process.setEventData(StandardPorts.VECTOR.getId(), motion);

                if (finalOwner != null) {
                    process.setEventData(StandardPorts.SOURCE_ENTITY.getId(), finalOwner);
                }
            });
        }
    }
}