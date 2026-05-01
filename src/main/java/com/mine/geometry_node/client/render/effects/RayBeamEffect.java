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
    private final float pitchOffset, yawOffset, maxLength, radius;
    private final boolean penSolid, penTrans, penEnt;
    private final int maxEnt;

    // 【核心优化】：缓存上一帧和当前帧的实际碰撞距离，用于平滑插值
    private float currentHitDistance;
    private float prevHitDistance;

    public RayBeamEffect(PacketSpawnDynamicVisual packet) {
        super(packet);
        CompoundTag data = packet.extraData();
        if (data != null) {
            this.sourceEntityId = data.getInt("sourceId");
            this.posOffset = new Vec3(data.getDouble("offX"), data.getDouble("offY"), data.getDouble("offZ"));
            this.pitchOffset = data.getFloat("offPitch");
            this.yawOffset = data.getFloat("offYaw");
            this.maxLength = data.getFloat("length");
            this.radius = data.getFloat("radius");

            this.penSolid = data.getBoolean("penSolid");
            this.penTrans = data.getBoolean("penTrans");
            this.penEnt = data.getBoolean("penEnt");
            this.maxEnt = data.getInt("maxEnt");
        } else {
            this.sourceEntityId = -1; this.posOffset = Vec3.ZERO;
            this.pitchOffset = 0; this.yawOffset = 0; this.maxLength = 20; this.radius = 0.1f;
            this.penSolid = false; this.penTrans = true; this.penEnt = false; this.maxEnt = 1;
        }

        this.currentHitDistance = this.maxLength;
        this.prevHitDistance = this.maxLength;
    }

    // 【核心优化】：将沉重的物理计算转移到 20Hz 的 Tick 中
    @Override
    public boolean tick() {
        // 先调用父类的 tick 处理寿命
        boolean isDead = super.tick();
        if (isDead) return true;

        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || sourceEntityId == -1) return false;

        Entity source = level.getEntity(sourceEntityId);
        if (source == null) return false;

        // 记录上一刻的距离，用于渲染插值
        this.prevHitDistance = this.currentHitDistance;

        // 获取不带 partialTick 的绝对坐标 (因为在 tick 里)
        Vec3 startPos = source.getEyePosition().add(posOffset);
        float currentPitch = source.getXRot() + pitchOffset;
        float currentYaw = source.getYRot() + yawOffset;
        Vec3 dir = Vec3.directionFromRotation(currentPitch, currentYaw);
        Vec3 maxEnd = startPos.add(dir.scale(maxLength));
        Vec3 actualEnd = maxEnd;

        // --- 以下是 20Hz 执行的物理检测（极其省性能） ---

        // 1. 方块检测
        if (!penSolid) {
            Vec3 currentStart = startPos;
            while (true) {
                ClipContext clipCtx = new ClipContext(currentStart, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source);
                BlockHitResult blockHit = level.clip(clipCtx);

                if (blockHit.getType() != HitResult.Type.MISS) {
                    BlockState hitState = level.getBlockState(blockHit.getBlockPos());
                    if (!hitState.canOcclude() && penTrans) {
                        currentStart = blockHit.getLocation().add(dir.scale(0.01));
                        if (currentStart.distanceToSqr(startPos) >= maxLength * maxLength) break;
                        continue;
                    } else {
                        actualEnd = blockHit.getLocation();
                        break;
                    }
                }
                break;
            }
        }

        // 2. 实体检测
        double actualDistSqr = startPos.distanceToSqr(actualEnd);
        AABB broadBox = new AABB(startPos, actualEnd).inflate(radius + 1.0);
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
                Entity lastHit = hitList.get(maxEnt - 1);
                AABB lastAABB = lastHit.getBoundingBox().inflate(lastHit.getPickRadius() + radius);
                actualEnd = lastAABB.clip(startPos, actualEnd).orElse(actualEnd);
            }
        }

        // 计算并缓存最终的碰撞距离
        this.currentHitDistance = (float) Math.sqrt(startPos.distanceToSqr(actualEnd));
        return false;
    }

    // 【核心优化】：Render 彻底变成纯数学绘图，144Hz 跑起来毫无压力
    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, Vec3 camPos, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || sourceEntityId == -1) return;

        Entity source = level.getEntity(sourceEntityId);
        if (source == null) return;

        // 1. 视角依然是 144Hz 极致丝滑插值
        Vec3 startPos = source.getEyePosition(partialTick).add(posOffset);
        float currentPitch = source.getViewXRot(partialTick) + pitchOffset;
        float currentYaw = source.getViewYRot(partialTick) + yawOffset;
        Vec3 dir = Vec3.directionFromRotation(currentPitch, currentYaw);

        // 2. 距离采用缓存值的平滑插值 (防止激光在前后缩短时产生阶梯感)
        float lerpedDistance = this.prevHitDistance + (this.currentHitDistance - this.prevHitDistance) * partialTick;

        // 3. 计算终点
        Vec3 endPos = startPos.add(dir.scale(lerpedDistance));

        // 4. 纯几何渲染 (相对相机坐标)
        Vec3 startRel = startPos.subtract(camPos);
        Vec3 endRel = endPos.subtract(camPos);
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