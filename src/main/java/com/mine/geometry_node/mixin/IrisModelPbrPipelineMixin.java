package com.mine.geometry_node.mixin;

import com.mine.geometry_node.client.model.compat.ModelIrisPbrBindings;
import com.mojang.blaze3d.textures.GpuTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Override only model attachments after Iris's normal albedo tracking; ordinary world draws are unchanged. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.pipeline.IrisRenderingPipeline", remap=false)
public abstract class IrisModelPbrPipelineMixin {
    @Inject(method="onSetAlbedoTex", at=@At("TAIL"), remap=false, require=0)
    private void geometryNode$bindPbr(GpuTextureView view, CallbackInfo callback) {
        if (view == null) return;
        var binding = ModelIrisPbrBindings.find(view.texture());
        if (binding == null) return;
        if (!ModelIrisPbrBindings.applySamplers(this, binding)) return;
        ModelIrisPbrBindings.changed();
    }
}
