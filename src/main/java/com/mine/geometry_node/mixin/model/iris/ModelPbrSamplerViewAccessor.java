package com.mine.geometry_node.mixin.model.iris;
import com.mojang.blaze3d.opengl.GlTextureView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="com.mojang.blaze3d.opengl.GlRenderPass$TextureViewAndSampler")
public interface ModelPbrSamplerViewAccessor {
    @Accessor("view") GlTextureView geometryNode$pbrView();
}
