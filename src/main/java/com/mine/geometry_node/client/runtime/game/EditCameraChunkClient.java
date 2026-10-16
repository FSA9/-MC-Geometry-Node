package com.mine.geometry_node.client.runtime.game;

import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketEditCamera;
import com.mine.geometry_node.core.network.packet.s2c.PacketEditCameraView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;

/** Client-thread position publication, throttled independently of UI/frame rate. */
public final class EditCameraChunkClient {
    private static ClientLevel level;
    private static boolean subscribed;
    private static int ticks;
    private static Vec3 lastSent;
    private EditCameraChunkClient() {}

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) { level = mc.level; subscribed = false; ticks = 0; lastSent = null; }
        if (level == null || mc.getConnection() == null) return;
        boolean active = EditViewportRuntime.isRendering() && EditViewportRuntime.hasVisibleViews()
                && GameViewportInput.isEditor(mc.screen) && mc.player != null && mc.player.isAlive();
        if (!active) { release(); return; }
        if (subscribed && ++ticks < 4) return;
        var position = EditViewportRuntime.camera().position();
        if (subscribed && position.equals(lastSent) && ticks < 20) return;
        ticks = 0;
        NetworkHandler.sendToServer(new PacketEditCamera(level.dimension().identifier(), true, position.x, position.y, position.z));
        lastSent = position;
        subscribed = true;
    }

    public static void release() {
        Minecraft mc = Minecraft.getInstance();
        if (subscribed && level == mc.level && mc.getConnection() != null) {
            NetworkHandler.sendToServer(new PacketEditCamera(level.dimension().identifier(), false, 0, 0, 0));
        }
        subscribed = false;
        ticks = 0;
        lastSent = null;
    }

    public static void accept(PacketEditCameraView packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null && packet.dimension().equals(mc.level.dimension().identifier())) {
            ((EditChunkCacheAccess) mc.level.getChunkSource()).geometryNode$cameraChunks()
                    .camera(packet.active(), packet.x(), packet.z(), packet.radius());
        }
    }
}
