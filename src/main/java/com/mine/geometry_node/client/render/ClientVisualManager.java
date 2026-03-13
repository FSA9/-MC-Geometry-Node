package com.mine.geometry_node.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * [客户端视觉管理器]
 * 负责接收网络包下发的特效指令，倒计时，并真正将其渲染到屏幕上。
 */
public class ClientVisualManager {

    // 线程安全的列表，用来存放所有存活的线条
    private static final List<DebugLine> ACTIVE_LINES = new CopyOnWriteArrayList<>();

    // --- 数据结构 ---
    private static class DebugLine {
        final Vec3 start, end;
        final int color;
        int remainingTicks;

        DebugLine(Vec3 start, Vec3 end, int color, int durationTicks) {
            this.start = start;
            this.end = end;
            this.color = color;
            this.remainingTicks = durationTicks;
        }
    }

    // --- 对外 API：添加特效 ---
    public static void addDebugLine(Vec3 start, Vec3 end, int color, int duration) {
        ACTIVE_LINES.add(new DebugLine(start, end, color, duration));
    }

    // --- 内部机制 1：每帧倒计时 ---
    public static void tick() {
        if (Minecraft.getInstance().isPaused()) return;

        // 遍历并移除寿命耗尽的线条
        ACTIVE_LINES.removeIf(line -> {
            line.remainingTicks--;
            return line.remainingTicks <= 0;
        });
    }

    // --- 内部机制 2：核心渲染管线 ---
    public static void renderWorld(PoseStack poseStack, Camera camera) {
        if (ACTIVE_LINES.isEmpty()) return;

        // 1. 获取摄像机的绝对坐标 (Double 高精度)
        Vec3 camPos = camera.getPosition();

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderType.lines());

        poseStack.pushPose();
        // 【关键修复 1】: 绝对不要做 poseStack.translate！让矩阵原点死死钉在摄像机上。
        Matrix4f matrix = poseStack.last().pose();

        for (DebugLine line : ACTIVE_LINES) {
            int a = (line.color >> 24) & 0xFF;
            int r = (line.color >> 16) & 0xFF;
            int g = (line.color >> 8) & 0xFF;
            int b = line.color & 0xFF;

            // 法线决定了线段渲染的方向
            Vec3 dir = line.end.subtract(line.start).normalize();
            float nx = (float) dir.x;
            float ny = (float) dir.y;
            float nz = (float) dir.z;

            // 【关键修复 2】: 先用 Double 高精度算出顶点相对于摄像机的距离，再转成 Float！
            // 这样无论你在世界边缘几百万格，dx 都是个很小的数，彻底消灭精度飘逸问题！
            float dx1 = (float) (line.start.x - camPos.x);
            float dy1 = (float) (line.start.y - camPos.y);
            float dz1 = (float) (line.start.z - camPos.z);

            float dx2 = (float) (line.end.x - camPos.x);
            float dy2 = (float) (line.end.y - camPos.y);
            float dz2 = (float) (line.end.z - camPos.z);

            // 画起点顶点 (传入局部坐标)
            vertexConsumer.addVertex(matrix, dx1, dy1, dz1)
                    .setColor(r, g, b, a)
                    .setNormal(poseStack.last(), nx, ny, nz);

            // 画终点顶点 (传入局部坐标)
            vertexConsumer.addVertex(matrix, dx2, dy2, dz2)
                    .setColor(r, g, b, a)
                    .setNormal(poseStack.last(), nx, ny, nz);
        }

        poseStack.popPose();
        bufferSource.endBatch(RenderType.lines());
    }
}