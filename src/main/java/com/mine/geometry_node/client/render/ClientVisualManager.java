package com.mine.geometry_node.client.render;

import com.mine.geometry_node.client.render.effects.*;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

public class ClientVisualManager {

    // 【核心优化 1】：换成普通 ArrayList，彻底告别 CopyOnWrite 的全量数组复制
    private static final List<AbstractVisualEffect> ACTIVE_EFFECTS = new ArrayList<>();

    // 【核心优化 2】：使用无锁并发队列，专门用来接纳网络线程发来的新特效
    private static final Queue<AbstractVisualEffect> PENDING_ADDITIONS = new ConcurrentLinkedQueue<>();

    private static final Map<String, Function<PacketSpawnDynamicVisual, AbstractVisualEffect>> EFFECT_FACTORIES = new HashMap<>();

    public static void init() {
        registerFactory("debug_line", DebugLineEffect::new);
        registerFactory("laser_beam", LaserBeamEffect::new);
        registerFactory("ray_beam", RayBeamEffect::new);
        registerFactory("item_visual", ItemVisualEffect::new);
    }

    public static void registerFactory(String effectType, Function<PacketSpawnDynamicVisual, AbstractVisualEffect> factory) {
        EFFECT_FACTORIES.put(effectType, factory);
    }

    public static void spawnEffectFromPacket(PacketSpawnDynamicVisual payload) {
        Function<PacketSpawnDynamicVisual, AbstractVisualEffect> factory = EFFECT_FACTORIES.get(payload.effectType());

        if (factory != null) {
            // 【核心优化 3】：网络线程只负责把特效丢进队列，绝不直接操作主渲染列表
            PENDING_ADDITIONS.add(factory.apply(payload));
        } else {
            System.err.println("[GeometryNode] Unknown visual effect type received: " + payload.effectType());
        }
    }

    public static void tick() {
        if (Minecraft.getInstance().isPaused()) return;

        // 【核心优化 4】：主线程在这里统一将队列里的新特效合并到主列表，做到真正的线程安全且无锁阻塞
        AbstractVisualEffect pendingEffect;
        while ((pendingEffect = PENDING_ADDITIONS.poll()) != null) {
            ACTIVE_EFFECTS.add(pendingEffect);
        }

        // 【核心优化 5】：倒序遍历 ArrayList。
        // 这样在 remove 时不需要移动大量元素，速度极快，彻底取代了原本高耗能的 removeIf。
        for (int i = ACTIVE_EFFECTS.size() - 1; i >= 0; i--) {
            if (ACTIVE_EFFECTS.get(i).tick()) {
                ACTIVE_EFFECTS.remove(i);
            }
        }
    }

    public static void renderWorld(PoseStack poseStack, Camera camera) {
        if (ACTIVE_EFFECTS.isEmpty()) return;

        Vec3 camPos = camera.getPosition();
        float partialTick = (float) Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        poseStack.pushPose();

        // 【核心优化 6】：直接用基础的 for 循环渲染 ArrayList，CPU 缓存命中率最高，速度最快
        for (int i = 0; i < ACTIVE_EFFECTS.size(); i++) {
            ACTIVE_EFFECTS.get(i).render(poseStack, bufferSource, camPos, partialTick);
        }

        poseStack.popPose();
    }
}