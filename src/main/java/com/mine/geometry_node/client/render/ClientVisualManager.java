package com.mine.geometry_node.client.render;

import com.mine.geometry_node.client.render.effects.AbstractVisualEffect;
import com.mine.geometry_node.client.render.effects.DebugLineEffect;
import com.mine.geometry_node.client.render.effects.LaserBeamEffect;
import com.mine.geometry_node.core.network.packet.s2c.PacketSpawnDynamicVisual;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;

public class ClientVisualManager {

    private static final List<AbstractVisualEffect> ACTIVE_EFFECTS = new CopyOnWriteArrayList<>();

    private static final Map<String, Function<PacketSpawnDynamicVisual, AbstractVisualEffect>> EFFECT_FACTORIES = new HashMap<>();

    // 注册
    public static void init() {
        registerFactory("debug_line", DebugLineEffect::new);
        registerFactory("laser_beam", LaserBeamEffect::new);
    }

    public static void registerFactory(String effectType, Function<PacketSpawnDynamicVisual, AbstractVisualEffect> factory) {
        EFFECT_FACTORIES.put(effectType, factory);
    }

    public static void spawnEffectFromPacket(PacketSpawnDynamicVisual payload) {
        Function<PacketSpawnDynamicVisual, AbstractVisualEffect> factory = EFFECT_FACTORIES.get(payload.effectType());

        if (factory != null) {
            ACTIVE_EFFECTS.add(factory.apply(payload));
        } else {
            System.err.println("[GeometryNode] Unknown visual effect type received: " + payload.effectType());
        }
    }

    // 生命周期与渲染
    public static void tick() {
        if (Minecraft.getInstance().isPaused()) return;
        ACTIVE_EFFECTS.removeIf(AbstractVisualEffect::tick);
    }

    public static void renderWorld(PoseStack poseStack, Camera camera) {
        if (ACTIVE_EFFECTS.isEmpty()) return;

        Vec3 camPos = camera.getPosition();
        float partialTick = (float) Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);

        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        poseStack.pushPose();
        for (AbstractVisualEffect effect : ACTIVE_EFFECTS) {
            effect.render(poseStack, bufferSource, camPos, partialTick);
        }
        poseStack.popPose();
    }
}