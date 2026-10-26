package com.mine.geometry_node.client.ui.components.integration.nativepreview;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/** Renderer bridge for GUI framing; no render events, layer submissions or world mutation. */
public interface LivingPreviewGeometry {
    void geometryNode$previewExtents(LivingEntityRenderState state, PoseStack pose, Consumer<Vector3fc> output);
}
