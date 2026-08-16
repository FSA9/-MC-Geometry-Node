package com.mine.geometry_node.mixin;

import com.mine.geometry_node.client.model.render.backend.host.iris.shadow.IrisShadowAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional phase hook required because Iris' public callback runs before opaque shadow depth is copied. */
@Pseudo
@Mixin(targets = "net.irisshaders.iris.shadows.ShadowRenderer", remap = false)
public abstract class IrisShadowRendererMixin {
    @Inject(method = "copyPreTranslucentDepth", at = @At("TAIL"), require = 0, remap = false)
    private void geometryNode$afterOpaqueDepthCopy(CallbackInfo callback) {
        IrisShadowAdapter.renderTranslucentAfterDepthCopy();
    }
}
