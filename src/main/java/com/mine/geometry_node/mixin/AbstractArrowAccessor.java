package com.mine.geometry_node.mixin;

import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractArrow.class)
public interface AbstractArrowAccessor {
    @Invoker("setInGround")
    void geometryNode$setInGround(boolean inGround);

    @Accessor("lastState")
    void geometryNode$setLastState(BlockState state);

    @org.spongepowered.asm.mixin.gen.Invoker("applyInertia")
    void geometryNode$applyInertia(float inertia);
}
