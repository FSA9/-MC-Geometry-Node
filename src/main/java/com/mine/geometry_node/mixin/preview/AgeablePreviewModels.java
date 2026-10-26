package com.mine.geometry_node.mixin.preview;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.AgeableMobRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Ageable renderers choose their model at submission, after GUI framing has already run. */
@SuppressWarnings("deprecation")
@Mixin(AgeableMobRenderer.class)
public interface AgeablePreviewModels {
    @Accessor("adultModel") EntityModel<?> geometryNode$adultPreviewModel();
    @Accessor("babyModel") EntityModel<?> geometryNode$babyPreviewModel();
}
