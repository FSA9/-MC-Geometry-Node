package com.mine.geometry_node.mixin.model.collision;

import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionMovement;
import com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionScene;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class ModelEntityCollisionMixin {
    @Inject(method = "collide", at = @At("RETURN"), cancellable = true)
    private void geometryNode$modelCollision(Vec3 requested, CallbackInfoReturnable<Vec3> callback) {
        Entity entity = (Entity) (Object) this;
        Vec3 vanillaMovement = callback.getReturnValue();
        if (vanillaMovement.lengthSqr() == 0) return;
        Level level = entity.level();
        if (ModelCollisionScene.snapshots(level).isEmpty()) return;
        try (var sample = com.mine.geometry_node.core.engine.system.model.collision.runtime.ModelCollisionMetrics.movement(level.isClientSide())) {
        AABB box = entity.getBoundingBox();
        // A walkable face can lift horizontal travel by up to sqrt(3) times its length.
        double lift = vanillaMovement.horizontalDistance() * 1.8 + Math.max(0.61, entity.maxUpStep());
        // Wall slides may redirect motion outside the original component-wise sweep.
        double sideways = vanillaMovement.length() * 2 + 1.0e-6;
        var candidates = ModelCollisionScene.query(level, box.expandTowards(vanillaMovement).inflate(sideways, lift, sideways), entity.getUUID());
        if (sample.sampled()) sample.candidates = candidates.size();
        if (candidates.isEmpty()) return;
        Vec3 resolved = ModelCollisionMovement.resolve(box, vanillaMovement, entity.onGround(), entity.maxUpStep(), candidates,
                (at, movement) -> Entity.collideBoundingBox(entity, movement, at, level,
                        level.getEntityCollisions(entity, at.expandTowards(movement))));
        callback.setReturnValue(resolved);
        }
    }
}
