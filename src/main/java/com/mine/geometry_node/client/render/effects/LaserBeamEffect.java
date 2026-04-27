package com.mine.geometry_node.client.render.effects;

import com.mine.geometry_node.client.render.RenderUtils;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class LaserBeamEffect extends DirectedVisualEffect {

    public LaserBeamEffect(PacketSpawnDynamicVisual packet) {
        super(packet);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 camPos, float partialTick) {
        // 1. 获取当前帧被公式驱动后的最新坐标与粗细
        DirectedAnchors anchors = computeAnchors(partialTick);

        VertexConsumer laserBuffer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        // 2. 解包颜色
        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        // 3. 转换为相对于相机的渲染坐标
        Vec3 startRel = anchors.start().subtract(camPos);
        Vec3 endRel = anchors.end().subtract(camPos);

        // 4. 几何体计算 (每一帧根据新的起点和终点重新构建)
        Vec3 d = endRel.subtract(startRel);
        if (d.lengthSqr() < 1e-5) return; // 防止起点终点重合导致法线计算崩溃
        Vec3 dir = d.normalize();

        Vec3 up = Math.abs(dir.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);

        // 使用动态计算的 size
        double radius = anchors.size() / 2.0;
        Vec3 right = dir.cross(up).normalize().scale(radius);
        Vec3 realUp = right.cross(dir).normalize().scale(radius);

        // 5. 构建激光长方体的 8 个顶点
        Vec3 p1 = startRel.add(right).add(realUp);
        Vec3 p2 = startRel.subtract(right).add(realUp);
        Vec3 p3 = startRel.subtract(right).subtract(realUp);
        Vec3 p4 = startRel.add(right).subtract(realUp);

        Vec3 p5 = p1.add(d), p6 = p2.add(d), p7 = p3.add(d), p8 = p4.add(d);

        // 6. 绘制 6 个面
        RenderUtils.drawQuad(laserBuffer, matrix, p1, p5, p6, p2, r, g, b, a); // 顶面
        RenderUtils.drawQuad(laserBuffer, matrix, p4, p3, p7, p8, r, g, b, a); // 底面
        RenderUtils.drawQuad(laserBuffer, matrix, p1, p4, p8, p5, r, g, b, a); // 右面
        RenderUtils.drawQuad(laserBuffer, matrix, p2, p6, p7, p3, r, g, b, a); // 左面
        RenderUtils.drawQuad(laserBuffer, matrix, p1, p2, p3, p4, r, g, b, a); // 起点面
        RenderUtils.drawQuad(laserBuffer, matrix, p5, p8, p7, p6, r, g, b, a); // 终点面
    }
}