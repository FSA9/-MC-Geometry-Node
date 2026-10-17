package com.mine.geometry_node.client.runtime.game.scene;

import com.mine.geometry_node.client.runtime.game.render.EditViewportRuntime;
import com.mine.geometry_node.client.runtime.game.input.GameViewportInput;
import com.mine.geometry_node.core.engine.graph.debug.ViewportDebugLayer;
import com.mine.geometry_node.core.network.NetworkHandler;
import com.mine.geometry_node.core.network.packet.c2s.PacketViewportDebugSubscription;
import com.mine.geometry_node.core.network.packet.s2c.PacketViewportDebugSnapshot;
import com.mine.geometry_node.core.network.packet.s2c.PacketGeometryDebugSnapshot;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/** Main client thread receives data; view UI only publishes immutable display settings. */
public final class ViewportDebugClient {
    private static ClientLevel world;
    private static int lastMask, ticks;
    private static Map<ViewportDebugLayer, PacketGeometryDebugSnapshot> layers = Map.of();
    private ViewportDebugClient() {}
    public static void tick() {
        var mc = Minecraft.getInstance();
        if (world != mc.level) { world=mc.level; lastMask=0; ticks=0; layers=Map.of(); }
        if (world == null || mc.getConnection() == null) return;
        int mask = EditViewportRuntime.hasVisibleViews() && GameViewportInput.isEditor(mc.screen)
                ? ViewportVisualization.requestedDebugMask() : 0;
        if (mask != lastMask || mask != 0 && ++ticks >= 20) {
            NetworkHandler.sendToServer(new PacketViewportDebugSubscription(world.dimension().identifier(),mask));
            lastMask=mask; ticks=0;
            if (mask == 0) layers=Map.of();
        }
    }
    public static void accept(PacketViewportDebugSnapshot packet) {
        var level=Minecraft.getInstance().level;
        if (level != null && packet.dimension().equals(level.dimension().identifier())) {
            world=level; layers=packet.layers();
        }
    }
    public static List<PacketGeometryDebugSnapshot.Mesh> visibleMeshes() {
        var state=ViewportVisualization.current();
        if (!state.helpers()) return List.of();
        List<PacketGeometryDebugSnapshot.Mesh> result=new ArrayList<>();
        layers.forEach((layer,data) -> { if (state.visible(ViewportVisualization.Layer.valueOf(layer.name()))) result.addAll(data.meshes()); });
        return result;
    }
    public static List<PacketGeometryDebugSnapshot.Mesh> meshes(ViewportDebugLayer layer) {
        var snapshot = layers.get(layer);
        return snapshot == null ? List.of() : snapshot.meshes();
    }
    public static void clear() { world=null; layers=Map.of(); lastMask=ticks=0; }
}
