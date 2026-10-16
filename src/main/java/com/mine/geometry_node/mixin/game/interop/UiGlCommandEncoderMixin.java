package com.mine.geometry_node.mixin.game.interop;

import com.mine.geometry_node.client.runtime.game.interop.UiRenderInterop;
import com.mojang.blaze3d.opengl.GlProgram;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Arc3D can change the bound program without changing Blaze3D's lastProgram.
 * Invalidate once at the next draw, including frames that do not render a world. */
@Mixin(targets = "com.mojang.blaze3d.opengl.GlCommandEncoder")
public abstract class UiGlCommandEncoderMixin {
    @Shadow private GlProgram lastProgram;
    @Shadow private RenderPipeline lastPipeline;
    @Unique private long geometryNode$uiRevision;

    @Inject(method = "trySetup", at = @At("HEAD"))
    private void geometryNode$invalidateAfterUi(CallbackInfoReturnable<Boolean> cir) {
        long revision = UiRenderInterop.revision();
        if (geometryNode$uiRevision != revision) {
            geometryNode$uiRevision = revision;
            lastProgram = null;
            lastPipeline = null;
        }
    }
}
