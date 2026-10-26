package com.mine.geometry_node.mixin.preview;

import com.mine.geometry_node.client.ui.components.integration.nativepreview.LivingPreviewGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Vector3fc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

import java.util.function.Consumer;

/** Reuses the renderer's virtual transforms, including addon scaling and poses. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityPreviewGeometryMixin<S extends LivingEntityRenderState,
        M extends EntityModel<? super S>> implements LivingPreviewGeometry {
    @Shadow protected M model;
    @Shadow protected abstract void setupRotations(S state, PoseStack pose, float bodyRot, float scale);
    @Shadow protected abstract void scale(S state, PoseStack pose);

    @Override
    @SuppressWarnings("unchecked")
    public void geometryNode$previewExtents(LivingEntityRenderState state, PoseStack pose, Consumer<Vector3fc> output) {
        S typed = (S) state;
        pose.pushPose();
        try {
            if (state.hasPose(net.minecraft.world.entity.Pose.SLEEPING) && state.bedOrientation != null) {
                float offset = state.eyeHeight - 0.1f;
                pose.translate(-state.bedOrientation.getStepX() * offset, 0, -state.bedOrientation.getStepZ() * offset);
            }
            pose.scale(state.scale, state.scale, state.scale);
            setupRotations(typed, pose, state.bodyRot, state.scale);
            pose.scale(-1, -1, 1);
            scale(typed, pose);
            pose.translate(0, EntityModel.MODEL_Y_OFFSET, 0);
            EntityModel<? super S> previewModel = model;
            if (this instanceof AgeablePreviewModels ageable) {
                previewModel = (EntityModel<? super S>) (state.isBaby
                        ? ageable.geometryNode$babyPreviewModel() : ageable.geometryNode$adultPreviewModel());
            }
            previewModel.setupAnim(typed);
            previewModel.root().getExtentsForGui(pose, output);
        } finally {
            pose.popPose();
        }
    }
}
