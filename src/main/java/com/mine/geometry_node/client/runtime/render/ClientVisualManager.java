package com.mine.geometry_node.client.runtime.render;

import com.mine.geometry_node.client.runtime.render.effects.*;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Function;

public class ClientVisualManager {
    private static final List<AbstractVisualEffect> ACTIVE_EFFECTS = new ArrayList<>();

    private static final Queue<AbstractVisualEffect> PENDING_ADDITIONS = new ConcurrentLinkedQueue<>();

    private static final Map<String, Function<PacketSpawnDynamicVisual, AbstractVisualEffect>> EFFECT_FACTORIES = new HashMap<>();

    public static void init() {
        registerFactory("debug_line", DebugLineEffect::new);
        registerFactory("laser_beam", LaserBeamEffect::new);
        registerFactory("ray_beam", RayBeamEffect::new);
        registerFactory("debug_box", DebugBoxEffect::new);
        registerFactory("item_visual", ItemVisualEffect::new);
        registerFactory("image_visual", ImageVisualEffect::new);
    }

    public static void registerFactory(String effectType, Function<PacketSpawnDynamicVisual, AbstractVisualEffect> factory) {
        EFFECT_FACTORIES.put(effectType, factory);
    }

    public static void spawnEffectFromPacket(PacketSpawnDynamicVisual payload) {
        Function<PacketSpawnDynamicVisual, AbstractVisualEffect> factory = EFFECT_FACTORIES.get(payload.effectType());

        if (factory != null) {
            PENDING_ADDITIONS.add(factory.apply(payload));
        } else {
            System.err.println("[GeometryNode] Unknown visual effect type received: " + payload.effectType());
        }
    }

    public static void tick() {
        if (Minecraft.getInstance().isPaused()) return;
        AbstractVisualEffect pendingEffect;
        while ((pendingEffect = PENDING_ADDITIONS.poll()) != null) {
            ACTIVE_EFFECTS.add(pendingEffect);
        }

        for (int i = ACTIVE_EFFECTS.size() - 1; i >= 0; i--) {
            if (ACTIVE_EFFECTS.get(i).tick()) {
                ACTIVE_EFFECTS.remove(i);
            }
        }
    }

    public static void clear() {
        PENDING_ADDITIONS.clear();
        ACTIVE_EFFECTS.clear();
    }

    public static void renderWorld(PoseStack poseStack, SubmitNodeCollector submitNodeCollector) {
        if (ACTIVE_EFFECTS.isEmpty()) return;

        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 camPos = camera.position();
        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        poseStack.pushPose();

        for (int i = 0; i < ACTIVE_EFFECTS.size(); i++) {
            ACTIVE_EFFECTS.get(i).render(poseStack, bufferSource, submitNodeCollector, camPos, partialTick);
        }

        poseStack.popPose();
    }
}
