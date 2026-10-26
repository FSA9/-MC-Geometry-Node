package com.mine.geometry_node.client.ui.components.integration.valuepreview;

import com.mine.geometry_node.client.ui.components.integration.nativepreview.LivingPreviewGeometry;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.phys.AABB;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.function.Consumer;

/** Screen-plane extents after the same transforms used by the GUI entity renderer. */
record EntityPreviewFrame(float width, float height, Vector3f translation) {
    static EntityPreviewFrame living(LivingPreviewGeometry geometry, LivingEntityRenderState state, Quaternionf rotation) {
        PoseStack pose = new PoseStack();
        pose.mulPose(rotation);
        Extents extents = new Extents();
        geometry.geometryNode$previewExtents(state, pose, extents);
        return extents.frame();
    }

    static EntityPreviewFrame box(AABB bounds, Quaternionf rotation) {
        Extents extents = new Extents();
        Vector3f point = new Vector3f();
        for (int i = 0; i < 8; i++) {
            point.set((float) ((i & 1) == 0 ? bounds.minX : bounds.maxX),
                    (float) ((i & 2) == 0 ? bounds.minY : bounds.maxY),
                    (float) ((i & 4) == 0 ? bounds.minZ : bounds.maxZ));
            extents.accept(rotation.transform(point));
        }
        return extents.frame();
    }

    private static final class Extents implements Consumer<Vector3fc> {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY;

        @Override public void accept(Vector3fc point) {
            if (!Float.isFinite(point.x()) || !Float.isFinite(point.y())) return;
            minX = Math.min(minX, point.x()); minY = Math.min(minY, point.y());
            maxX = Math.max(maxX, point.x()); maxY = Math.max(maxY, point.y());
        }

        EntityPreviewFrame frame() {
            if (!Float.isFinite(minX)) return null;
            return new EntityPreviewFrame(Math.max(0.25f, maxX - minX), Math.max(0.25f, maxY - minY),
                    new Vector3f(-(minX + maxX) / 2, -(minY + maxY) / 2, 0));
        }
    }
}
