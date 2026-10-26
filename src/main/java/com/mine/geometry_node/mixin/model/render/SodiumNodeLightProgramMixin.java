package com.mine.geometry_node.mixin.model.render;

import com.mine.geometry_node.client.runtime.model.render.bvh.ModelIrisHeldLight;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Sodium deletes programs directly, bypassing the vanilla cache invalidation hook. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.gl.shader.GlProgram",remap=false)
public abstract class SodiumNodeLightProgramMixin {
    @WrapOperation(method="delete",at=@At(value="INVOKE",target="Lorg/lwjgl/opengl/GL20C;glDeleteProgram(I)V"),remap=false)
    private void nodeLight$delete(int program, Operation<Void> original) {
        ModelIrisHeldLight.deleted(program);
        original.call(program);
    }
}
