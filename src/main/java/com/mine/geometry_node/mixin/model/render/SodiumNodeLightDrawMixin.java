package com.mine.geometry_node.mixin.model.render;

import com.mine.geometry_node.client.runtime.light.render.NativeTerrainLights;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer",remap=false)
public abstract class SodiumNodeLightDrawMixin {
    @Inject(method="begin",at=@At("RETURN"),remap=false)
    private void nodeLight$begin(CallbackInfo callback) {
        if(!NativeTerrainLights.enabled())
            com.mine.geometry_node.client.runtime.light.render.NodeLightRenderer.uploadForward(
                    org.lwjgl.opengl.GL11C.glGetInteger(org.lwjgl.opengl.GL20C.GL_CURRENT_PROGRAM));
        NativeTerrainLights.begin();
    }
    @Inject(method="end",at=@At("HEAD"),remap=false)
    private void nodeLight$end(CallbackInfo callback) {NativeTerrainLights.end();}
}
