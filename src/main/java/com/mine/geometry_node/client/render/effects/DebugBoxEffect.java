package com.mine.geometry_node.client.render.effects;

import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public class DebugBoxEffect extends AbstractVisualEffect {

    private final Vec3 center;
    private final Vec3 size;
    private final Vec3 rot;

    public DebugBoxEffect(PacketSpawnDynamicVisual packet) {
        super(packet);
        CompoundTag data = packet.extraData();
        if (data != null) {
            this.center = new Vec3(data.getDouble("startX"), data.getDouble("startY"), data.getDouble("startZ"));
            this.size = new Vec3(data.getDouble("sizeX"), data.getDouble("sizeY"), data.getDouble("sizeZ"));
            this.rot = new Vec3(data.getDouble("rotX"), data.getDouble("rotY"), data.getDouble("rotZ"));
        } else {
            this.center = Vec3.ZERO;
            this.size = new Vec3(1, 1, 1);
            this.rot = Vec3.ZERO;
        }
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 camPos, float partialTick) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        poseStack.pushPose();

        // 1. 移动到相机的相对坐标
        Vec3 renderPos = center.subtract(camPos);
        poseStack.translate(renderPos.x, renderPos.y, renderPos.z);

        // 2. 应用旋转 (严格遵守 YXZ 顺序，保证框和实际碰撞算法完全对齐)
        Quaternionf rotationQuat = new Quaternionf().rotationYXZ(
                (float) Math.toRadians(rot.y),
                (float) Math.toRadians(rot.x),
                (float) Math.toRadians(rot.z)
        );
        poseStack.mulPose(rotationQuat);

        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();

        // 手动计算局部空间中的半边长 (等同于缩放)
        float hX = (float) size.x * 0.5f;
        float hY = (float) size.y * 0.5f;
        float hZ = (float) size.z * 0.5f;

        float minX = -hX, maxX = hX;
        float minY = -hY, maxY = hY;
        float minZ = -hZ, maxZ = hZ;

        // --- 画线环节 ---

        // 底面 (Bottom)
        drawLine(buffer, pose, matrix, minX, minY, minZ, maxX, minY, minZ, r, g, b, a);
        drawLine(buffer, pose, matrix, maxX, minY, minZ, maxX, minY, maxZ, r, g, b, a);
        drawLine(buffer, pose, matrix, maxX, minY, maxZ, minX, minY, maxZ, r, g, b, a);
        drawLine(buffer, pose, matrix, minX, minY, maxZ, minX, minY, minZ, r, g, b, a);

        // 顶面 (Top)
        drawLine(buffer, pose, matrix, minX, maxY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(buffer, pose, matrix, maxX, maxY, minZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, pose, matrix, maxX, maxY, maxZ, minX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, pose, matrix, minX, maxY, maxZ, minX, maxY, minZ, r, g, b, a);

        // 四根立柱 (Pillars)
        drawLine(buffer, pose, matrix, minX, minY, minZ, minX, maxY, minZ, r, g, b, a);
        drawLine(buffer, pose, matrix, maxX, minY, minZ, maxX, maxY, minZ, r, g, b, a);
        drawLine(buffer, pose, matrix, maxX, minY, maxZ, maxX, maxY, maxZ, r, g, b, a);
        drawLine(buffer, pose, matrix, minX, minY, maxZ, minX, maxY, maxZ, r, g, b, a);

        poseStack.popPose();
    }

    /**
     * 高效绘制单条线段，并自动计算和应用法线方向
     */
    private void drawLine(VertexConsumer buffer, PoseStack.Pose pose, Matrix4f matrix,
                          float x1, float y1, float z1, float x2, float y2, float z2,
                          int r, int g, int b, int a) {
        float nx = x2 - x1;
        float ny = y2 - y1;
        float nz = z2 - z1;
        float len = (float) Math.sqrt(nx * nx + ny * ny + nz * nz);
        if (len > 0) {
            nx /= len; ny /= len; nz /= len;
        }
        buffer.addVertex(matrix, x1, y1, z1).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
        buffer.addVertex(matrix, x2, y2, z2).setColor(r, g, b, a).setNormal(pose, nx, ny, nz);
    }
}