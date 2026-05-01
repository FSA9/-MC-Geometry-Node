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
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class RayBeamEffect extends AbstractVisualEffect {

    private final int sourceEntityId;
    private final Vec3 posOffset;
    private final float pitchOffset, yawOffset, length, radius;

    // 物理规则缓存
    private final boolean penSolid, penTrans, penEnt;
    private final int maxEnt;

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

            this.penSolid = data.getBoolean("penSolid");
            this.penTrans = data.getBoolean("penTrans");
            this.penEnt = data.getBoolean("penEnt");
            this.maxEnt = data.getInt("maxEnt");
        } else {
            // ... 默认值兜底略
            this.sourceEntityId = -1; this.posOffset = Vec3.ZERO;
            this.pitchOffset = 0; this.yawOffset = 0; this.length = 20; this.radius = 0.1f;
            this.penSolid = false; this.penTrans = true; this.penEnt = false; this.maxEnt = 1;
        }
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 camPos, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || sourceEntityId == -1) return;

        Entity source = level.getEntity(sourceEntityId);
        if (source == null) return;

        // 1. 获取绝对平滑的起点和方向
        Vec3 startPos = source.getEyePosition(partialTick).add(posOffset);
        float currentPitch = source.getViewXRot(partialTick) + pitchOffset;
        float currentYaw = source.getViewYRot(partialTick) + yawOffset;
        Vec3 dir = Vec3.directionFromRotation(currentPitch, currentYaw);
        Vec3 maxEnd = startPos.add(dir.scale(length));
        Vec3 actualEnd = maxEnd;

        // 2. [客户端高频物理计算] 方块碰撞
        if (!penSolid) {
            Vec3 currentStart = startPos;
            while (true) {
                ClipContext clipCtx = new ClipContext(currentStart, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source);
                BlockHitResult blockHit = level.clip(clipCtx);

                if (blockHit.getType() != HitResult.Type.MISS) {
                    BlockState hitState = level.getBlockState(blockHit.getBlockPos());
                    if (!hitState.canOcclude() && penTrans) {
                        currentStart = blockHit.getLocation().add(dir.scale(0.01));
                        if (currentStart.distanceToSqr(startPos) >= length * length) break;
                        continue;
                    } else {
                        actualEnd = blockHit.getLocation(); // 撞到实心墙，折断激光！
                        break;
                    }
                }
                break;
            }
        }

        // 3. [客户端高频物理计算] 实体碰撞
        double actualDistSqr = startPos.distanceToSqr(actualEnd);
        AABB broadBox = new AABB(startPos, actualEnd).inflate(radius + 1.0);
        // 注意：客户端这里只为了视觉折断，不需要复杂的逻辑判定，碰谁折谁
        List<Entity> entities = level.getEntities(source, broadBox, e -> !e.isSpectator() && e.isPickable());

        List<Entity> hitList = new ArrayList<>();
        for (Entity e : entities) {
            AABB aabb = e.getBoundingBox().inflate(e.getPickRadius() + radius);
            Optional<Vec3> hitOpt = aabb.clip(startPos, actualEnd);
            if (hitOpt.isPresent() && startPos.distanceToSqr(hitOpt.get()) <= actualDistSqr) {
                hitList.add(e);
            }
        }

        if (!hitList.isEmpty()) {
            hitList.sort(Comparator.comparingDouble(e -> e.distanceToSqr(startPos)));
            if (!penEnt) {
                Entity firstHit = hitList.get(0);
                AABB firstAABB = firstHit.getBoundingBox().inflate(firstHit.getPickRadius() + radius);
                actualEnd = firstAABB.clip(startPos, actualEnd).orElse(actualEnd);
            } else if (maxEnt > 0 && hitList.size() > maxEnt) {
                // 如果穿透数量达到上限，折断在最后一个允许穿透的实体身上
                Entity lastHit = hitList.get(maxEnt - 1);
                AABB lastAABB = lastHit.getBoundingBox().inflate(lastHit.getPickRadius() + radius);
                actualEnd = lastAABB.clip(startPos, actualEnd).orElse(actualEnd);
            }
        }

        // 4. 开始用实际算出来的折断点 actualEnd 进行几何绘制！
        Vec3 startRel = startPos.subtract(camPos);
        Vec3 endRel = actualEnd.subtract(camPos);
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
        float[] c = RenderUtils.unpackColor(color);

        RenderUtils.drawQuad(buffer, matrix, p1, p5, p6, p2, (int)(c[0]*255), (int)(c[1]*255), (int)(c[2]*255), (int)(c[3]*255));
        RenderUtils.drawQuad(buffer, matrix, p4, p3, p7, p8, (int)(c[0]*255), (int)(c[1]*255), (int)(c[2]*255), (int)(c[3]*255));
        RenderUtils.drawQuad(buffer, matrix, p1, p4, p8, p5, (int)(c[0]*255), (int)(c[1]*255), (int)(c[2]*255), (int)(c[3]*255));
        RenderUtils.drawQuad(buffer, matrix, p2, p6, p7, p3, (int)(c[0]*255), (int)(c[1]*255), (int)(c[2]*255), (int)(c[3]*255));
        RenderUtils.drawQuad(buffer, matrix, p1, p2, p3, p4, (int)(c[0]*255), (int)(c[1]*255), (int)(c[2]*255), (int)(c[3]*255));
        RenderUtils.drawQuad(buffer, matrix, p5, p8, p7, p6, (int)(c[0]*255), (int)(c[1]*255), (int)(c[2]*255), (int)(c[3]*255));
    }
}