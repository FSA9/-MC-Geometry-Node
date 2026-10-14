package com.mine.geometry_node.mixin.model.render;

import com.mine.geometry_node.client.runtime.light.NodeLightTerrainShader;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.gl.shader.ShaderLoader",remap=false)
public abstract class SodiumNodeLightShaderMixin {
    @Inject(method="getShaderSource",at=@At("RETURN"),cancellable=true,remap=false)
    private static void nodeLight$terrainSource(Identifier identifier, CallbackInfoReturnable<String> callback) {
        if(identifier.getNamespace().equals("sodium"))
            callback.setReturnValue(NodeLightTerrainShader.patchSodium(identifier.getPath(),callback.getReturnValue()));
    }
}
