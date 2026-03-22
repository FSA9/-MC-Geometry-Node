package com.mine.geometry_node.client.render.effects;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class DebugLineEffect extends AbstractVisualEffect {

    public DebugLineEffect(Vec3 start, Vec3 end, int color, int durationTicks) {
        super(start, end, color, durationTicks);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 camPos) {
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        Vec3 dir = end.subtract(start).normalize();
        float nx = (float) dir.x, ny = (float) dir.y, nz = (float) dir.z;

        Vec3 relStart = getRelativePos(start, camPos);
        Vec3 relEnd = getRelativePos(end, camPos);

        vertexConsumer.addVertex(matrix, (float)relStart.x, (float)relStart.y, (float)relStart.z)
                .setColor(r, g, b, a).setNormal(poseStack.last(), nx, ny, nz);
        vertexConsumer.addVertex(matrix, (float)relEnd.x, (float)relEnd.y, (float)relEnd.z)
                .setColor(r, g, b, a).setNormal(poseStack.last(), nx, ny, nz);
    }
}