package com.mine.geometry_node.client.render.effects;

import com.mine.geometry_node.client.render.RenderUtils;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public class RayBeamEffect extends AbstractVisualEffect {

    private final int sourceEntityId;
    private final Vec3 posOffset;
    private final float pitchOffset;
    private final float yawOffset;
    private final float length;
    private final float radius;

    public RayBeamEffect(PacketSpawnDynamicVisual packet) {
        super(packet);
        CompoundTag data = packet.extraData();
        if (data != null) {
            this.sourceEntityId = data.getInt("sourceId");
            this.posOffset = new Vec3(data.getDouble("offX"), data.getDouble("offY"), data.getDouble("offZ"));
            this.pitchOffset = data.getFloat("offPitch");
            this.yawOffset = data.getFloat("offYaw");
            this.length = data.getFloat("length");
            this.radius = data.getFloat("radius");
        } else {
            this.sourceEntityId = -1;
            this.posOffset = Vec3.ZERO;
            this.pitchOffset = 0f;
            this.yawOffset = 0f;
            this.length = 20f;
            this.radius = 0.1f;
        }
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 camPos, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || sourceEntityId == -1) return;

        Entity source = level.getEntity(sourceEntityId);
        if (source == null) return;

        // 1. 获取包含 partialTick 插值的绝对平滑坐标和视角 (核心：144Hz 刷新率的保障)
        // 注意：这里默认使用眼睛高度，你也可以根据需要改成基础 position
        Vec3 startPos = source.getEyePosition(partialTick).add(posOffset);
        float currentPitch = source.getViewXRot(partialTick) + pitchOffset;
        float currentYaw = source.getViewYRot(partialTick) + yawOffset;

        // 2. 将欧拉角转化为射线方向并计算终点
        Vec3 dir = Vec3.directionFromRotation(currentPitch, currentYaw);
        Vec3 endPos = startPos.add(dir.scale(length));

        // 3. 相对相机坐标转换
        Vec3 startRel = startPos.subtract(camPos);
        Vec3 endRel = endPos.subtract(camPos);

        // 4. 渲染长方体几何 (复用原有激光逻辑)
        Vec3 d = endRel.subtract(startRel);
        if (d.lengthSqr() < 1e-5) return;
        Vec3 normalizedDir = d.normalize();

        Vec3 up = Math.abs(normalizedDir.y) > 0.99 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 right = normalizedDir.cross(up).normalize().scale(radius);
        Vec3 realUp = right.cross(normalizedDir).normalize().scale(radius);

        Vec3 p1 = startRel.add(right).add(realUp);
        Vec3 p2 = startRel.subtract(right).add(realUp);
        Vec3 p3 = startRel.subtract(right).subtract(realUp);
        Vec3 p4 = startRel.add(right).subtract(realUp);

        Vec3 p5 = p1.add(d), p6 = p2.add(d), p7 = p3.add(d), p8 = p4.add(d);

        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lightning());
        Matrix4f matrix = poseStack.last().pose();

        int a = (color >> 24) & 0xFF;
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;

        RenderUtils.drawQuad(buffer, matrix, p1, p5, p6, p2, r, g, b, a);
        RenderUtils.drawQuad(buffer, matrix, p4, p3, p7, p8, r, g, b, a);
        RenderUtils.drawQuad(buffer, matrix, p1, p4, p8, p5, r, g, b, a);
        RenderUtils.drawQuad(buffer, matrix, p2, p6, p7, p3, r, g, b, a);
        RenderUtils.drawQuad(buffer, matrix, p1, p2, p3, p4, r, g, b, a);
        RenderUtils.drawQuad(buffer, matrix, p5, p8, p7, p6, r, g, b, a);
    }
}