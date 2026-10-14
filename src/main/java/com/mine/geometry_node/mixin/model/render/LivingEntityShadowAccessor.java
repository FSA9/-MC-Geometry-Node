package com.mine.geometry_node.mixin.model.render;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.model.EntityModel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reuse renderer-specific body transforms without firing a second entity render event. */
@Mixin(LivingEntityRenderer.class)
public interface LivingEntityShadowAccessor {
    @Accessor("model")
    void geometryNode$shadowModel(EntityModel<?> model);

    @Invoker("setupRotations")
    void geometryNode$shadowRotations(LivingEntityRenderState state, PoseStack pose, float bodyRotation, float scale);

    @Invoker("scale")
    void geometryNode$shadowScale(LivingEntityRenderState state, PoseStack pose);

    @Invoker("isBodyVisible")
    boolean geometryNode$shadowBodyVisible(LivingEntityRenderState state);
}
