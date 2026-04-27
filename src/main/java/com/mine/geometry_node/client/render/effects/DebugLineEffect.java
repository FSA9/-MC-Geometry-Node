package com.mine.geometry_node.client.render.effects;

import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class DebugLineEffect extends DirectedVisualEffect {

    // 1. 构造函数改为直接接收网络包，并交给父类处理 AST 编译
    public DebugLineEffect(PacketSpawnDynamicVisual packet) {
        super(packet);
    }

    // 2. 渲染方法加上 partialTick 参数
    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 camPos, float partialTick) {
        // 3. 获取当前帧动态计算后的起点和终点
        DirectedAnchors anchors = computeAnchors(partialTick);

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());
        Matrix4f matrix = poseStack.last().pose();

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // 注意：使用 anchors 的动态坐标，而不是被废弃的 start 和 end
        Vec3 dir = anchors.end().subtract(anchors.start());
        if (dir.lengthSqr() < 1e-5) return; // 容错：防止起点和终点重合导致法线归一化崩溃

        dir = dir.normalize();
        float nx = (float) dir.x, ny = (float) dir.y, nz = (float) dir.z;

        // 计算相对坐标
        Vec3 relStart = anchors.start().subtract(camPos);
        Vec3 relEnd = anchors.end().subtract(camPos);

        vertexConsumer.addVertex(matrix, (float)relStart.x, (float)relStart.y, (float)relStart.z)
                .setColor(r, g, b, a).setNormal(poseStack.last(), nx, ny, nz);
        vertexConsumer.addVertex(matrix, (float)relEnd.x, (float)relEnd.y, (float)relEnd.z)
                .setColor(r, g, b, a).setNormal(poseStack.last(), nx, ny, nz);
    }
}