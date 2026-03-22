package com.mine.geometry_node.client.render;

import com.mine.geometry_node.client.render.effects.AbstractVisualEffect;
import com.mine.geometry_node.client.render.effects.DebugLineEffect;
import com.mine.geometry_node.client.render.effects.LaserBeamEffect;
import com.mine.geometry_node.core.network.packet.PacketSpawnVisual;
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

    private static final Map<String, Function<PacketSpawnVisual, AbstractVisualEffect>> EFFECT_FACTORIES = new HashMap<>();

    // 注册机制 (在客户端 Init 阶段调用)
    public static void init() {
        registerFactory("debug_line", packet -> new DebugLineEffect(
                packet.startPos(), packet.endPos(), packet.color(), packet.durationTicks()
        ));
        registerFactory("laser_beam", packet -> new LaserBeamEffect(
                packet.startPos(), packet.endPos(), packet.color(), packet.size(), packet.durationTicks()
        ));
    }

    public static void registerFactory(String effectType, Function<PacketSpawnVisual, AbstractVisualEffect> factory) {
        EFFECT_FACTORIES.put(effectType, factory);
    }

    public static void spawnEffectFromPacket(PacketSpawnVisual payload) {
        Function<PacketSpawnVisual, AbstractVisualEffect> factory = EFFECT_FACTORIES.get(payload.effectType());

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
        MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();

        poseStack.pushPose();
        for (AbstractVisualEffect effect : ACTIVE_EFFECTS) {
            effect.render(poseStack, bufferSource, camPos);
        }
        poseStack.popPose();
    }
}