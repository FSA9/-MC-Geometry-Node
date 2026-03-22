package com.mine.geometry_node.client.render.effects;

import com.mine.geometry_node.client.render.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class LaserBeamEffect extends AbstractVisualEffect {
    private final float size;

    public LaserBeamEffect(Vec3 start, Vec3 end, int color, float size, int durationTicks) {
        super(start, end, color, durationTicks);
        this.size = size;
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 camPos) {
        VertexConsumer laserBuffer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        Vec3 startRel = getRelativePos(start, camPos);
        Vec3 endRel = getRelativePos(end, camPos);

        Vec3 d = endRel.subtract(startRel);
        Vec3 dir = d.normalize();

        Vec3 up = Math.abs(dir.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);

        double radius = size / 2.0;
        Vec3 right = dir.cross(up).normalize().scale(radius);
        Vec3 realUp = right.cross(dir).normalize().scale(radius);

        Vec3 p1 = startRel.add(right).add(realUp);
        Vec3 p2 = startRel.subtract(right).add(realUp);
        Vec3 p3 = startRel.subtract(right).subtract(realUp);
        Vec3 p4 = startRel.add(right).subtract(realUp);

        Vec3 p5 = p1.add(d), p6 = p2.add(d), p7 = p3.add(d), p8 = p4.add(d);

        // 使用我们抽离的公共工具类
        RenderUtils.drawQuad(laserBuffer, matrix, p1, p5, p6, p2, r, g, b, a); // 顶面
        RenderUtils.drawQuad(laserBuffer, matrix, p4, p3, p7, p8, r, g, b, a); // 底面
        RenderUtils.drawQuad(laserBuffer, matrix, p1, p4, p8, p5, r, g, b, a); // 右面
        RenderUtils.drawQuad(laserBuffer, matrix, p2, p6, p7, p3, r, g, b, a); // 左面
        RenderUtils.drawQuad(laserBuffer, matrix, p1, p2, p3, p4, r, g, b, a); // 起点
        RenderUtils.drawQuad(laserBuffer, matrix, p5, p8, p7, p6, r, g, b, a); // 终点
    }
}