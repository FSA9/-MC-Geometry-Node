package com.mine.geometry_node.mixin.game.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Minecraft.class)
public interface EditViewportMinecraftAccessor {
    @Mutable @Accessor("levelRenderer")
    void geometryNode$setLevelRenderer(LevelRenderer renderer);
}
