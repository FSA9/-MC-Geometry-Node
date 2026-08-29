package com.mine.geometry_node.mixin;

import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorNativeAiController;
import com.mine.geometry_node.core.engine.behavior.runtime.BehaviorBrainLeaseAccess;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

@Mixin(Brain.class)
public abstract class BrainBehaviorLeaseMixin implements BehaviorBrainLeaseAccess {
    @Unique private int geometryNode$behaviorLeaseMask;

    @Override
    public int geometryNode$getBehaviorLeaseMask() {
        return geometryNode$behaviorLeaseMask;
    }

    @Override
    public void geometryNode$setBehaviorLeaseMask(int mask) {
        geometryNode$behaviorLeaseMask = mask;
    }

    @Inject(method = "tick", at = @At("HEAD"))
    private void geometryNode$syncBehaviorLeases(ServerLevel level, LivingEntity body,
                                                  CallbackInfo ci) {
        BehaviorNativeAiController.syncBrainLeases((Brain<?>) (Object) this, body);
    }

    @Redirect(
            method = "startEachNonRunningBehavior",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;tryStart(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;J)Z")
    )
    private boolean geometryNode$tryStart(BehaviorControl<?> behavior, ServerLevel level,
                                          LivingEntity body, long timestamp) {
        return BehaviorNativeAiController.tryStart(behavior, level, body, timestamp);
    }

    @Redirect(
            method = "tickEachRunningBehavior",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;tickOrStop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/LivingEntity;J)V")
    )
    private void geometryNode$tickOrStop(BehaviorControl<?> behavior, ServerLevel level,
                                         LivingEntity body, long timestamp) {
        BehaviorNativeAiController.tickOrStop(behavior, level, body, timestamp);
    }

    @Inject(method = "setMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/lang/Object;)V",
            at = @At("HEAD"), cancellable = true)
    private <U> void geometryNode$blockMemoryWrite(MemoryModuleType<U> type, U value, CallbackInfo ci) {
        if (BehaviorNativeAiController.blocksMemoryWrite((Brain<?>) (Object) this, type)) ci.cancel();
    }

    @Inject(method = "setMemory(Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Ljava/util/Optional;)V",
            at = @At("HEAD"), cancellable = true)
    private <U> void geometryNode$blockOptionalMemoryWrite(MemoryModuleType<U> type,
                                                           Optional<? extends U> value,
                                                           CallbackInfo ci) {
        if (BehaviorNativeAiController.blocksMemoryWrite((Brain<?>) (Object) this, type)) ci.cancel();
    }

    @Inject(method = "setMemoryWithExpiry", at = @At("HEAD"), cancellable = true)
    private <U> void geometryNode$blockExpiringMemoryWrite(MemoryModuleType<U> type, U value,
                                                           long timeToLive, CallbackInfo ci) {
        if (BehaviorNativeAiController.blocksMemoryWrite((Brain<?>) (Object) this, type)) ci.cancel();
    }

    @Inject(method = "eraseMemory", at = @At("HEAD"), cancellable = true)
    private <U> void geometryNode$blockMemoryErase(MemoryModuleType<U> type, CallbackInfo ci) {
        if (BehaviorNativeAiController.blocksMemoryWrite((Brain<?>) (Object) this, type)) ci.cancel();
    }
}
