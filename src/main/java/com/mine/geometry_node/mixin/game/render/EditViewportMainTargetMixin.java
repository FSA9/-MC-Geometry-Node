package com.mine.geometry_node.mixin.game.render;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Minecraft.class)
public abstract class EditViewportMainTargetMixin {
    @ModifyReturnValue(method = "getMainRenderTarget", at = @At("RETURN"))
    private RenderTarget geometryNode$worldTarget(RenderTarget original) {
        return EditViewportRuntime.worldTarget(original);
    }
}
