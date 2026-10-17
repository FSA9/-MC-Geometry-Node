package com.mine.geometry_node.mixin.game.render;

import com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime;
import com.mine.geometry_node.client.runtime.game.selection.ViewportEntitySource;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelRenderer.class)
public abstract class ViewportEntitySelectionMixin {
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void geometryNode$captureSelection(Entity entity, float partialTick,
                                              CallbackInfoReturnable<EntityRenderState> cir) {
        if (EditViewportRuntime.isRendering()) ViewportEntitySource.extract(entity, cir.getReturnValue());
    }
}
