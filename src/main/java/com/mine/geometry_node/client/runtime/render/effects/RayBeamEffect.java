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
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public class RayBeamEffect extends AbstractVisualEffect {

    private final VisualTransform transform;
    private final LiveValue.State<Float> maxLength;
    private final LiveValue.State<Float> radius;
    private final boolean penSolid, penTrans, penEnt;
    private final int maxEnt;

    // 【核心优化】：缓存上一帧和当前帧的实际碰撞距离，用于平滑插值
    private float currentHitDistance;

    public RayBeamEffect(PacketSpawnDynamicVisual packet) {
        super(packet);
        CompoundTag data = packet.extraData();
        this.transform=VisualTransform.capture(this,data);
        float baseLength;
        float baseRadius;
        baseLength = data.getFloatOr("length", 20.0f);
        baseRadius = data.getFloatOr("radius", 0.1f);

        this.penSolid = data.getBooleanOr("penSolid", false);
        this.penTrans = data.getBooleanOr("penTrans", true);
        this.penEnt = data.getBooleanOr("penEnt", false);
        this.maxEnt = data.getIntOr("maxEnt", 1);

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
        if (level == null) return false;
        RayFrame frame = resolveFrame(level, 1.0F, expressionContext(0.0F));
        if (frame == null) return false;
        Entity source = frame.source();
        Vec3 startPos = frame.position();
        Vec3 dir = frame.direction();
        float evaluatedMaxLength = frame.length();
        float evaluatedRadius = frame.radius();
        Vec3 maxEnd = startPos.add(dir.scale(evaluatedMaxLength));
        Vec3 actualEnd = maxEnd;

        // --- 以下是 20Hz 执行的物理检测（极其省性能） ---

        // 1. 方块检测
        if (!penSolid) {
            Vec3 currentStart = startPos;
            while (true) {
                ClipContext clipCtx = new ClipContext(currentStart, maxEnd, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                        source == null ? CollisionContext.empty() : CollisionContext.of(source));
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
        RayFrame frame = resolveFrame(level, partialTick, expressionContext(partialTick));
        if (frame == null) return;
        Vec3 startPos = frame.position();
        Vec3 dir = frame.direction();
        float evaluatedMaxLength = frame.length();
        float evaluatedRadius = frame.radius();

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

    /** Position follows the shared anchor; view inheritance remains an explicit ray option. */
    @Nullable RayFrame resolveFrame(@Nullable ClientLevel level, float partialTick,
                                    ExpressionEvaluationContext expressionContext) {
        VisualTransform.Frame frame=transform.resolve(level,partialTick,expressionContext);
        if(frame==null)return null;
        var anchor=transform.anchor().anchor();
        Entity source=anchor.requiresEntity() && level!=null ? level.getEntity(anchor.entityId()) : null;
        return new RayFrame(frame.position(),
                com.mine.geometry_node.core.engine.spatial.SpatialTransform.rotate(frame.orientation(),new Vec3(0,0,1)),
                Math.max(0,maxLength.evaluate(expressionContext)),
                Math.max(0.001F,Math.abs(radius.evaluate(expressionContext))),source);
    }

    record RayFrame(Vec3 position, Vec3 direction, float length, float radius, @Nullable Entity source) {}

}
