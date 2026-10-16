package com.mine.geometry_node.mixin.game.render;

import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface EditViewportResourcePoolAccessor {
    @Accessor("resourcePool")
    CrossFrameResourcePool geometryNode$getResourcePool();

    @Mutable @Accessor("resourcePool")
    void geometryNode$setResourcePool(CrossFrameResourcePool pool);
}
