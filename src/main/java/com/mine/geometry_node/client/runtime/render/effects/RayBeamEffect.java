package com.mine.geometry_node.client.runtime.render.effects;

import com.mine.geometry_node.core.engine.graph.expression.ExpressionEvaluationContext;
import com.mine.geometry_node.core.engine.graph.expression.LiveValue;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mine.geometry_node.core.node.nodes.actions.visual.DrawRayBeam;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class RayBeamEffect extends AbstractVisualEffect {

    private final int sourceEntityId;
    private final LiveValue.State<Vec3> posOffset;
    private final LiveValue.State<Float> pitchOffset;
    private final LiveValue.State<Float> yawOffset;
    private final LiveValue.State<Float> maxLength;
    private final LiveValue.State<Float> radius;
    private final boolean penSolid, penTrans, penEnt;
    private final int maxEnt;

    // 【核心优化】：缓存上一帧和当前帧的实际碰撞距离，用于平滑插值
    private float currentHitDistance;

    public RayBeamEffect(PacketSpawnDynamicVisual packet) {
        super(packet);
        CompoundTag data = packet.extraData();
        Vec3 baseOffset;
        float basePitch;
        float baseYaw;
        float baseLength;
        float baseRadius;
        if (data != null) {
            this.sourceEntityId = data.getIntOr("sourceId", -1);
            baseOffset = new Vec3(data.getDoubleOr("offX", 0.0), data.getDoubleOr("offY", 0.0), data.getDoubleOr("offZ", 0.0));
            basePitch = data.getFloatOr("offPitch", 0.0f);
            baseYaw = data.getFloatOr("offYaw", 0.0f);
            baseLength = data.getFloatOr("length", 20.0f);
            baseRadius = data.getFloatOr("radius", 0.1f);

            this.penSolid = data.getBooleanOr("penSolid", false);
            this.penTrans = data.getBooleanOr("penTrans", true);
            this.penEnt = data.getBooleanOr("penEnt", false);
            this.maxEnt = data.getIntOr("maxEnt", 1);
        } else {
            this.sourceEntityId = -1;
            baseOffset = Vec3.ZERO;
            basePitch = 0.0F;
            baseYaw = 0.0F;
            baseLength = 20.0F;
            baseRadius = 0.1F;
            this.penSolid = false; this.penTrans = true; this.penEnt = false; this.maxEnt = 1;
        }

        this.posOffset = captureXyz(DrawRayBeam.START_PORT, "offset", baseOffset);
        this.pitchOffset = captureFloat(DrawRayBeam.PITCH_PORT, "pitch", basePitch);
        this.yawOffset = captureFloat(DrawRayBeam.YAW_PORT, "yaw", baseYaw);
        this.maxLength = captureFloat(DrawRayBeam.DISTANCE_PORT, "distance", baseLength);
        this.radius = captureFloat(DrawRayBeam.RADIUS_PORT, "radius", baseRadius);
        this.currentHitDistance = Math.max(0.0F, baseLength);
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

        ExpressionEvaluationContext expressionContext = expressionContext(0.0F);
        Vec3 evaluatedOffset = posOffset.evaluate(expressionContext);
        float evaluatedPitchOffset = pitchOffset.evaluate(expressionContext);
        float evaluatedYawOffset = yawOffset.evaluate(expressionContext);
        float evaluatedMaxLength = Math.max(0.0F, maxLength.evaluate(expressionContext));
        float evaluatedRadius = Math.max(0.001F, Math.abs(radius.evaluate(expressionContext)));

        // 获取不带 partialTick 的绝对坐标 (因为在 tick 里)
        Vec3 startPos = source.getEyePosition().add(evaluatedOffset);
        float currentPitch = source.getXRot() + evaluatedPitchOffset;
        float currentYaw = source.getYRot() + evaluatedYawOffset;
        Vec3 dir = Vec3.directionFromRotation(currentPitch, currentYaw);
        Vec3 maxEnd = startPos.add(dir.scale(evaluatedMaxLength));
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
                        if (currentStart.distanceToSqr(startPos) >= evaluatedMaxLength * evaluatedMaxLength) break;
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
        AABB broadBox = new AABB(startPos, actualEnd).inflate(evaluatedRadius + 1.0);
        List<Entity> entities = level.getEntities(source, broadBox, e -> !e.isSpectator() && e.isPickable());

        List<Entity> hitList = new ArrayList<>();
        for (Entity e : entities) {
            AABB aabb = e.getBoundingBox().inflate(e.getPickRadius() + evaluatedRadius);
            Optional<Vec3> hitOpt = aabb.clip(startPos, actualEnd);
            if (hitOpt.isPresent() && startPos.distanceToSqr(hitOpt.get()) <= actualDistSqr) {
                hitList.add(e);
            }
        }

        if (!hitList.isEmpty()) {
            hitList.sort(Comparator.comparingDouble(e -> e.distanceToSqr(startPos)));
            if (!penEnt) {
                Entity firstHit = hitList.get(0);
                AABB firstAABB = firstHit.getBoundingBox().inflate(firstHit.getPickRadius() + evaluatedRadius);
                actualEnd = firstAABB.clip(startPos, actualEnd).orElse(actualEnd);
            } else if (maxEnt > 0 && hitList.size() > maxEnt) {
                Entity lastHit = hitList.get(maxEnt - 1);
                AABB lastAABB = lastHit.getBoundingBox().inflate(lastHit.getPickRadius() + evaluatedRadius);
                actualEnd = lastAABB.clip(startPos, actualEnd).orElse(actualEnd);
            }
        }

        // 计算并缓存最终的碰撞距离
        this.currentHitDistance = (float) Math.sqrt(startPos.distanceToSqr(actualEnd));
        return false;
    }

    // 【核心优化】：Render 彻底变成纯数学绘图，144Hz 跑起来毫无压力
    @Override
    public void render(PoseStack poseStack, MultiBufferSource.BufferSource bufferSource, SubmitNodeCollector submitNodeCollector, Vec3 camPos, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || sourceEntityId == -1) return;

        Entity source = level.getEntity(sourceEntityId);
        if (source == null) return;

        ExpressionEvaluationContext expressionContext = expressionContext(partialTick);
        Vec3 evaluatedOffset = posOffset.evaluate(expressionContext);
        float evaluatedPitchOffset = pitchOffset.evaluate(expressionContext);
        float evaluatedYawOffset = yawOffset.evaluate(expressionContext);
        float evaluatedMaxLength = Math.max(0.0F, maxLength.evaluate(expressionContext));
        float evaluatedRadius = Math.max(0.001F, Math.abs(radius.evaluate(expressionContext)));

        // 1. 视角依然是 144Hz 极致丝滑插值
        Vec3 startPos = source.getEyePosition(partialTick).add(evaluatedOffset);
        float currentPitch = source.getViewXRot(partialTick) + evaluatedPitchOffset;
        float currentYaw = source.getViewYRot(partialTick) + evaluatedYawOffset;
        Vec3 dir = Vec3.directionFromRotation(currentPitch, currentYaw);

        Vec3 endPos = startPos.add(dir.scale(Math.min(this.currentHitDistance, evaluatedMaxLength)));

        Vec3 startRel = startPos.subtract(camPos);
        Vec3 endRel = endPos.subtract(camPos);

        BeamGeometry.drawPrism(
                bufferSource.getBuffer(RenderTypes.lightning()),
                poseStack.last().pose(),
                startRel,
                endRel,
                evaluatedRadius,
                color
        );
    }
}
