package com.mine.geometry_node.mixin.game.render;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(LevelRenderer.class)
public interface EditViewportLevelStateAccessor {
    @Accessor("ticks")
    void geometryNode$setTicks(int ticks);

    @Invoker("setSectionDirty")
    void geometryNode$markSectionDirty(int x, int y, int z, boolean playerChanged);

    @Invoker("removeBlockBreakingProgress")
    void geometryNode$expireBreakingProgress();
}
